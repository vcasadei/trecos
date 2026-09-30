package app.trecos.sync

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** The Drive layout of sync (design D14) over an in-memory Drive. */
class DriveRemoteTest {

    /** An in-memory Drive that understands the queries [DriveRemote] sends. */
    class FakeDrive : DriveFiles {
        data class Entry(val file: DriveFile, var bytes: ByteArray = ByteArray(0))

        val entries = LinkedHashMap<String, Entry>()
        private var next = 0
        var uploads = 0

        override suspend fun list(query: String, space: String): List<DriveFile> {
            val parent = Regex("'([^']+)' in parents").find(query)?.groupValues?.get(1)
            val name = Regex("name = '((?:[^'\\\\]|\\\\.)*)'").find(query)?.groupValues?.get(1)?.replace("\\'", "'")
            val mime = Regex("mimeType = '([^']+)'").find(query)?.groupValues?.get(1)
            return entries.values.map { it.file }.filter { f ->
                (parent == null || (if (parent == "root") f.parents.isEmpty() else parent in f.parents)) &&
                    (name == null || f.name == name) && (mime == null || f.mimeType == mime)
            }
        }

        override suspend fun createFolder(name: String, parent: String?): DriveFile =
            DriveFile("id${next++}", name, DriveClient.FOLDER, listOfNotNull(parent)).also { entries[it.id] = Entry(it) }

        override suspend fun upload(name: String, parent: String, bytes: ByteArray, mimeType: String): DriveFile {
            uploads++
            return DriveFile("id${next++}", name, mimeType, listOf(parent)).also { entries[it.id] = Entry(it, bytes) }
        }

        override suspend fun update(id: String, bytes: ByteArray, mimeType: String) {
            entries.getValue(id).bytes = bytes
        }

        override suspend fun download(id: String): ByteArray = entries[id]?.bytes ?: throw DriveNotFoundException(id)

        override suspend fun delete(id: String) {
            entries.remove(id)
        }

        /** @return the path of a file, folder names joined by `/`. */
        fun path(file: DriveFile): String {
            val parent = file.parents.firstOrNull()?.let { entries[it]?.file }
            return if (parent == null) file.name else path(parent) + "/" + file.name
        }
    }

    private val drive = FakeDrive()
    private val remote = DriveRemote(drive)

    /** @return every file path in the fake Drive. */
    private fun paths() = drive.entries.values.filter { it.file.mimeType != DriveClient.FOLDER }.map { drive.path(it.file) }.toSet()

    @Test
    fun theFolderIsCreatedOnlyOnTheFirstConnection() = runBlocking {
        assertThrows(DriveFolderMissingException::class.java) { runBlocking { remote.open(create = false) } }
        assertFalse(remote.open(create = true))
        assertTrue(remote.open(create = false))
        assertEquals(1, drive.entries.values.count { it.file.name == "Trecos" })
    }

    @Test
    fun layout() = runBlocking {
        remote.open(create = true)
        remote.putCommit("h1", "c1", byteArrayOf(1))
        remote.putRef("h1", "dev", Ref("c1", 5, 1))
        remote.putObject("ab", byteArrayOf(2))

        assertEquals(setOf("Trecos/houses/h1/commits/c1.jsonl.gz", "Trecos/houses/h1/refs/dev.json", "Trecos/objects/ab.webp"), paths())
        assertEquals(listOf("h1"), remote.houses())
        assertEquals(mapOf("dev" to Ref("c1", 5, 1)), remote.refs("h1"))
        assertArrayEquals(byteArrayOf(1), remote.commit("h1", "c1"))
        assertEquals(listOf("c1"), remote.commits("h1"))
        assertEquals(setOf("ab"), remote.objects())
        assertArrayEquals(byteArrayOf(2), remote.getObject("ab"))
        assertNull(remote.commit("h1", "nope"))
        assertNull(remote.getObject("nope"))
    }

    @Test
    fun writesAreIdempotent() = runBlocking {
        remote.open(create = true)
        remote.putCommit("h1", "c1", byteArrayOf(1))
        remote.putCommit("h1", "c1", byteArrayOf(1))
        remote.putObject("ab", byteArrayOf(2))
        remote.putObject("ab", byteArrayOf(2))
        remote.putRef("h1", "dev", Ref("c1", 5, 1))
        remote.putRef("h1", "dev", Ref("c2", 6, 1))

        assertEquals(3, drive.uploads)
        assertEquals(Ref("c2", 6, 1), remote.refs("h1")["dev"])
    }

    @Test
    fun deletingCommits() = runBlocking {
        remote.open(create = true)
        remote.putCommit("h1", "c1", byteArrayOf(1))
        remote.deleteCommit("h1", "c1")
        remote.deleteCommit("h1", "c1")
        assertTrue(remote.commits("h1").isEmpty())
    }

    @Test
    fun anEmptyFolderHasNothing() = runBlocking {
        remote.open(create = true)
        assertTrue(remote.houses().isEmpty())
        assertTrue(remote.refs("h1").isEmpty())
        assertTrue(remote.commits("h1").isEmpty())
        assertTrue(remote.objects().isEmpty())
    }

    @Test
    fun aDeletedFolderIsNoticed() {
        runBlocking { remote.open(create = true) }
        drive.entries.clear()
        assertThrows(DriveFolderMissingException::class.java) { runBlocking { DriveRemote(drive).open(create = false) } }
    }
}
