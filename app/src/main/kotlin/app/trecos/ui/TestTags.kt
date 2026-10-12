package app.trecos.ui

import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId

/**
 * Exposes the Compose test tags below this node as Android resource ids, so
 * the Maestro device flows can find controls by tag. Set at the activity's
 * root, and again at the root of every full-screen dialog, because a dialog
 * is its own window and does not inherit the flag.
 *
 * @return this modifier with the flag added.
 */
fun Modifier.exposeTestTags(): Modifier = semantics { testTagsAsResourceId = true }
