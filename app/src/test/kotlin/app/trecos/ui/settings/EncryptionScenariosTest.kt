package app.trecos.ui.settings

import android.content.Context
import android.net.Uri
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performScrollToNode
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import app.trecos.Features
import app.trecos.backup.BackupExporter
import app.trecos.crypto.AesKeyWrapper
import app.trecos.crypto.DatabaseCipher
import app.trecos.crypto.DatabaseKeys
import app.trecos.crypto.SnapshotCipher
import app.trecos.data.Container
import app.trecos.data.Fixtures.item
import app.trecos.data.House
import app.trecos.data.Item
import app.trecos.data.SearchIndex
import app.trecos.data.TrecosDatabase
import app.trecos.places.PhotoStore
import app.trecos.sync.AuthOutcome
import app.trecos.sync.DriveAuth
import app.trecos.sync.DriveSession
import app.trecos.sync.DriveUser
import app.trecos.sync.SyncEngine
import app.trecos.sync.SyncEngineTest
import app.trecos.sync.SyncStore
import app.trecos.ui.backup.BackupFiles
import app.trecos.ui.backup.LocalBackupFiles
import app.trecos.ui.places.PlacesTestBase
import app.trecos.ui.shell.TrecosApp
import app.trecos.ui.shell.TrecosTab
import app.trecos.ui.shell.tabTag
import app.trecos.ui.theme.TrecosTheme
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import javax.crypto.KeyGenerator
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLog

