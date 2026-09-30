package app.trecos.ui.places

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.trecos.R
import app.trecos.data.ImageSource
import app.trecos.data.Photo
import app.trecos.ui.appContainer
import app.trecos.ui.theme.LocalMotion
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** The zoom level of the full-screen viewer, for tests. */
val ZoomLevel = SemanticsPropertyKey<Float>("ZoomLevel")

/** Exposes the viewer's zoom level to tests. */
var SemanticsPropertyReceiver.zoomLevel by ZoomLevel

/**
 * Returns the test tag of a photo thumbnail.
 *
 * @param sha the photo's SHA-256.
 * @return the tag, such as `photo_<sha>`.
 */
fun photoTag(sha: String): String = "photo_$sha"

/**
 * A photo's square thumbnail, regenerated from the stored photo when missing.
 *
 * @param sha the photo's SHA-256.
 * @param size the thumbnail's side.
 * @param modifier modifier for the thumbnail.
 */
@Composable
fun PhotoThumb(sha: String, size: Dp, modifier: Modifier = Modifier) {
    val store = appContainer().photoStore
    val file by produceState<java.io.File?>(null, sha) { value = store.thumbnail(sha) }
    Box(
        modifier
            .size(size)
            .clip(RoundedCornerShape(size / 6))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .testTag(photoTag(sha)),
    ) {
        file?.let { AsyncImage(model = it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
    }
}

/**
 * The photos part of a form: thumbnails (the first is the main photo) with
 * "Set as main" and "Remove", long-press drag to reorder, and "Add photo",
 * which asks Camera or Gallery the first time and is unavailable at three photos.
 *
 * @param photos the photos' SHA-256s, main first.
 * @param onPicked called with newly picked images to import.
 * @param onSetMain makes a photo the main one.
 * @param onRemove removes a photo.
 * @param onMove moves a photo from one position to another.
 * @param requestAdd when set, opens the add flow at once (photo-first mode); `onAddRequested` resets it.
 * @param onAddRequested called once the requested add flow has opened.
 */
@Composable
fun PhotoEditor(
    photos: List<String>,
    onPicked: (List<Uri>) -> Unit,
    onSetMain: (String) -> Unit,
    onRemove: (String) -> Unit,
    onMove: (from: Int, to: Int) -> Unit,
    requestAdd: Boolean = false,
    onAddRequested: () -> Unit = {},
) {
    val app = appContainer()
    val scope = rememberCoroutineScope()
    val source by app.preferences.imageSource.collectAsState(initial = null)
    val launchers = LocalPhotoSource.current.rememberLaunchers(onPicked)
    var asking by remember { mutableStateOf(false) }
    val remaining = Photo.MAX_PER_OWNER - photos.size

    fun add() {
        when (source) {
            ImageSource.Camera -> launchers.camera()
            ImageSource.Gallery -> launchers.gallery(remaining)
            else -> asking = true
        }
    }
    if (requestAdd && source != null) {
        onAddRequested()
        if (remaining > 0) add() else onPicked(emptyList())
    }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(R.string.field_photos), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            photos.forEachIndexed { index, sha -> EditablePhoto(sha, index, photos.size, onSetMain, onRemove, onMove) }
            OutlinedButton(onClick = { add() }, enabled = remaining > 0, modifier = Modifier.testTag("add_photo")) {
                Icon(painterResource(R.drawable.ic_add), contentDescription = null)
                Text(stringResource(R.string.add_photo))
            }
        }
        if (remaining <= 0) {
            Text(stringResource(R.string.photo_limit), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag("photo_limit"))
        }
    }
    if (asking) {
        var remember by remember { mutableStateOf(true) }
        fun choose(choice: ImageSource) {
            asking = false
            if (remember) scope.launch { app.preferences.setImageSource(choice) }
            if (choice == ImageSource.Camera) launchers.camera() else launchers.gallery(remaining)
        }
        AlertDialog(
            onDismissRequest = { asking = false },
            title = { Text(stringResource(R.string.source_title)) },
            text = {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { remember = !remember }) {
                    Checkbox(checked = remember, onCheckedChange = { remember = it }, modifier = Modifier.testTag("remember_choice"))
                    Text(stringResource(R.string.remember_choice))
                }
            },
            confirmButton = {
                TextButton(onClick = { choose(ImageSource.Camera) }, modifier = Modifier.testTag("source_camera")) { Text(stringResource(R.string.source_camera)) }
            },
            dismissButton = {
                TextButton(onClick = { choose(ImageSource.Gallery) }, modifier = Modifier.testTag("source_gallery")) { Text(stringResource(R.string.source_gallery)) }
            },
        )
    }
}

