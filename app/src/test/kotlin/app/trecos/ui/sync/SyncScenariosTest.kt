package app.trecos.ui.sync

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToNode
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import android.net.Uri
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import app.trecos.Features
import app.trecos.backup.BackupExporter
import app.trecos.backup.HouseSnapshot
import app.trecos.backup.SnapshotFormat
import app.trecos.data.Item
import app.trecos.sync.Ref
import app.trecos.ui.backup.BackupFiles
import app.trecos.ui.backup.LocalBackupFiles
import app.trecos.ui.shell.TrecosApp
import app.trecos.ui.theme.TrecosTheme
import java.io.File
import kotlinx.serialization.json.JsonNull
import app.trecos.data.Container
import app.trecos.data.Fixtures.item
import app.trecos.data.House
import app.trecos.data.Photo
import app.trecos.data.SearchIndex
import app.trecos.data.SyncFrequency
import app.trecos.data.TrecosDatabase
import app.trecos.places.PhotoStore
import app.trecos.places.Selection
import app.trecos.sync.AuthOutcome
import app.trecos.sync.Conflict
import app.trecos.sync.ConflictKind
import app.trecos.sync.DriveAuth
import app.trecos.sync.DriveSession
import app.trecos.sync.DriveUser
import app.trecos.sync.StoredConflict
import app.trecos.sync.SyncEngine
import app.trecos.sync.SyncEngineTest
import app.trecos.sync.SyncStore
import app.trecos.sync.SyncWorker
import app.trecos.ui.places.PlacesTestBase
import app.trecos.ui.settings.SETTINGS_LIST_TAG
import app.trecos.ui.shell.TrecosTab
import app.trecos.ui.shell.tabTag
import java.security.MessageDigest
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Scenarios of the sync spec through the UI (tasks 12.1, 12.3, 12.7-12.11),
 * with a fake Google sign-in and an in-memory Drive.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h800dp-xhdpi")
class SyncScenariosTest : PlacesTestBase() {

    @get:Rule
    val folder = TemporaryFolder()

    /** Answers the sign-in as configured. */
    private class FakeAuth : DriveAuth {
        var outcome: AuthOutcome = AuthOutcome.Granted("tok")

        @Composable
        override fun rememberConnect(onOutcome: (AuthOutcome) -> Unit): () -> Unit = { onOutcome(outcome) }

        override suspend fun token(context: Context): String? = (outcome as? AuthOutcome.Granted)?.token
    }

    private val auth = FakeAuth()
    private val remote = SyncEngineTest.FakeRemote()
    private val user = DriveUser("Vitor", "v@example.com")
    private val vault = SyncEngineTest.FakeVault()

    @Before
    fun useFakes() {
        initWorkManager()
        app.features = Features(driveSync = true)
        app.sync.auth = auth
        app.sync.connectTo = { DriveSession(remote, { user }, vault) }
        app.sync.onUnmeteredNetwork = { true }
    }

    /** @return whether a node with the tag exists. */
    private fun exists(tag: String) = rule.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    /** Opens Settings > Sync. */
    private fun openSync() {
        click(tabTag(TrecosTab.Settings))
        tag(SETTINGS_LIST_TAG).performScrollToNode(hasTestTag("setting_sync"))
        click("setting_sync")
    }

