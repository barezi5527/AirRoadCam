package de.roadcam.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.RandomAccessFile

class ClipRepositoryTest {

    @get:Rule
    val folder = TemporaryFolder()

    private class FakeProtectionStore(initial: Set<String> = emptySet()) : ProtectionStore {
        val names = initial.toMutableSet()
        val shockNames = mutableSetOf<String>()
        override fun protectedNames(): Set<String> = names.toSet()
        override fun saveProtectedNames(names: Set<String>) {
            this.names.clear()
            this.names.addAll(names)
        }

        override fun shockProtectedNames(): Set<String> = shockNames.toSet()
        override fun saveShockProtectedNames(names: Set<String>) {
            this.shockNames.clear()
            this.shockNames.addAll(names)
        }
    }

    private val now = 1_700_000_000_000L

    private fun clip(directory: File, name: String, size: Long = 1L, modified: Long = now): File =
        File(directory, name).apply {
            RandomAccessFile(this, "rw").use { it.setLength(size) }
            setLastModified(modified)
        }

    private fun repository(
        directory: File,
        protection: ProtectionStore,
        usableSpace: Long = Long.MAX_VALUE
    ) = ClipRepository(
        directory = directory,
        protectionStore = protection,
        clock = { now },
        usableSpace = { usableSpace }
    )

    @Test
    fun `deleteClip removes unprotected clip`() {
        val directory = folder.newFolder()
        val file = clip(directory, "RoadCam_1.mp4")
        val protection = FakeProtectionStore()
        val repository = repository(directory, protection)

        val result = repository.deleteClip(DashClip(file, protected = false))

        assertEquals(DeleteResult.DELETED, result)
        assertFalse(file.exists())
    }

    @Test
    fun `deleteClip keeps protected clip`() {
        val directory = folder.newFolder()
        val file = clip(directory, "RoadCam_1.mp4")
        val protection = FakeProtectionStore(setOf(file.name))
        val repository = repository(directory, protection)

        val result = repository.deleteClip(DashClip(file, protected = true))

        assertEquals(DeleteResult.PROTECTED, result)
        assertTrue(file.exists())
        assertTrue(protection.names.contains(file.name))
    }

    @Test
    fun `prune removes oldest clips until storage limit is met`() {
        val directory = folder.newFolder()
        val oldest = clip(directory, "oldest.mp4", size = 1L * 1024 * 1024 * 1024, modified = now - 3_000)
        val middle = clip(directory, "middle.mp4", size = 1L * 1024 * 1024 * 1024, modified = now - 2_000)
        val newest = clip(directory, "newest.mp4", size = 1L * 1024 * 1024 * 1024, modified = now - 1_000)
        val repository = repository(directory, FakeProtectionStore())

        val result = repository.prune(DashcamSettings(storageLimitGb = 2))

        assertEquals(listOf(oldest), result.removed)
        assertFalse(oldest.exists())
        assertTrue(middle.exists())
        assertTrue(newest.exists())
    }

    @Test
    fun `prune never removes protected clips`() {
        val directory = folder.newFolder()
        val protected = clip(directory, "protected.mp4", size = 3L * 1024 * 1024 * 1024, modified = now - 3_000)
        val newest = clip(directory, "newest.mp4", size = 1L * 1024 * 1024 * 1024, modified = now - 1_000)
        val repository = repository(directory, FakeProtectionStore(setOf(protected.name)))

        val result = repository.prune(DashcamSettings(storageLimitGb = 2))

        assertEquals(listOf(newest), result.removed)
        assertTrue(protected.exists())
        assertFalse(newest.exists())
    }

    @Test
    fun `prune removes clips older than retention when enabled`() {
        val directory = folder.newFolder()
        val day = 24L * 60L * 60L * 1000L
        val expired = clip(directory, "expired.mp4", modified = now - 6 * day)
        val recent = clip(directory, "recent.mp4", modified = now - 1 * day)
        val repository = repository(directory, FakeProtectionStore())

        val result = repository.prune(
            DashcamSettings(deleteOldClips = true, retentionDays = 5, storageLimitGb = 64)
        )

        assertEquals(listOf(expired), result.removed)
        assertFalse(expired.exists())
        assertTrue(recent.exists())
    }

    @Test
    fun `shock protection is listed and survives pruning`() {
        val directory = folder.newFolder()
        val file = clip(directory, "shock.mp4")
        val protection = FakeProtectionStore()
        val repository = repository(directory, protection)

        repository.addShockProtected(file.name)

        val listed = repository.listClips().single()
        assertTrue(listed.protected)
        assertTrue(listed.shockProtected)
        assertTrue(protection.shockNames.contains(file.name))

        repository.prune(DashcamSettings(deleteOldClips = true, retentionDays = 1, storageLimitGb = 1))
        assertTrue(file.exists())
    }

    @Test
    fun `removing protection clears shock marker`() {
        val directory = folder.newFolder()
        val file = clip(directory, "shock.mp4")
        val protection = FakeProtectionStore()
        val repository = repository(directory, protection)

        repository.addShockProtected(file.name)
        repository.removeProtected(file.name)

        val listed = repository.listClips().single()
        assertFalse(listed.protected)
        assertFalse(listed.shockProtected)
    }

    @Test
    fun `retention is ignored when option is disabled`() {
        val directory = folder.newFolder()
        val expired = clip(directory, "expired.mp4", modified = now - 30 * 24L * 60L * 60L * 1000L)
        val repository = repository(directory, FakeProtectionStore())

        val result = repository.prune(
            DashcamSettings(deleteOldClips = false, retentionDays = 1, storageLimitGb = 64)
        )

        assertTrue(result.removed.isEmpty())
        assertTrue(expired.exists())
    }
}
