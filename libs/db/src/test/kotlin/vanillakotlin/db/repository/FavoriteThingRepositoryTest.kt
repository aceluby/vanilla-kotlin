package vanillakotlin.db.repository

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.junit.jupiter.api.Test
import vanillakotlin.models.FavoriteThing
import vanillakotlin.random.randomThing

class FavoriteThingRepositoryTest {
    private val jdbi by lazy { buildTestDb() }
    private val repository by lazy { FavoriteThingRepository(jdbi) }

    @Test fun `upsert favorite thing - insert new thing`() {
        val favoriteThing = FavoriteThing(
            id = null,
            thingIdentifier = randomThing(),
        )

        val result = repository.upsert(favoriteThing)

        assertSoftly {
            result.id shouldNotBe null
            result.thingIdentifier shouldBe favoriteThing.thingIdentifier
            result.createdTs shouldNotBe null
            result.updatedTs shouldNotBe null
        }
    }

    @Test fun `upsert favorite thing - update existing thing`() {
        // First insert a thing
        val originalThing = FavoriteThing(
            id = null,
            thingIdentifier = randomThing(),
        )
        val insertedThing = repository.upsert(originalThing)

        // Wait a moment to ensure different timestamp
        Thread.sleep(100)

        // Now upsert the same thing again (should update timestamp)
        val result = repository.upsert(insertedThing)

        assertSoftly {
            result.id shouldBe insertedThing.id
            result.thingIdentifier shouldBe insertedThing.thingIdentifier
            result.createdTs shouldBe insertedThing.createdTs
            result.updatedTs shouldNotBe insertedThing.updatedTs // Should be updated
        }
    }

    @Test fun `delete thing - thing exists`() {
        val favoriteThing = FavoriteThing(
            id = null,
            thingIdentifier = randomThing(),
        )
        val insertedThing = repository.upsert(favoriteThing)

        val rowCount = repository.deleteItem(insertedThing.thingIdentifier)

        assertSoftly {
            rowCount shouldBe 1
            // Verify thing is deleted by checking findAll doesn't contain it
            val allThings = repository.findAll()
            allThings.none { it.thingIdentifier == insertedThing.thingIdentifier } shouldBe true
        }
    }

    @Test fun `delete thing - thing does not exist`() {
        val nonExistentThing = randomThing()

        val rowCount = repository.deleteItem(nonExistentThing)

        rowCount shouldBe 0
    }

    @Test fun `find all things`() {
        // Clear any existing things by checking current state
        val initialCount = repository.findAll().size

        // Insert a few things
        val thing1 = FavoriteThing(thingIdentifier = randomThing())
        val thing2 = FavoriteThing(thingIdentifier = randomThing())

        repository.upsert(thing1)
        repository.upsert(thing2)

        val allThings = repository.findAll()

        assertSoftly {
            allThings.size shouldBe (initialCount + 2)
            allThings.any { it.thingIdentifier == thing1.thingIdentifier } shouldBe true
            allThings.any { it.thingIdentifier == thing2.thingIdentifier } shouldBe true
        }
    }

    @Test fun `batch operations - add to batch and run batch`() {
        val thing1 = FavoriteThing(thingIdentifier = randomThing())
        val thing2 = FavoriteThing(thingIdentifier = randomThing())
        val thing3 = FavoriteThing(thingIdentifier = randomThing())

        // Add things to batch
        repository.addToBatch(thing1)
        repository.addToBatch(thing2)
        repository.addToBatch(thing3)

        // Run batch
        val result = repository.runBatch()

        assertSoftly {
            result shouldBe 3

            // Verify things were inserted
            val allThings = repository.findAll()
            allThings.any { it.thingIdentifier == thing1.thingIdentifier } shouldBe true
            allThings.any { it.thingIdentifier == thing2.thingIdentifier } shouldBe true
            allThings.any { it.thingIdentifier == thing3.thingIdentifier } shouldBe true
        }
    }

    @Test fun `batch operations - empty batch returns zero`() {
        val result = repository.runBatch()

        result shouldBe 0
    }
}
