package app.trecos.lock

import android.app.KeyguardManager
import android.content.Context
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * How long the app may stay in the background before it locks again.
 *
 * @property millis the timeout.
 */
enum class LockTimeout(val millis: Long) {
    Immediately(0),
    OneMinute(60_000),
    FiveMinutes(5 * 60_000),
    FifteenMinutes(15 * 60_000),
}

/** When the app must be unlocked (spec "When the app locks"). */
object LockPolicy {
    /**
     * @param enabled whether the app lock is on.
     * @param backgroundedAt when the app last went to the background, or `null` on a fresh start.
     * @param now the current time.
     * @param timeout the chosen timeout.
     * @return whether the user must unlock now.
     */
    fun mustUnlock(enabled: Boolean, backgroundedAt: Long?, now: Long, timeout: LockTimeout): Boolean = when {
        !enabled -> false
        backgroundedAt == null -> true
        else -> now - backgroundedAt >= timeout.millis
    }
}

/** The phone's own lock: whether one is set, and asking the user to pass it. */
interface DeviceSecurity {
    /** @return whether the phone has a PIN, pattern, password or biometric lock. */
    fun isScreenLockSet(): Boolean

    /**
     * Asks for the phone's fingerprint, face, PIN, pattern or password.
     *
     * @param activity the activity showing the prompt.
     * @param title the prompt's title.
     * @param onResult called with `true` when the user passed, `false` when they cancelled or failed.
     */
    fun authenticate(activity: FragmentActivity, title: String, onResult: (Boolean) -> Unit)
}

/**
 * [DeviceSecurity] on the real phone (design D17): `BiometricPrompt` with the
 * device credential as fallback, which also covers Android 9 and 10.
 *
 * @param context the application context.
 */
class SystemDeviceSecurity(private val context: Context) : DeviceSecurity {
    override fun isScreenLockSet(): Boolean = context.getSystemService(KeyguardManager::class.java)?.isDeviceSecure == true

    override fun authenticate(activity: FragmentActivity, title: String, onResult: (Boolean) -> Unit) {
        val prompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onResult(true)

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) = onResult(false)
            },
        )
        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder().setTitle(title).setAllowedAuthenticators(BIOMETRIC_WEAK or DEVICE_CREDENTIAL).build(),
        )
    }
}

/**
 * The lock state of the running app. It lives as long as the process, so a
 * fresh start always begins locked when the lock is on.
 *
 * @param clock the current time; tests move it forward.
 */
class AppLock(var clock: () -> Long) {
    private val lockedState = MutableStateFlow(false)
    private val noticeState = MutableStateFlow(false)
    private var backgroundedAt: Long? = null

    /** Whether the app is locked, hiding every screen. */
    val locked: StateFlow<Boolean> = lockedState.asStateFlow()

    /** Whether to tell the user the lock was turned off because the phone lost its screen lock. */
    val turnedOffNotice: StateFlow<Boolean> = noticeState.asStateFlow()

    /**
     * Called when the app comes to the foreground.
     *
     * @param enabled whether the app lock is on.
     * @param timeout the chosen timeout.
     * @param screenLockSet whether the phone still has a screen lock.
     * @return `true` when the lock setting must be turned off because the screen lock is gone.
     */
    fun onForeground(enabled: Boolean, timeout: LockTimeout, screenLockSet: Boolean): Boolean {
        if (enabled && !screenLockSet) {
            lockedState.value = false
            noticeState.value = true
            return true
        }
        if (LockPolicy.mustUnlock(enabled, backgroundedAt, clock(), timeout)) lockedState.value = true
        return false
    }

    /** Called when the app goes to the background. */
    fun onBackground() {
        if (!lockedState.value) backgroundedAt = clock()
    }

    /** The user unlocked. */
    fun unlocked() {
        lockedState.value = false
        backgroundedAt = clock()
    }

    /** The user saw the "lock turned off" notice. */
    fun noticeSeen() {
        noticeState.value = false
    }
}