    /** Waits for text anywhere. */
    private fun shows(text: String) =
        eventually(10_000) { rule.onAllNodes(hasText(text, substring = true), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }

    /** Seeds a house with an item. */
    private fun house() = seed(items = listOf(item("pi").copy(name = "Raspberry Pi")))

    /** Connects through the screen. */
    private fun connect() {
        openSync()
        click("connect_drive")
        eventually(10_000) { app.sync.store.load().connected && app.sync.store.load().lastSuccess != null }
    }

    @Test
    fun settingsShowsNoSyncWhenTheFlagIsOff() {
        app.features = Features(driveSync = false)
        seed()
        click(tabTag(TrecosTab.Settings))
        tag(SETTINGS_LIST_TAG).performScrollToNode(hasTestTag("setting_backup"))
        assertFalse(exists("setting_sync"))
    }

    @Test
    fun firstConnection() {
        house()
        connect()

        assertTrue("the Trecos folder was created", remote.exists)
        assertEquals(1, remote.commits["h1"]!!.size)
        waitForTextIn("sync_account", "v@example.com")
    }

    @Test
    fun signInCancelled() {
        house()
        auth.outcome = AuthOutcome.Cancelled
        openSync()
        click("connect_drive")

        rule.waitForIdle()
        assertFalse(app.sync.store.load().connected)
        assertFalse(remote.exists)
        tag("connect_drive")
    }

    @Test
    fun driveUnreachable() {
        house()
        connect()
        remote.failAt = remote.calls + 1
        click("sync_now")

        eventually(10_000) { app.sync.store.load().lastError != null }
        tag("sync_error").assertTextContains("Couldn't reach Google Drive", substring = true)
        shows("Trecos will try again automatically.")
        shows("Last sync:")
    }

    @Test
    fun viewingHistory() {
        house()
        app.sync.rename("Pixel 6")
        connect()
        click("open_history")

        shows("Vitor, Pixel 6, ")
        shows("changes")
    }

    @Test
    fun resolvingASameFieldConflict() {
        house()
        runBlocking { app.database.items().update(app.database.items().get("pi")!!.copy(quantity = 2)) }
        connect()
        addConflict(Conflict("item/pi", ConflictKind.SameField, "quantity", JsonPrimitive(2), JsonPrimitive(5)))
        openConflicts()

        tag("conflict_question").assertTextContains("Quantity: 2 or 5?")
        click("keep_theirs")
        eventually(10_000) { runBlocking { app.database.items().get("pi") }?.quantity == 5 }
        tag("no_conflicts")
    }

    @Test
    fun keepingMineLeavesTheLocalValue() {
        house()
        connect()
        addConflict(Conflict("item/pi", ConflictKind.SameField, "name", JsonPrimitive("Raspberry Pi"), JsonPrimitive("Pi 4")))
        openConflicts()
        click("keep_mine")

        tag("no_conflicts")
        assertEquals("Raspberry Pi", runBlocking { app.database.items().get("pi") }!!.name)
    }

    @Test
    fun keepingAnItemTrashedHere() {
        house()
        connect()
        val before = runBlocking { app.database.items().get("pi")!! }
        runBlocking { app.organize.trash(Selection(itemIds = listOf("pi"))) }
        val rows = SnapshotFormat.toRows(runBlocking { HouseSnapshot.read(app.database, app.database.houses().get("h1")!!) })
        val theirs = JsonObject(rows.getValue("item/pi") + mapOf("deletedAt" to JsonNull, "name" to JsonPrimitive("Pi 4")))
        addConflict(Conflict("item/pi", ConflictKind.EditVersusDelete, mine = null, theirs = theirs, deletedHere = true))
        openConflicts()
        click("conflict_keep")

        eventually(10_000) { runBlocking { app.database.items().get("pi") }?.name == "Pi 4" }
        assertTrue(runBlocking { app.database.snapshots().trash("h1") }.isEmpty())
        assertEquals(before.id, runBlocking { app.database.items().get("pi") }!!.id)
    }

    @Test
    fun deletingAnItemDeletedThere() {
        house()
        connect()
        addConflict(Conflict("item/pi", ConflictKind.EditVersusDelete, mine = null, theirs = null, deletedHere = false))
        openConflicts()
        tag("conflict_question").assertTextContains("deleted or trashed on", substring = true)
        click("conflict_delete")

        eventually(10_000) { runBlocking { app.database.organize().itemAnyState("pi") } == null }
    }

    @Test
    fun conflictsShowABadgeInSettings() {
        house()
        connect()
        addConflict(Conflict("item/pi", ConflictKind.SameField, "quantity", JsonPrimitive(1), JsonPrimitive(3)))
        pressBack()
        tag(SETTINGS_LIST_TAG).performScrollToNode(hasTestTag("setting_sync"))
        tag("sync_badge").assertTextContains("1 conflict to resolve")
    }

    @Test
    fun newPhone() {
        otherDeviceUploads("h9", "Parents")
        seed(houses = listOf(House("h1", "My home", icon = "house", createdAt = 1, updatedAt = 1)))
        openSync()
        click("connect_drive")

        tag("restore_offer").assertTextContains("Parents", substring = true)
        click("restore")
        eventually(10_000) { runBlocking { app.database.houses().get("h9") } != null }
        assertNull("the empty first house was replaced", runBlocking { app.database.houses().get("h1") })
        assertEquals("Lamp", runBlocking { app.database.items().get("lamp") }!!.name)
    }

    @Test
    fun mergingWithDriveData() {
        otherDeviceUploads("h9", "Parents")
        house()
        openSync()
        click("connect_drive")

        tag("merge_offer")
        click("merge")
        eventually(10_000) { runBlocking { app.database.houses().get("h9") } != null }
        assertTrue(runBlocking { app.database.houses().get("h1") } != null)
    }

    @Test
    fun keepingOnlyDriveData() {
        otherDeviceUploads("h9", "Parents")
        house()
        openSync()
        click("connect_drive")
        click("keep_drive")

        eventually(10_000) { runBlocking { app.database.houses().get("h9") } != null && runBlocking { app.database.houses().get("h1") } == null }
    }

    @Test
    fun oldAppNewData() {
        house()
        connect()
        remote.refs["h1"]!!["tablet"] = Ref("future", 1, formatVersion = 99)
        click("sync_now")

        eventually(10_000) { app.sync.store.load().lastError != null }
        tag("sync_error").assertTextContains("newer version of Trecos", substring = true)
    }

    @Test
    fun folderDeleted() {
        house()
        connect()
        remote.exists = false
        click("sync_now")

        tag("upload_again")
        click("upload_again")
        eventually(10_000) { remote.exists && !app.sync.store.load().driveMissing }
    }

    @Test
    fun disconnectingKeepsTheData() {
        house()
        connect()
        tag("sync_list").performScrollToNode(hasTestTag("disconnect"))
        click("disconnect")

        eventually(10_000) { !app.sync.store.load().connected }
        assertEquals("Raspberry Pi", runBlocking { app.database.items().get("pi") }!!.name)
        tag("connect_drive")
    }

    @Test
    fun replacingWithSyncOn() {
        house()
        val file = folder.newFile("b.zip")
        runBlocking { BackupExporter(app.database, app.photoStore).export(emptySet(), file.outputStream(), 1, "t") }
        rule.runOnUiThread {
            rule.activity.setContent {
                CompositionLocalProvider(LocalBackupFiles provides OpenOnly(file)) { TrecosTheme { TrecosApp() } }
            }
        }
        connect()
        pressBack()
        tag(SETTINGS_LIST_TAG).performScrollToNode(hasTestTag("setting_backup"))
        click("setting_backup")
        click("import")
        click("import_replace")

        tag("replace_confirm").assertTextContains("your other devices will receive this data at the next sync", substring = true)
    }

    /** A file picker that always opens [file]. */
    private class OpenOnly(private val file: File) : BackupFiles {
        @Composable
        override fun rememberPickers(onCreated: (Uri?) -> Unit, onOpened: (Uri?) -> Unit): Pair<(String) -> Unit, () -> Unit> =
            { _: String -> onCreated(null) } to { onOpened(Uri.fromFile(file)) }

        override fun openOutput(context: Context, uri: Uri) = error("Not used")
        override fun openInput(context: Context, uri: Uri) = File(uri.path!!).inputStream()
        override fun delete(context: Context, uri: Uri) = Unit
    }

    @Test
    fun dailySync() {
        house()
        connect()
        val work = WorkManager.getInstance(context())
        SyncWorker.schedule(context(), SyncFrequency.Daily)
        val info = work.getWorkInfosForUniqueWork(SyncWorker.NAME).get().single()
        runBlocking { app.database.items().update(app.database.items().get("pi")!!.copy(quantity = 9)) }
        val before = remote.commits["h1"]!!.size
        val driver = WorkManagerTestInitHelper.getTestDriver(context())!!
        driver.setAllConstraintsMet(info.id)
        driver.setPeriodDelayMet(info.id)

        eventually(10_000) { remote.commits["h1"]!!.size == before + 1 }
        // The worker may still be finishing after the commit is written; a periodic job then waits for its next run.
        eventually(10_000) { work.getWorkInfoById(info.id).get()!!.state == WorkInfo.State.ENQUEUED }
        assertTrue(info.periodicityInfo!!.repeatIntervalMillis >= 24 * 60 * 60 * 1000L)
    }

    @Test
    fun neverCancelsTheSchedule() {
        SyncWorker.schedule(context(), SyncFrequency.Every5Days)
        SyncWorker.schedule(context(), SyncFrequency.Never)
        val infos = WorkManager.getInstance(context()).getWorkInfosForUniqueWork(SyncWorker.NAME).get()
        assertTrue(infos.all { it.state == WorkInfo.State.CANCELLED })
    }

    @Test
    fun mobileData() {
        house()
        val bytes = ByteArray(500) { it.toByte() }
        val sha = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        app.photoStore.photoFile(sha).apply { parentFile?.mkdirs() }.writeBytes(bytes)
        runBlocking { app.database.photos().insert(listOf(Photo("p1", "h1", Photo.OWNER_ITEM, "pi", sha, 0, 1))) }
        app.sync.onUnmeteredNetwork = { false }
        connect()

        assertEquals(1, remote.commits["h1"]!!.size)
        assertTrue("photos wait for Wi-Fi", remote.objects.isEmpty())
        app.sync.onUnmeteredNetwork = { true }
        click("sync_now")
        eventually(10_000) { remote.objects.containsKey(sha) }
    }

    /** Adds a conflict as a sync would. */
    private fun addConflict(conflict: Conflict) {
        app.sync.store.update { it.copy(conflicts = it.conflicts + StoredConflict("h1", conflict, "Tablet")) }
        app.sync.refresh()
    }

    /** Opens the conflicts from the sync screen, where [connect] leaves the app. */
    private fun openConflicts() {
        tag("sync_list").performScrollToNode(hasTestTag("open_conflicts"))
        click("open_conflicts")
    }

    /** Another device uploads a house with a lamp to the fake Drive. */
    private fun otherDeviceUploads(houseId: String, name: String) {
        val context = context()
        val db = Room.inMemoryDatabaseBuilder(context, TrecosDatabase::class.java).allowMainThreadQueries().addCallback(SearchIndex.onCreate).build()
        runBlocking {
            db.houses().insert(House(houseId, name, icon = "house", createdAt = 1, updatedAt = 1))
            db.containers().insert(Container("shelf", houseId, null, "Shelf", icon = "box", createdAt = 1, updatedAt = 1))
            db.items().insert(Item("lamp", houseId, "shelf", "Lamp", createdAt = 1, updatedAt = 1))
            val engine = SyncEngine(db, PhotoStore(folder.newFolder(), context.contentResolver), SyncStore(folder.newFolder(), "Tablet"), clock = { 5 })
            engine.sync(remote, user, photosAllowed = true, create = true)
        }
        db.close()
    }

    /** @return the application context. */
    private fun context(): Context = ApplicationProvider.getApplicationContext()

    /** Uses the WorkManager test helpers. */
    private fun initWorkManager() {
        WorkManagerTestInitHelper.initializeTestWorkManager(context(), Configuration.Builder().setExecutor(SynchronousExecutor()).build())
    }
}
