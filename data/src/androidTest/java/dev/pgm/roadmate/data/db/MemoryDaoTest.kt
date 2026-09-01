package dev.pgm.roadmate.data.db

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Exercises [MemoryDao] against a real (in-memory) Room database — the unit
 * tests only ever see the fakes, so this is the one place the actual SQL is
 * checked. Runs on a device/emulator.
 */
@RunWith(AndroidJUnit4::class)
class MemoryDaoTest {

    private lateinit var db: RoadMateDatabase
    private lateinit var dao: MemoryDao

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, RoadMateDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.memoryDao()
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun recentExchanges_respectsTheSinceCutoffAndOrdersNewestFirst() = runTest {
        dao.insertExchange(TripExchangeEntity(question = "old", answer = "a", at = 1_000))
        dao.insertExchange(TripExchangeEntity(question = "older", answer = "b", at = 2_000))
        dao.insertExchange(TripExchangeEntity(question = "newest", answer = "c", at = 3_000))

        val recent = dao.recentExchanges(since = 1_500, limit = 10)

        assertEquals(listOf("newest", "older"), recent.map { it.question })
    }

    @Test
    fun pruneExchangesBefore_dropsOldRows() = runTest {
        dao.insertExchange(TripExchangeEntity(question = "keep", answer = "a", at = 5_000))
        dao.insertExchange(TripExchangeEntity(question = "drop", answer = "b", at = 100))

        dao.pruneExchangesBefore(1_000)

        assertEquals(listOf("keep"), dao.recentExchanges(since = 0, limit = 10).map { it.question })
    }

    @Test
    fun bumpFact_incrementsWhenPresent_andReportsZeroWhenAbsent() = runTest {
        assertEquals(0, dao.bumpFact("PLACE", "gasolineras", now = 1))

        dao.insertFact(UserFactEntity(type = "PLACE", value = "gasolineras", updatedAt = 1, hits = 1))
        assertEquals(1, dao.bumpFact("PLACE", "gasolineras", now = 2))

        assertEquals(2, dao.topFactsByType("PLACE", 5).first().hits)
    }

    @Test
    fun topFactsByType_ordersByHits() = runTest {
        dao.insertFact(UserFactEntity(type = "PLACE", value = "poco", updatedAt = 1, hits = 1))
        dao.insertFact(UserFactEntity(type = "PLACE", value = "mucho", updatedAt = 1, hits = 9))

        assertEquals(listOf("mucho", "poco"), dao.topFactsByType("PLACE", 5).map { it.value })
    }

    @Test
    fun deleteFactByKey_removesOnlyThatKeyedRow() = runTest {
        dao.insertFact(UserFactEntity(type = "RELATIONSHIP", factKey = "hermano", value = "Ana", updatedAt = 1))
        dao.insertFact(UserFactEntity(type = "RELATIONSHIP", factKey = "jefe", value = "Luis", updatedAt = 1))

        dao.deleteFactByKey("RELATIONSHIP", "hermano")

        assertEquals(listOf("Luis"), dao.factsByType("RELATIONSHIP").map { it.value })
        assertNull(dao.findFact("RELATIONSHIP", "Ana"))
    }

    @Test
    fun clear_wipesBothTables() = runTest {
        dao.insertExchange(TripExchangeEntity(question = "q", answer = "a", at = 1))
        dao.insertFact(UserFactEntity(type = "PREFERENCE", value = "x", updatedAt = 1))

        dao.clearExchanges()
        dao.clearFacts()

        assertEquals(emptyList<TripExchangeEntity>(), dao.recentExchanges(since = 0, limit = 10))
        assertEquals(emptyList<UserFactEntity>(), dao.factsByType("PREFERENCE"))
    }
}