/**
 * One photo in the editor: a thumbnail with a menu, draggable after a long press.
 *
 * @param sha the photo's SHA-256.
 * @param index its position.
 * @param count how many photos there are.
 * @param onSetMain makes it the main photo.
 * @param onRemove removes it.
 * @param onMove moves it to another position after a drag.
 */
@Composable
private fun EditablePhoto(sha: String, index: Int, count: Int, onSetMain: (String) -> Unit, onRemove: (String) -> Unit, onMove: (Int, Int) -> Unit) {
    var menu by remember { mutableStateOf(false) }
    var drag by remember { mutableFloatStateOf(0f) }
    val step = with(LocalDensity.current) { 104.dp.toPx() }
    Box(
        Modifier
            .offset { IntOffset(drag.roundToInt(), 0) }
            .pointerInput(index, count) {
                detectDragGesturesAfterLongPress(
                    onDrag = { change, amount ->
                        change.consume()
                        drag += amount.x
                    },
                    onDragEnd = {
                        val target = (index + (drag / step).roundToInt()).coerceIn(0, count - 1)
                        drag = 0f
                        if (target != index) onMove(index, target)
                    },
                    onDragCancel = { drag = 0f },
                )
            },
    ) {
        PhotoThumb(
            sha,
            96.dp,
            Modifier
                .then(if (index == 0) Modifier.border(3.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(16.dp)) else Modifier)
                .clickable { menu = true },
        )
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            if (index > 0) {
                DropdownMenuItem(text = { Text(stringResource(R.string.set_main)) }, onClick = {
                    menu = false
                    onSetMain(sha)
                }, modifier = Modifier.testTag("photo_main_$sha"))
            }
            DropdownMenuItem(text = { Text(stringResource(R.string.action_remove)) }, onClick = {
                menu = false
                onRemove(sha)
            }, modifier = Modifier.testTag("photo_remove_$sha"))
        }
    }
}

/**
 * A landscape carousel of an owner's photos, main first. Tapping a photo
 * opens the full-screen viewer.
 *
 * @param photos the photos' SHA-256s, main first.
 * @param onOpen called with the tapped photo's index.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PhotoCarousel(photos: List<String>, onOpen: (Int) -> Unit) {
    val store = appContainer().photoStore
    val pager = rememberPagerState { photos.size }
    Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(16.dp)).testTag("carousel")) {
        HorizontalPager(state = pager, modifier = Modifier.fillMaxSize()) { page ->
            AsyncImage(
                model = store.photoFile(photos[page]),
                contentDescription = stringResource(R.string.photo_n, page + 1, photos.size),
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().clickable { onOpen(page) }.testTag("carousel_${photos[page]}"),
            )
        }
    }
}

/**
 * Full-screen photos: swipe between them and pinch to zoom up to the stored
 * resolution. Opens with a zoom from the tapped photo.
 *
 * @param photos the photos' SHA-256s, main first.
 * @param start the index to open at.
 * @param onDismiss closes the viewer.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PhotoViewer(photos: List<String>, start: Int, onDismiss: () -> Unit) {
    val store = appContainer().photoStore
    val motion = LocalMotion.current
    val pager = rememberPagerState(initialPage = start) { photos.size }
    val visible = remember { MutableTransitionState(false).apply { targetState = true } }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        AnimatedVisibility(
            visibleState = visible,
            enter = scaleIn(motion.spec(motion.longMs), initialScale = 0.3f) + fadeIn(motion.spec(motion.longMs)),
        ) {
            Box(Modifier.fillMaxSize().background(Color.Black).testTag("photo_viewer")) {
                HorizontalPager(state = pager, modifier = Modifier.fillMaxSize()) { page ->
                    var scale by remember { mutableFloatStateOf(1f) }
                    val max = remember(page) { maxZoom(store.photoFile(photos[page])) }
                    val transform = rememberTransformableState { _, zoom, _, _ -> scale = (scale * zoom).coerceIn(1f, max) }
                    AsyncImage(
                        model = store.photoFile(photos[page]),
                        contentDescription = stringResource(R.string.photo_n, page + 1, photos.size),
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .transformable(transform)
                            .graphicsLayer { scaleX = scale; scaleY = scale }
                            .semantics { zoomLevel = scale }
                            .testTag("viewer_${photos[page]}"),
                    )
                }
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)) {
                    Text(stringResource(R.string.close), color = Color.White)
                }
            }
        }
    }
}

/**
 * @param file a stored photo.
 * @return how far it can be zoomed before pixels are enlarged: its long side over 1080, at least 2.
 */
private fun maxZoom(file: java.io.File): Float {
    val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
    android.graphics.BitmapFactory.decodeFile(file.path, bounds)
    return (maxOf(bounds.outWidth, bounds.outHeight) / 1080f).coerceAtLeast(2f)
}

