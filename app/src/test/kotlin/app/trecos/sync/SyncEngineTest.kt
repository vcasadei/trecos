package app.trecos.sync

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.trecos.data.Container
import app.trecos.data.House
import app.trecos.data.Item
import app.trecos.data.Photo
import app.trecos.data.SearchIndex
import app.trecos.data.TrecosDatabase
import app.trecos.places.OrganizeStore
import app.trecos.places.PhotoStore
import app.trecos.places.Selection
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The sync engine with two simulated devices and an in-memory Drive (task
 * 12.6 and the merge scenarios end to end), including fault injection.
 */
@RunWith(RobolectricTestRunner::class)
class SyncEngineTest {

    @get:Rule
    val folder = TemporaryFolder()

    /** An in-memory [SyncRemote] that can fail its Nth call. */
    class FakeRemote : SyncRemote {
        var exists = false
        val refs = HashMap<String, HashMap<String, Ref>>()
        val commits = HashMap<String, HashMap<String, ByteArray>>()
        val objects = HashMap<String, ByteArray>()
        val deleted = HashMap<String, Deletion>()
        var uploads = 0
        var calls = 0
        var failAt: Int? = null

        /** Counts a call and fails it when told to. */
        private fun call() {
            calls++
            if (calls == failAt) throw IOException("Signal lost at call $calls")
        }

        override suspend fun open(create: Boolean): Boolean {
            call()
            if (exists) return true
            if (!create) throw DriveFolderMissingException("The Trecos folder is missing")
            exists = true
            return false
        }

        override suspend fun houses(): List<String> = call().let { (refs.keys + commits.keys).toList() }
        override suspend fun refs(houseId: String): Map<String, Ref> = call().let { refs[houseId].orEmpty().toMap() }
        override suspend fun putRef(houseId: String, deviceId: String, ref: Ref) {
            call()
            refs.getOrPut(houseId) { HashMap() }[deviceId] = ref
        }
        override suspend fun commit(houseId: String, commitId: String): ByteArray? = call().let { commits[houseId]?.get(commitId) }
        override suspend fun putCommit(houseId: String, commitId: String, bytes: ByteArray) {
            call()
            commits.getOrPut(houseId) { HashMap() }.getOrPut(commitId) { bytes }
        }
        override suspend fun commits(houseId: String): List<String> = call().let { commits[houseId]?.keys?.toList().orEmpty() }
        override suspend fun deleteCommit(houseId: String, commitId: String) {
            call()
            commits[houseId]?.remove(commitId)
        }
        override suspend fun deletion(houseId: String): Deletion? = call().let { deleted[houseId] }
        override suspend fun markDeleted(houseId: String, deletion: Deletion) {
            call()
            deleted[houseId] = deletion
        }
        override suspend fun clearDeleted(houseId: String) {
            call()
            deleted.remove(houseId)
        }
        override suspend fun objects(): Set<String> = call().let { objects.keys.toSet() }
        override suspend fun putObject(sha256: String, bytes: ByteArray) {
            call()
            if (objects.put(sha256, bytes) == null) uploads++
        }
        override suspend fun getObject(sha256: String): ByteArray? = call().let { objects[sha256] }
    }

    /** An in-memory key vault. */
    class FakeVault : app.trecos.crypto.KeyVault {
        var key: ByteArray? = null

        override suspend fun get(): ByteArray? = key

        override suspend fun put(key: ByteArray) {
            this.key = key
        }

        override suspend fun delete() {
            key = null
        }
    }

