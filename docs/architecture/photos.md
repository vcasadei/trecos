# Photos

Design D8. Photos stay small, private and on the device (and in the user's
own Drive once sync arrives).

## Pipeline

| Step | How | Why |
|---|---|---|
| Source | The camera app writes to `cache/camera/` through a `FileProvider` URI; the Android photo picker returns content URIs | No camera or storage permission; nothing reaches the gallery |
| Read | The image's bytes are read through the content resolver | One code path for camera, picker and tests |
| Decode | `ImageDecoder` from a byte buffer, target size so the long side is at most 1920 px, software bitmap | EXIF orientation is applied during the decode |
| Encode | WebP, quality 80 (`WEBP_LOSSY` on Android 11+, `WEBP` below) | WebP output carries no metadata: location, camera and dates are gone |
| Name | SHA-256 of the WebP bytes; `files/photos/<sha>.webp` | Identical photos are stored once |
| Thumbnail | Centred square crop, 320 px, WebP q80; `files/thumbs/<sha>.webp` | Lists stay fast; regenerated when missing (never synced) |
| Original | Never kept; camera captures in `cache/camera/` are deleted after import | Privacy and space |

## Data

`photo` rows (schema 4): `id`, `houseId`, `ownerType` (item, container or
house), `ownerId`, `sha256`, `position` (0 is the main photo), `createdAt`.
Up to 3 per owner. Several rows may share a file.

| Operation | Photos |
|---|---|
| Form save | The form's photos replace the owner's rows; photos picked in a form that is cancelled are never written as rows |
| Move to another house | Rows follow their owners |
| Copy, Duplicate | Each copy gets its own rows, sharing the files |
| Trash | Rows stay while the owner is in the trash |
| Purge, Delete permanently, house deletion | Rows are deleted, then files no row refers to (older than one hour, so a form still open keeps its new photos) |

## UI

| Where | What |
|---|---|
| Forms (item, container, house) | Photos first: add (Camera or Gallery; the first time asks, with "Remember my choice" checked), "Set as main", remove, long-press drag to reorder; add is disabled at 3 |
| Item, container and house screens | Landscape (16:9) carousel, main photo first; the icon shows when there are no photos |
| Full-screen viewer | Swipe between photos, pinch to zoom up to the stored resolution; opens with a zoom from the tapped photo (250 ms, instant with "Remove animations") |
| Lists | The main photo's thumbnail instead of the icon, loaded with Coil |
| Photo-first mode | Adding an item opens the image source at once; the form then opens with the photo and the name focused; cancelling still opens the form |
