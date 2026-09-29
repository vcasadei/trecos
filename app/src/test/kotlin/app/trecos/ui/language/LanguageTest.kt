package app.trecos.ui.language

import android.content.pm.PackageManager
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.app.AppLocalesMetadataHolderService
import androidx.core.os.LocaleListCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import android.content.ComponentName
import android.content.Context
import app.trecos.MainActivity
import app.trecos.R
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Scenarios of the app-shell "Languages" requirement.
 */
@RunWith(RobolectricTestRunner::class)
class LanguageTest {

    @After
    fun clearChoice() {
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList())
    }

    @Test
    @Config(qualifiers = "pt-rBR")
    fun firstLaunchOnAPortuguesePhone() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity {
                assertEquals(AppLanguage.PortugueseBrazil, AppLanguage.current())
                assertEquals("Início", it.getString(R.string.tab_home))
                assertEquals("Buscar", it.getString(R.string.tab_search))
                assertEquals("Ajustes", it.getString(R.string.tab_settings))
            }
        }
    }

    @Test
    @Config(qualifiers = "pt-rPT")
    fun firstLaunchOnAPortugalPortuguesePhone() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity {
                assertEquals(AppLanguage.PortugueseBrazil, AppLanguage.current())
                assertEquals("Início", it.getString(R.string.tab_home))
            }
        }
    }

    @Test
    @Config(qualifiers = "de-rDE")
    fun unsupportedDeviceLanguage() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity {
                assertEquals(AppLanguage.English, AppLanguage.current())
                assertEquals("Home", it.getString(R.string.tab_home))
            }
        }
    }

    @Test
    @Config(sdk = [28], qualifiers = "pt-rBR")
    fun changingLanguageInTheAppOnAndroid9() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { assertEquals("Início", it.getString(R.string.tab_home)) }

            scenario.onActivity { AppLanguage.apply(AppLanguage.English) }

            scenario.onActivity {
                assertEquals(AppLanguage.English, AppLanguage.current())
                assertEquals("Home", it.getString(R.string.tab_home))
            }
        }
    }

    @Test
    @Config(sdk = [28])
    fun choiceIsStoredForRestartsOnAndroid9() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val service = context.packageManager.getServiceInfo(
            ComponentName(context, AppLocalesMetadataHolderService::class.java),
            PackageManager.GET_META_DATA or PackageManager.MATCH_DISABLED_COMPONENTS,
        )
        assertTrue(service.metaData.getBoolean("autoStoreLocales"))
    }

    @Test
    fun resolvePicksTheFirstSupportedLocale() {
        val german = java.util.Locale.GERMANY
        val brazil = java.util.Locale.forLanguageTag("pt-BR")
        val portugal = java.util.Locale.forLanguageTag("pt-PT")
        assertEquals(AppLanguage.PortugueseBrazil, AppLanguage.resolve(listOf(german, brazil)))
        assertEquals(AppLanguage.PortugueseBrazil, AppLanguage.resolve(listOf(german, portugal)))
        assertEquals(AppLanguage.English, AppLanguage.resolve(listOf(german)))
        assertEquals(AppLanguage.English, AppLanguage.resolve(emptyList()))
    }
}
