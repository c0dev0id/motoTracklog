package de.codevoid.mototracklog

import de.codevoid.mototracklog.data.RunReader
import de.codevoid.mototracklog.data.RunRepository
import de.codevoid.mototracklog.data.RunWriter
import de.codevoid.mototracklog.model.GpsRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class RunRepositoryTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val repository by lazy { RunRepository(folder.newFolder("runs")) }

    private fun point(timeMillis: Long) = GpsRecord(
        timeMillis = timeMillis,
        elapsedRealtimeNanos = timeMillis * 1_000_000L,
        latitude = 52.0,
        longitude = 8.9,
        altitude = null,
        accuracy = 3f,
        verticalAccuracy = null,
        speed = null,
        speedAccuracy = null,
        bearing = null,
        bearingAccuracy = null,
    )

    private fun writeRun(id: Long, vararg times: Long) {
        val writer = RunWriter(repository.runFile(id))
        times.forEach { writer.append(point(it)) }
        writer.close()
    }

    @Test
    fun runsAreListedNewestFirstWithTheirSpanAndCount() {
        writeRun(1_000L, 1_000L, 2_000L, 5_000L)
        writeRun(9_000L, 9_000L, 9_500L)

        val runs = repository.listRuns()

        assertEquals(listOf(9_000L, 1_000L), runs.map { it.id })
        assertEquals(3L, runs[1].pointCount)
        assertEquals(1_000L, runs[1].startTimeMillis)
        assertEquals(4_000L, runs[1].durationMillis)
    }

    @Test
    fun aRunWithoutPointsIsListedAtItsIdsTime() {
        // A run started and stopped before the first fix: the header alone.
        writeRun(7_000L)

        val run = repository.listRuns().single()

        assertEquals(0L, run.pointCount)
        assertEquals(7_000L, run.startTimeMillis)
        assertEquals(0L, run.durationMillis)
    }

    @Test
    fun mergeKeepsTheEarlierIdAndSortsThePointsByTime() {
        writeRun(1_000L, 1_000L, 3_000L)
        writeRun(2_000L, 2_000L, 4_000L)

        val merged = repository.merge(listOf(2_000L, 1_000L))

        assertEquals(1_000L, merged)
        assertEquals(listOf(1_000L), repository.listRuns().map { it.id })
        val times = RunReader.readAll(repository.runFile(1_000L)).map { it.timeMillis }
        assertEquals(listOf(1_000L, 2_000L, 3_000L, 4_000L), times)
    }

    @Test
    fun mergeLeavesNoTemporaryFileBehind() {
        writeRun(1_000L, 1_000L)
        writeRun(2_000L, 2_000L)

        repository.merge(listOf(1_000L, 2_000L))

        val names = repository.runFile(1_000L).parentFile!!.list()!!.toList()
        assertEquals(listOf("1000.dat"), names)
    }

    @Test
    fun mergeNeedsTwoRunsThatExist() {
        writeRun(1_000L, 1_000L)

        assertNull(repository.merge(listOf(1_000L, 2_000L)))
        assertTrue(repository.runFile(1_000L).exists())
    }

    @Test
    fun deleteRemovesTheRunsFile() {
        writeRun(1_000L, 1_000L)

        assertTrue(repository.delete(1_000L))
        assertFalse(repository.runFile(1_000L).exists())
        assertEquals(emptyList<Long>(), repository.listRuns().map { it.id })
    }
}
