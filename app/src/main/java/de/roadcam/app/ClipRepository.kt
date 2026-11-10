package de.roadcam.app

import android.content.Context
import java.io.File

interface ProtectionStore {
    fun protectedNames(): Set<String>
    fun saveProtectedNames(names: Set<String>)
    fun shockProtectedNames(): Set<String>
    fun saveShockProtectedNames(names: Set<String>)
}

class SharedPreferencesProtectionStore(context: Context) : ProtectionStore {
    private val prefs = context.applicationContext.getSharedPreferences("clips", Context.MODE_PRIVATE)

    override fun protectedNames(): Set<String> =
        prefs.getStringSet("protected", emptySet()).orEmpty().toSet()

    override fun saveProtectedNames(names: Set<String>) {
        prefs.edit().putStringSet("protected", names.toSet()).apply()
    }

    override fun shockProtectedNames(): Set<String> =
        prefs.getStringSet("shockProtected", emptySet()).orEmpty().toSet()

    override fun saveShockProtectedNames(names: Set<String>) {
        prefs.edit().putStringSet("shockProtected", names.toSet()).apply()
    }
}

enum class DeleteResult { DELETED, PROTECTED, MISSING }

data class PruneResult(
    val removed: List<File>,
    val totalBytes: Long
)

class ClipRepository(
    private val directory: File,
    private val protectionStore: ProtectionStore,
    private val clock: () -> Long = System::currentTimeMillis,
    private val usableSpace: (File) -> Long = { it.usableSpace }
) {
    fun listClips(): List<DashClip> {
        val protected = protectionStore.protectedNames()
        val shockProtected = protectionStore.shockProtectedNames()
        return clipFiles()
            .filter { it.length() > 0L }
            .sortedByDescending(File::lastModified)
            .map { DashClip(it, it.name in protected, it.name in shockProtected) }
    }

    fun totalSize(): Long = clipFiles().sumOf(File::length)

    fun isProtected(name: String): Boolean = name in protectionStore.protectedNames()

    fun addProtected(name: String) {
        val names = protectionStore.protectedNames().toMutableSet()
        names.add(name)
        protectionStore.saveProtectedNames(names)
    }

    fun addShockProtected(name: String) {
        addProtected(name)
        val names = protectionStore.shockProtectedNames().toMutableSet()
        names.add(name)
        protectionStore.saveShockProtectedNames(names)
    }

    fun removeProtected(name: String) {
        val names = protectionStore.protectedNames().toMutableSet()
        names.remove(name)
        protectionStore.saveProtectedNames(names)
        val shockNames = protectionStore.shockProtectedNames().toMutableSet()
        shockNames.remove(name)
        protectionStore.saveShockProtectedNames(shockNames)
    }

    fun deleteClip(clip: DashClip, currentFile: File? = null): DeleteResult {
        if (clip.file == currentFile) return DeleteResult.MISSING
        if (clip.protected || isProtected(clip.file.name)) return DeleteResult.PROTECTED
        val deleted = clip.file.delete()
        removeProtected(clip.file.name)
        return if (deleted) DeleteResult.DELETED else DeleteResult.MISSING
    }

    fun prune(settings: DashcamSettings, currentFile: File? = null): PruneResult {
        val removed = mutableListOf<File>()
        if (settings.deleteOldClips) {
            removed += removeExpired(settings.retentionMillis, currentFile)
        }

        val protected = protectionStore.protectedNames()
        val files = clipFiles()
        var total = files.sumOf(File::length)
        val oldestFirst = files
            .filter { it.name !in protected && it != currentFile }
            .sortedBy(File::lastModified)

        for (file in oldestFirst) {
            val limitReached = total <= settings.storageLimitBytes
            val spaceOk = usableSpace(directory) >= DashcamSettings.FREE_SPACE_RESERVE_BYTES
            if (limitReached && spaceOk) break
            val length = file.length()
            if (file.delete()) {
                total -= length
                removeProtected(file.name)
                removed += file
            }
        }
        return PruneResult(removed, total)
    }

    private fun removeExpired(retentionMillis: Long, currentFile: File?): List<File> {
        val protected = protectionStore.protectedNames()
        val cutoff = clock() - retentionMillis
        return clipFiles()
            .filter {
                it.name !in protected && it != currentFile && it.lastModified() < cutoff
            }
            .onEach { file ->
                if (file.delete()) removeProtected(file.name)
            }
            .filterNot(File::exists)
    }

    private fun clipFiles(): List<File> = directory.listFiles()
        .orEmpty()
        .filter {
            it.isFile && (
                it.extension.equals("mp4", ignoreCase = true) ||
                    it.extension.equals("jpg", ignoreCase = true) ||
                    it.extension.equals("jpeg", ignoreCase = true)
                )
        }
}