    /** One simulated device. */
    inner class Device(name: String) {
        val db: TrecosDatabase = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), TrecosDatabase::class.java)
            .allowMainThreadQueries().addCallback(SearchIndex.onCreate).build()
        val photos = PhotoStore(folder.newFolder(), ApplicationProvider.getApplicationContext<Context>().contentResolver)
        val store = SyncStore(folder.newFolder(), name)
        var now = 1_000L
        val engine = SyncEngine(db, photos, store, clock = { now++ })
        val organize = OrganizeStore(db, { now++ }, { UUID.randomUUID().toString() })

        /** Syncs, pulling [pull] too. */
        fun sync(photosAllowed: Boolean = true, pull: Set<String> = emptySet(), through: SyncRemote = remote) =
            runBlocking { engine.sync(through, DriveUser("Vitor", "v@example.com"), photosAllowed, create = true, pull = pull) }

        /** @return an item. */
        fun item(id: String) = runBlocking { db.items().get(id) }

        /** Changes an item. */
        fun edit(id: String, change: (Item) -> Item) = runBlocking { db.items().update(change(db.items().get(id)!!)) }
    }

    private val remote = FakeRemote()
    private val devices = mutableListOf<Device>()

    @After
    fun close() = devices.forEach { it.db.close() }

    /** @return a new device. */
    private fun device(name: String) = Device(name).also { devices += it }

    /** A phone with one house, a container and a Raspberry Pi. */
    private fun phone(): Device = device("phone").also { d ->
        runBlocking {
            d.db.houses().insert(House("h1", "Apartment", icon = "house", createdAt = 1, updatedAt = 1))
            d.db.containers().insert(Container("boxA", "h1", null, "Box A", icon = "box", createdAt = 1, updatedAt = 1))
            d.db.items().insert(Item("pi", "h1", "boxA", "Pi", 1, createdAt = 1, updatedAt = 1))
        }
    }

    /** A phone and a tablet sharing the house. */
    private fun pair(): Pair<Device, Device> {
        val phone = phone()
        phone.sync()
        val tablet = device("tablet")
        tablet.sync(pull = setOf("h1"))
        return phone to tablet
    }

    /** A phone and a tablet sharing two houses: h1 with the Pi, and an empty "Beach house". */
    private fun pairWithTwoHouses(): Pair<Device, Device> {
        val phone = phone()
        runBlocking { phone.db.houses().insert(House("h2", "Beach house", icon = "house", createdAt = 1, updatedAt = 1)) }
        phone.sync()
        val tablet = device("tablet")
        tablet.sync(pull = setOf("h1", "h2"))
        return phone to tablet
    }

    /** Deletes a house as the delete screen does, with sync connected. */
    private fun Device.deleteHouse(id: String) = runBlocking {
        organize.deleteHouse(id)
        engine.houseDeleted(id, connected = true)
    }

    @Test
    fun deletingAHouseKeepsItInDriveAndRemovesItElsewhere() {
        val (phone, tablet) = pairWithTwoHouses()
        val commits = remote.commits["h1"]!!.keys.toSet()
        phone.deleteHouse("h1")
        phone.sync()

        assertNotNull(remote.deleted["h1"])
        assertEquals(phone.store.load().deviceId, remote.deleted["h1"]!!.deviceId)
        tablet.sync()
        assertNull(runBlocking { tablet.db.houses().get("h1") })
        assertNull(tablet.item("pi"))
        assertNotNull(runBlocking { tablet.db.houses().get("h2") })
        assertEquals(setOf("h1"), tablet.store.load().droppedHouses)
        assertEquals("nothing deleted from Drive", commits, remote.commits["h1"]!!.keys)
        assertTrue("a deleted house isn't offered back", phone.sync().remoteOnlyHouses.isEmpty())
        assertTrue(tablet.sync().remoteOnlyHouses.isEmpty())
    }

    @Test
    fun restoringADeletedHouseBringsItBackEverywhere() {
        val (phone, tablet) = pairWithTwoHouses()
        phone.deleteHouse("h1")
        phone.sync()
        tablet.sync()

        val deleted = runBlocking { phone.engine.deletedHouses(remote) }
        assertEquals(setOf("h1"), deleted.keys)
        runBlocking { phone.engine.restoreDeleted(remote, "h1", DriveUser("Vitor", "v@example.com"), photosAllowed = true) }
        assertEquals("Pi", phone.item("pi")!!.name)
        assertNull(remote.deleted["h1"])

        tablet.sync()
        assertEquals("Pi", tablet.item("pi")!!.name)
        assertTrue(tablet.store.load().droppedHouses.isEmpty())
        phone.edit("pi") { it.copy(quantity = 3) }
        phone.sync()
        tablet.sync()
        assertEquals("they sync again as usual", 3, tablet.item("pi")!!.quantity)
    }

    @Test
    fun restoringOnTheOnlyDevice() {
        val phone = phone()
        runBlocking { phone.db.houses().insert(House("h2", "Beach house", icon = "house", createdAt = 1, updatedAt = 1)) }
        phone.sync()
        phone.deleteHouse("h1")
        phone.sync()
        runBlocking { phone.engine.restoreDeleted(remote, "h1", DriveUser("Vitor", "v@example.com"), photosAllowed = true) }

        assertEquals("Pi", phone.item("pi")!!.name)
        assertEquals(1, phone.sync().let { remote.refs["h1"]!!.size })
    }

    @Test
    fun unsyncedChangesKeepADeletedHouse() {
        val (phone, tablet) = pairWithTwoHouses()
        phone.deleteHouse("h1")
        phone.sync()
        tablet.edit("pi") { it.copy(name = "Raspberry Pi 5") }
        tablet.sync()

        assertEquals("Raspberry Pi 5", tablet.item("pi")!!.name)
        assertNull("the edit wins: the house is no longer deleted", remote.deleted["h1"])
        assertTrue(tablet.store.load().droppedHouses.isEmpty())
        assertEquals(listOf("h1"), phone.sync().remoteOnlyHouses)
    }

    @Test
    fun theOnlyHouseOnADeviceIsKept() {
        val phone = phone()
        runBlocking { phone.db.houses().insert(House("h2", "Beach house", icon = "house", createdAt = 1, updatedAt = 1)) }
        phone.sync()
        val tablet = device("tablet")
        tablet.sync(pull = setOf("h1"))
        phone.deleteHouse("h1")
        phone.sync()
        tablet.sync()

        assertEquals("Pi", tablet.item("pi")!!.name)
        assertNull(remote.deleted["h1"])
    }

    @Test
    fun aHouseDeletedOfflineIsMarkedAtTheNextSync() {
        val (phone, _) = pairWithTwoHouses()
        phone.deleteHouse("h1")
        remote.failAt = remote.calls + 1
        assertThrows(IOException::class.java) { phone.sync() }
        assertNull(remote.deleted["h1"])
        assertEquals(setOf("h1"), phone.store.load().pendingDeletes)

        phone.sync()
        assertNotNull(remote.deleted["h1"])
        assertTrue(phone.store.load().pendingDeletes.isEmpty())
    }

    @Test
    fun aHouseNeverSyncedIsNotMarked() {
        val (phone, _) = pairWithTwoHouses()
        runBlocking { phone.db.houses().insert(House("h3", "Office", icon = "house", createdAt = 1, updatedAt = 1)) }
        phone.deleteHouse("h3")
        phone.sync()

        assertNull(remote.deleted["h3"])
        assertTrue(phone.store.load().pendingDeletes.isEmpty())
    }

    @Test
    fun firstConnectionUploadsTheData() {
        val phone = phone()
        val report = phone.sync()

        assertTrue(remote.exists)
        assertEquals(1, report.commits)
        assertEquals(1, remote.commits["h1"]!!.size)
        val deviceId = phone.store.load().deviceId
        assertEquals(setOf(deviceId), remote.refs["h1"]!!.keys)
        val entry = phone.engine.history().single()
        assertEquals("Vitor", entry.userName)
        assertEquals("phone", entry.deviceName)
        assertNotNull(phone.store.load().lastSuccess)
    }

    @Test
    fun nothingChanged() {
        val phone = phone()
        phone.sync()
        phone.sync()
        assertEquals(1, remote.commits["h1"]!!.size)
        assertEquals(1, phone.engine.history().size)
    }

    @Test
    fun aNewDeviceGetsTheHouse() {
        val (_, tablet) = pair()
        assertEquals("Pi", tablet.item("pi")!!.name)
        assertEquals("boxA", tablet.item("pi")!!.containerId)
    }

    @Test
    fun noConflict() {
        val (phone, tablet) = pair()
        phone.edit("pi") { it.copy(quantity = 2) }
        tablet.edit("pi") { it.copy(description = "4 GB") }
        phone.sync()
        tablet.sync()
        phone.sync()

        listOf(phone, tablet).forEach { d ->
            assertEquals(2, d.item("pi")!!.quantity)
            assertEquals("4 GB", d.item("pi")!!.description)
            assertTrue(d.store.load().conflicts.isEmpty())
        }
    }

    @Test
    fun sameField() {
        val (phone, tablet) = pair()
        phone.edit("pi") { it.copy(quantity = 2) }
        tablet.edit("pi") { it.copy(quantity = 5) }
        tablet.sync()
        phone.sync()

        val conflict = phone.store.load().conflicts.single().conflict
        assertEquals(ConflictKind.SameField, conflict.kind)
        assertEquals("quantity", conflict.field)
        assertEquals("2", conflict.mine.toString())
        assertEquals("5", conflict.theirs.toString())
        assertEquals(2, phone.item("pi")!!.quantity)
    }

    @Test
    fun editVersusDelete() {
        val (phone, tablet) = pair()
        runBlocking { phone.organize.trash(Selection(itemIds = listOf("pi"))) }
        tablet.edit("pi") { it.copy(name = "Raspberry Pi") }
        tablet.sync()
        phone.sync()

        val conflict = phone.store.load().conflicts.single().conflict
        assertEquals(ConflictKind.EditVersusDelete, conflict.kind)
        assertTrue(conflict.deletedHere)
        assertNotNull("still trashed here until resolved", runBlocking { phone.db.organize().itemAnyState("pi") }!!.deletedAt)
    }

    @Test
    fun anItemAddedIntoADeletedContainerMovesToTheTopLevel() {
        val (phone, tablet) = pair()
        runBlocking {
            phone.db.items().insert(Item("usb", "h1", "boxA", "USB stick", createdAt = 5, updatedAt = 5))
            val entries = tablet.organize.trash(Selection(containerIds = listOf("boxA")))
            entries.forEach { tablet.organize.deletePermanently(it) }
        }
        tablet.sync()
        phone.sync()

        assertNull(phone.item("usb")!!.containerId)
        assertNull(runBlocking { phone.db.containers().get("boxA") })
    }

    @Test
    fun photosUploadOnceAndWaitForWifi() {
        val phone = phone()
        val bytes = ByteArray(1_000) { it.toByte() }
        val sha = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        phone.photos.photoFile(sha).apply { parentFile?.mkdirs() }.writeBytes(bytes)
        runBlocking { phone.db.photos().insert(listOf(Photo("p1", "h1", Photo.OWNER_ITEM, "pi", sha, 0, 1), Photo("p2", "h1", Photo.OWNER_CONTAINER, "boxA", sha, 0, 1))) }

        assertEquals(1, phone.sync(photosAllowed = false).photosWaiting)
        assertTrue(remote.objects.isEmpty())
        phone.sync(photosAllowed = true)
        phone.sync(photosAllowed = true)
        assertEquals(1, remote.uploads)

        val tablet = device("tablet")
        tablet.sync(pull = setOf("h1"))
        assertTrue(tablet.photos.photoFile(sha).exists())
    }

    @Test
    fun oldAppNewData() {
        val (phone, tablet) = pair()
        remote.refs["h1"]!![tablet.store.load().deviceId] = Ref("future", 9, formatVersion = 99)
        phone.edit("pi") { it.copy(quantity = 7) }

        assertThrows(NewerFormatException::class.java) { phone.sync() }
        assertEquals(7, phone.item("pi")!!.quantity)
    }

    @Test
    fun folderDeleted() {
        val phone = phone()
        phone.sync()
        remote.exists = false
        assertThrows(DriveFolderMissingException::class.java) {
            runBlocking { phone.engine.sync(remote, DriveUser(), photosAllowed = true, create = false) }
        }
        assertTrue(phone.store.load().driveMissing)
        assertEquals("Pi", phone.item("pi")!!.name)
    }

    @Test
    fun filesAlteredOutsideTheApp() {
        val (phone, tablet) = pair()
        tablet.edit("pi") { it.copy(quantity = 4) }
        tablet.sync()
        val head = remote.refs["h1"]!![tablet.store.load().deviceId]!!.commitId
        remote.commits["h1"]!![head] = "garbage".toByteArray()

        assertThrows(DriveFolderMissingException::class.java) { phone.sync() }
        assertTrue(phone.store.load().driveMissing)
        assertEquals(1, phone.item("pi")!!.quantity)
    }

    @Test
    fun keepsThirtyCommitsPerDevice() {
        val phone = phone()
        repeat(35) { n ->
            phone.edit("pi") { it.copy(quantity = n + 2) }
            phone.sync()
        }
        assertEquals(SyncEngine.KEEP, remote.commits["h1"]!!.size)
        assertEquals(SyncEngine.KEEP, phone.engine.history().size)
    }

    @Test
    fun interruptedSync() {
        // The same two-device exchange, failing once at every remote call in turn, must end the same.
        fun exchange(failAt: Int?): Pair<Device, Device> {
            remote.refs.clear(); remote.commits.clear(); remote.objects.clear(); remote.exists = false; remote.calls = 0; remote.failAt = null
            val (phone, tablet) = pair()
            phone.edit("pi") { it.copy(quantity = 3) }
            tablet.edit("pi") { it.copy(description = "spare") }
            tablet.sync()
            remote.calls = 0
            remote.failAt = failAt
            runCatching { phone.sync() }
            remote.failAt = null
            phone.sync()
            tablet.sync()
            return phone to tablet
        }
        val (cleanPhone, _) = exchange(null)
        val expectedCommits = remote.commits["h1"]!!.size
        val expectedHistory = cleanPhone.engine.history().size
        for (step in 1..12) {
            val (phone, tablet) = exchange(step)
            listOf(phone, tablet).forEach { d ->
                assertEquals("step $step", 3, d.item("pi")!!.quantity)
                assertEquals("step $step", "spare", d.item("pi")!!.description)
                assertTrue("step $step", d.store.load().conflicts.isEmpty())
            }
            assertEquals("no duplicate commits at step $step", expectedCommits, remote.commits["h1"]!!.size)
            assertEquals("step $step", expectedHistory, phone.engine.history().size)
        }
    }

    @Test
    fun throughTheDriveLayout() {
        val drive = DriveRemoteTest.FakeDrive()
        val phone = phone()
        phone.sync(through = DriveRemote(drive))
        val tablet = device("tablet")
        tablet.sync(pull = setOf("h1"), through = DriveRemote(drive))
        tablet.edit("pi") { it.copy(quantity = 8) }
        tablet.sync(through = DriveRemote(drive))
        phone.sync(through = DriveRemote(drive))

        assertEquals(8, phone.item("pi")!!.quantity)
        assertEquals(1, drive.entries.values.count { it.file.name == "Trecos" })
    }

    @Test
    fun theCacheIsKeptOnTheDevice() {
        val phone = phone()
        phone.sync()
        val head = phone.store.load().houses["h1"]!!.head!!
        assertTrue(phone.store.cachedCommit("h1", head).exists())
        assertTrue(File(phone.store.cachedCommit("h1", head).path).length() > 0)
    }
}
