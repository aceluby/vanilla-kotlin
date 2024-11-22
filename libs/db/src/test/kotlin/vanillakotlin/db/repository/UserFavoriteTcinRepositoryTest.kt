package vanillakotlin.db.repository


internal class UserFavoriteTcinRepositoryTest {
    private val db by lazy { buildTestDb() }
    private val repository by lazy { UserFavoriteTcinRepository(db) }

    @Test fun `user favorite item crud operations`() {
        val testUserName = randomUsername()
        val testTcin = randomTcin()

        // initially there should be no data found or deleted
        repository.deleteItem(testUserName, testTcin) shouldBe 0
        repository.findAll(testUserName) shouldBe emptyList()

        // insert
        val inserted = repository.upsert(UserFavoriteTcin(userName = testUserName, item = testTcin))
        inserted.userName shouldBe testUserName
        inserted.item shouldBe testTcin
        val insertedId = checkNotNull(inserted.id)
        val insertedCreatedAt = checkNotNull(inserted.createdTs)
        val insertedUpdatedAt = checkNotNull(inserted.updatedTs)

        // find the favorite we previously inserted
        val postInsertFavorites = repository.findAll(testUserName)
        postInsertFavorites.size shouldBe 1
        assertSoftly(postInsertFavorites.first()) {
            userName shouldBe testUserName
            item shouldBe testTcin
            id shouldBe insertedId
            createdTs shouldBe insertedCreatedAt
            updatedTs shouldBe insertedUpdatedAt
        }

        // update
        assertSoftly(repository.upsert(UserFavoriteTcin(userName = testUserName, item = testTcin))) {
            userName shouldBe testUserName
            item shouldBe testTcin
            id shouldBe insertedId
            createdTs shouldBe insertedCreatedAt
            checkNotNull(updatedTs) shouldBeGreaterThan insertedUpdatedAt
        }

        // delete
        repository.deleteItem(testUserName, testTcin) shouldBe 1

        // after deletion, there should again be no data found or deleted
        repository.deleteItem(testUserName, testTcin) shouldBe 0
        repository.findAll(testUserName) shouldBe emptyList()
    }

    @Test fun `validate outbox`() {
        val userName = randomUsername()
        val item = randomTcin()
        val messageKey = "$userName:$item"

        repository.upsert(UserFavoriteTcin(userName = userName, item = item))
        repository.deleteItem(userName, item)

        val outboxes =
            db.findAll(
                sql =
                    """
                    SELECT * 
                    FROM outbox 
                    WHERE message_key = :messageKey ORDER BY created_ts
                    """.trimIndent(),
                args = mapOf("messageKey" to messageKey),
                rowMapper = ::mapToOutbox,
            )

        outboxes.size shouldBe 2

        // The first outbox row should represent the inserted user favorite item
        val insertedFavoriteOutbox = outboxes.first()
        insertedFavoriteOutbox.messageKey shouldBe messageKey
        insertedFavoriteOutbox.createdTs shouldNotBe null

        assertSoftly(mapper.readValue<UserFavoriteTcin>(checkNotNull(insertedFavoriteOutbox.body))) {
            this.item shouldBe item
            this.userName shouldBe userName
            createdTs shouldNotBe null
            updatedTs shouldNotBe null
        }

        // The second outbox should represent the deleted user favorite item
        val deletedFavoriteOutbox = outboxes[1]
        deletedFavoriteOutbox.messageKey shouldBe messageKey
        deletedFavoriteOutbox.createdTs shouldNotBe null
        deletedFavoriteOutbox.body shouldBe null
    }

    @Test fun `validate batch`() {
        val userName = randomUsername()
        val item = randomTcin()
        val userFavoriteTcin = UserFavoriteTcin(userName = userName, item = item)

        // insert
        repository.addToBatch(userFavoriteTcin)
        repository.runBatch()

        var dbFavoriteTcin = repository.findAll(userName).first { it.item == item }
        assertSoftly(dbFavoriteTcin) {
            userName shouldBe userName
            item shouldBe item
            createdTs shouldNotBe null
            updatedTs shouldNotBe null
            createdTs shouldBe updatedTs
        }

        // update
        repository.addToBatch(userFavoriteTcin)
        repository.runBatch()

        dbFavoriteTcin = repository.findAll(userName).first { it.item == item }
        assertSoftly(dbFavoriteTcin) {
            userName shouldBe userName
            item shouldBe item
            createdTs shouldNotBe null
            updatedTs shouldNotBe null
            updatedTs?.shouldBeAfter(createdTs ?: fail { "createdTs should not be null" })
        }

        // delete
        val deleteRecord = userFavoriteTcin.copy(isDeleted = true)
        repository.addToBatch(deleteRecord)
        repository.runBatch()

        repository.findAll(userName).none { it.item == item } shouldBe true
    }
}
