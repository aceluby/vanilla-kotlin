package vanillakotlin.db.repository


internal class UserFavoriteTcinRepositoryTest {
    private val db by lazy { buildTestDb() }
    private val repository by lazy { UserFavoriteTcinRepository(db) }

    @Test fun `user favorite tcin crud operations`() {
        val testUserName = randomUsername()
        val testTcin = randomTcin()

        // initially there should be no data found or deleted
        repository.deleteByUserNameAndTcin(testUserName, testTcin) shouldBe 0
        repository.findByUserName(testUserName) shouldBe emptyList()

        // insert
        val inserted = repository.upsert(UserFavoriteTcin(userName = testUserName, tcin = testTcin))
        inserted.userName shouldBe testUserName
        inserted.tcin shouldBe testTcin
        val insertedId = checkNotNull(inserted.id)
        val insertedCreatedAt = checkNotNull(inserted.createdTs)
        val insertedUpdatedAt = checkNotNull(inserted.updatedTs)

        // find the favorite we previously inserted
        val postInsertFavorites = repository.findByUserName(testUserName)
        postInsertFavorites.size shouldBe 1
        assertSoftly(postInsertFavorites.first()) {
            userName shouldBe testUserName
            tcin shouldBe testTcin
            id shouldBe insertedId
            createdTs shouldBe insertedCreatedAt
            updatedTs shouldBe insertedUpdatedAt
        }

        // update
        assertSoftly(repository.upsert(UserFavoriteTcin(userName = testUserName, tcin = testTcin))) {
            userName shouldBe testUserName
            tcin shouldBe testTcin
            id shouldBe insertedId
            createdTs shouldBe insertedCreatedAt
            checkNotNull(updatedTs) shouldBeGreaterThan insertedUpdatedAt
        }

        // delete
        repository.deleteByUserNameAndTcin(testUserName, testTcin) shouldBe 1

        // after deletion, there should again be no data found or deleted
        repository.deleteByUserNameAndTcin(testUserName, testTcin) shouldBe 0
        repository.findByUserName(testUserName) shouldBe emptyList()
    }

    @Test fun `validate outbox`() {
        val userName = randomUsername()
        val tcin = randomTcin()
        val messageKey = "$userName:$tcin"

        repository.upsert(UserFavoriteTcin(userName = userName, tcin = tcin))
        repository.deleteByUserNameAndTcin(userName, tcin)

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

        // The first outbox row should represent the inserted user favorite tcin
        val insertedFavoriteOutbox = outboxes.first()
        insertedFavoriteOutbox.messageKey shouldBe messageKey
        insertedFavoriteOutbox.createdTs shouldNotBe null

        assertSoftly(mapper.readValue<UserFavoriteTcin>(checkNotNull(insertedFavoriteOutbox.body))) {
            this.tcin shouldBe tcin
            this.userName shouldBe userName
            createdTs shouldNotBe null
            updatedTs shouldNotBe null
        }

        // The second outbox should represent the deleted user favorite tcin
        val deletedFavoriteOutbox = outboxes[1]
        deletedFavoriteOutbox.messageKey shouldBe messageKey
        deletedFavoriteOutbox.createdTs shouldNotBe null
        deletedFavoriteOutbox.body shouldBe null
    }

    @Test fun `validate batch`() {
        val userName = randomUsername()
        val tcin = randomTcin()
        val userFavoriteTcin = UserFavoriteTcin(userName = userName, tcin = tcin)

        // insert
        repository.addToBatch(userFavoriteTcin)
        repository.runBatch()

        var dbFavoriteTcin = repository.findByUserName(userName).first { it.tcin == tcin }
        assertSoftly(dbFavoriteTcin) {
            userName shouldBe userName
            tcin shouldBe tcin
            createdTs shouldNotBe null
            updatedTs shouldNotBe null
            createdTs shouldBe updatedTs
        }

        // update
        repository.addToBatch(userFavoriteTcin)
        repository.runBatch()

        dbFavoriteTcin = repository.findByUserName(userName).first { it.tcin == tcin }
        assertSoftly(dbFavoriteTcin) {
            userName shouldBe userName
            tcin shouldBe tcin
            createdTs shouldNotBe null
            updatedTs shouldNotBe null
            updatedTs?.shouldBeAfter(createdTs ?: fail { "createdTs should not be null" })
        }

        // delete
        val deleteRecord = userFavoriteTcin.copy(isDeleted = true)
        repository.addToBatch(deleteRecord)
        repository.runBatch()

        repository.findByUserName(userName).none { it.tcin == tcin } shouldBe true
    }
}