/**
 * Scenarios of the encryption spec through the UI (tasks 13.1-13.7). Tests
 * run on an in-memory database without SQLCipher, so the export is faked and
 * a "restart" is the next start's [app.trecos.crypto.EncryptionSwap.finish];
 * the real export runs in the device tests.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h800dp-xhdpi")
class EncryptionScenariosTest : PlacesTestBase() {

    @get:Rule
    val folder = TemporaryFolder()

    /** Always grants access. */
    private class Granting : DriveAuth {
        @Composable
        override fun rememberConnect(onOutcome: (AuthOutcome) -> Unit): () -> Unit = { onOutcome(AuthOutcome.Granted("tok")) }
        override suspend fun token(context: Context): String = "tok"
    }

    /** A file picker that records where an export would go. */
    private class Files(private val target: File) : BackupFiles {
        var created = 0

        @Composable
        override fun rememberPickers(onCreated: (Uri?) -> Unit, onOpened: (Uri?) -> Unit): Pair<(String) -> Unit, () -> Unit> =
            { _: String ->
                created++
                onCreated(Uri.fromFile(target))
            } to { onOpened(null) }

        override fun openOutput(context: Context, uri: Uri): OutputStream = File(uri.path!!).outputStream()
        override fun openInput(context: Context, uri: Uri): InputStream = File(uri.path!!).inputStream()
        override fun delete(context: Context, uri: Uri) = Unit
    }

    private val remote = SyncEngineTest.FakeRemote()
    private val vault = SyncEngineTest.FakeVault()
    private val user = DriveUser("Vitor", "v@example.com")
    private var restarts = 0
    private var exportFails = false

    @Before
    fun useFakes() {
        WorkManagerTestInitHelper.initializeTestWorkManager(context(), Configuration.Builder().setExecutor(SynchronousExecutor()).build())
        app.features = Features(driveSync = true, encryption = true)
        app.sync.auth = Granting()
        app.sync.connectTo = { DriveSession(remote, { user }, vault) }
        app.sync.onUnmeteredNetwork = { true }
        app.encryption.wrapper = AesKeyWrapper(KeyGenerator.getInstance("AES").apply { init(256) }.generateKey())
        app.encryption.cipher = DatabaseCipher { _, target, key, _ ->
            if (exportFails) error("Power lost")
            target.writeText(if (key == null) "plain" else "encrypted")
        }
        app.encryption.restart = { restarts++ }
    }

    /** @return the application context. */
    private fun context(): Context = ApplicationProvider.getApplicationContext()

    /** @return whether a node with the tag exists. */
    private fun exists(tag: String) = rule.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    /** Opens Settings at the encryption row. */
    private fun openEncryption() {
        click(tabTag(TrecosTab.Settings))
        tag(SETTINGS_LIST_TAG).performScrollToNode(hasTestTag("setting_encryption"))
    }

    /** Connects sync as the user would. */
    private fun connectSync() {
        runBlocking { app.sync.connect("tok") }
    }

    /** What the next start of the app does: finish the prepared swap. */
    private fun restart() {
        app.encryption.swap.finish()
        app.encryption.refresh()
    }

    /** Turns encryption on through the UI and restarts. */
    private fun turnOn() {
        openEncryption()
        click("setting_encryption")
        click("confirm_encryption")
        eventually(10_000) { restarts == 1 }
        restart()
    }

    @Test
    fun theSettingIsHiddenWhenTheFlagIsOff() {
        app.features = Features(driveSync = true, encryption = false)
        seed()
        click(tabTag(TrecosTab.Settings))
        tag(SETTINGS_LIST_TAG).performScrollToNode(hasTestTag("setting_app_lock"))
        assertFalse(exists("setting_encryption"))
    }

    @Test
    fun turningEncryptionOn() {
        seed(items = listOf(item("pi").copy(name = "Raspberry Pi")))
        connectSync()
        turnOn()

        assertTrue(app.encryption.keys.isEncrypted())
        assertArrayEquals("the recovery copy is the key in use", app.encryption.keys.current(), vault.key)
        waitForTextIn("value_encryption", "On")
    }

    @Test
    fun noGoogleAccount() {
        seed()
        openEncryption()
        click("setting_encryption")

        tag("encryption_needs_account").assertTextContains("Google account", substring = true)
        click("encryption_connect")
        tag("connect_drive")
        assertFalse(app.encryption.keys.isEncrypted())
    }

    @Test
    fun interruptedEncryption() {
        seed(items = listOf(item("pi").copy(name = "Raspberry Pi")))
        connectSync()
        exportFails = true
        openEncryption()
        click("setting_encryption")
        click("confirm_encryption")

        eventually(10_000) { rule.onAllNodes(hasText("Encryption didn't finish", substring = true)).fetchSemanticsNodes().isNotEmpty() }
        restart()
        assertFalse(app.encryption.keys.isEncrypted())
        assertFalse(app.encryption.swap.isPending())
        assertEquals(0, restarts)
        assertEquals("Raspberry Pi", runBlocking { app.database.items().get("pi") }!!.name)
        waitForTextIn("value_encryption", "Off")
    }

    @Test
    fun interruptedByAShutdown() {
        seed(items = listOf(item("pi").copy(name = "Raspberry Pi")))
        // The phone died during the export: only the "exporting" marker and half a file are left.
        app.encryption.swap.staged.apply { parentFile?.mkdirs() }.writeText("half")
        File(context().noBackupFilesDir, "crypto").apply { mkdirs() }.resolve("swap.phase").writeText("exporting")
        restart()

        assertFalse(app.encryption.keys.isEncrypted())
        assertFalse(app.encryption.swap.staged.exists())
        openEncryption()
        waitForTextIn("value_encryption", "Off")
    }

    @Test
    fun driveCopy() {
        seed(items = listOf(item("pi").copy(name = "Raspberry Pi")))
        connectSync()
        turnOn()
        runBlocking { app.database.items().update(app.database.items().get("pi")!!.copy(quantity = 3)) }
        runBlocking { app.sync.syncNow() }

        val head = remote.refs["h1"]!!.values.maxByOrNull { it.time }!!.commitId
        val file = remote.commits["h1"]!!.getValue(head)
        assertTrue(SnapshotCipher.isEncrypted(file))
        assertFalse(String(file, Charsets.ISO_8859_1).contains("Raspberry"))
    }

    @Test
    fun lostPhone() {
        // The old phone had encryption on and synced.
        val key = DatabaseKeys.newKey()
        vault.key = key
        val old = Room.inMemoryDatabaseBuilder(context(), TrecosDatabase::class.java).allowMainThreadQueries().addCallback(SearchIndex.onCreate).build()
        runBlocking {
            old.houses().insert(House("h9", "Parents", icon = "house", createdAt = 1, updatedAt = 1))
            old.containers().insert(Container("shelf", "h9", null, "Shelf", icon = "box", createdAt = 1, updatedAt = 1))
            old.items().insert(Item("lamp", "h9", "shelf", "Lamp", createdAt = 1, updatedAt = 1))
            SyncEngine(old, PhotoStore(folder.newFolder(), context().contentResolver), SyncStore(folder.newFolder(), "Old phone"), clock = { 5 }, writeKey = { key })
                .sync(remote, user, photosAllowed = true, create = true)
        }
        old.close()
        assertTrue(SnapshotCipher.isEncrypted(remote.commits["h9"]!!.values.single()))

        // The new phone has only its empty first house.
        seed(houses = listOf(House("h1", "My home", icon = "house", createdAt = 1, updatedAt = 1)))
        click(tabTag(TrecosTab.Settings))
        tag(SETTINGS_LIST_TAG).performScrollToNode(hasTestTag("setting_sync"))
        click("setting_sync")
        click("connect_drive")
        click("restore")

        eventually(10_000) { runBlocking { app.database.items().get("lamp") } != null }
        eventually(10_000) { restarts == 1 }
        restart()
        assertArrayEquals("this phone is encrypted with the account's key", key, app.encryption.keys.current())
    }

    @Test
    fun disabling() {
        seed(items = listOf(item("pi").copy(name = "Raspberry Pi")))
        connectSync()
        turnOn()
        openEncryption()
        click("setting_encryption")
        click("confirm_decryption")
        eventually(10_000) { restarts == 2 }
        restart()

        assertFalse(app.encryption.keys.isEncrypted())
        assertNull("the key left Drive", vault.key)
        runBlocking { app.database.items().update(app.database.items().get("pi")!!.copy(quantity = 4)) }
        runBlocking { app.sync.syncNow() }
        val head = remote.refs["h1"]!!.values.maxByOrNull { it.time }!!.commitId
        assertFalse("the next sync uploads unencrypted copies", SnapshotCipher.isEncrypted(remote.commits["h1"]!!.getValue(head)))
    }

    @Test
    fun disconnectingWithEncryptionOn() {
        seed()
        connectSync()
        turnOn()
        click(tabTag(TrecosTab.Settings))
        tag(SETTINGS_LIST_TAG).performScrollToNode(hasTestTag("setting_sync"))
        click("setting_sync")
        tag("sync_list").performScrollToNode(hasTestTag("disconnect"))
        click("disconnect")

        eventually(10_000) { rule.onAllNodes(hasText("Turn encryption off before disconnecting", substring = true)).fetchSemanticsNodes().isNotEmpty() }
        assertTrue(app.sync.store.load().connected)
    }

    @Test
    fun exportWithEncryptionOn() {
        seed()
        connectSync()
        turnOn()
        val target = folder.newFile("backup.zip").apply { delete() }
        val files = Files(target)
        rule.runOnUiThread { rule.activity.setContent { CompositionLocalProvider(LocalBackupFiles provides files) { TrecosTheme { TrecosApp() } } } }
        click(tabTag(TrecosTab.Settings))
        tag(SETTINGS_LIST_TAG).performScrollToNode(hasTestTag("setting_backup"))
        click("setting_backup")
        click("export")

        tag("export_unencrypted")
        assertEquals("nothing is written unless I continue", 0, files.created)
        click("export_anyway")
        eventually(10_000) { target.exists() && target.length() > 0 }
    }

    @Test
    fun theKeyNeverReachesLogsOrExports() {
        seed(items = listOf(item("pi").copy(name = "Raspberry Pi")))
        ShadowLog.clear()
        connectSync()
        turnOn()
        val key = app.encryption.keys.current()!!
        val hex = String(DatabaseKeys.passphrase(key))
        val backup = folder.newFile("b.zip")
        runBlocking { BackupExporter(app.database, app.photoStore).export(emptySet(), backup.outputStream(), 1, "t") }

        val logged = ShadowLog.getLogs().joinToString("\n") { "${it.tag} ${it.msg}" }
        assertFalse(logged.contains(hex))
        val bytes = backup.readBytes()
        assertFalse(String(bytes, Charsets.ISO_8859_1).contains(hex))
        assertFalse(bytes.toList().windowed(key.size).any { it == key.toList() })
        assertNotNull(vault.key)
    }
}
