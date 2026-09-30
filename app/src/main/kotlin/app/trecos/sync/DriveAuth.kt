package app.trecos.sync

import android.app.Activity
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/** How asking for Drive access ended. */
sealed interface AuthOutcome {
    /**
     * Access granted.
     *
     * @property token an OAuth access token for Drive.
     */
    data class Granted(val token: String) : AuthOutcome

    /** The user closed the Google screen. */
    data object Cancelled : AuthOutcome

    /**
     * It didn't work, for example without Google Play services or a network.
     *
     * @property reason what went wrong, for the logs of a developer build only.
     */
    data class Failed(val reason: String) : AuthOutcome
}

/** Access to the user's Drive; tests use a fake, since the Google screens can't run there. */
interface DriveAuth {
    /**
     * Creates the "connect" action for a screen: it shows Google's account and consent screens when needed.
     *
     * @param onOutcome called with how it ended.
     * @return starts connecting.
     */
    @Composable
    fun rememberConnect(onOutcome: (AuthOutcome) -> Unit): () -> Unit

    /**
     * Gets a token without showing anything, for background syncs.
     *
     * @param context a context.
     * @return the token, or `null` when the user must connect again.
     */
    suspend fun token(context: Context): String?
}

/**
 * Google Identity's `AuthorizationClient` (design D14) with the `drive.file`
 * scope (only files Trecos created) and `drive.appdata` (the hidden app folder,
 * for the encryption key later). No password or token is stored by the app.
 */
object GoogleDriveAuth : DriveAuth {
    private val request: AuthorizationRequest = AuthorizationRequest.builder()
        .setRequestedScopes(listOf(Scope("https://www.googleapis.com/auth/drive.file"), Scope("https://www.googleapis.com/auth/drive.appdata")))
        .build()

    @Composable
    override fun rememberConnect(onOutcome: (AuthOutcome) -> Unit): () -> Unit {
        val context = LocalContext.current
        val consent = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
            if (result.resultCode != Activity.RESULT_OK) {
                onOutcome(AuthOutcome.Cancelled)
            } else {
                val granted = runCatching { Identity.getAuthorizationClient(context).getAuthorizationResultFromIntent(result.data) }.getOrNull()
                onOutcome(granted?.accessToken?.let { AuthOutcome.Granted(it) } ?: AuthOutcome.Failed("No token"))
            }
        }
        return remember(context, consent) {
            {
                Identity.getAuthorizationClient(context).authorize(request)
                    .addOnSuccessListener { result: AuthorizationResult ->
                        val pending = result.pendingIntent
                        when {
                            result.hasResolution() && pending != null -> consent.launch(IntentSenderRequest.Builder(pending.intentSender).build())
                            result.accessToken != null -> onOutcome(AuthOutcome.Granted(result.accessToken!!))
                            else -> onOutcome(AuthOutcome.Failed("No token"))
                        }
                    }
                    .addOnFailureListener { onOutcome(AuthOutcome.Failed(it.javaClass.simpleName)) }
            }
        }
    }

    override suspend fun token(context: Context): String? = suspendCancellableCoroutine { continuation ->
        Identity.getAuthorizationClient(context).authorize(request)
            .addOnSuccessListener { result -> continuation.resume(if (result.hasResolution()) null else result.accessToken) }
            .addOnFailureListener { continuation.resume(null) }
    }
}
