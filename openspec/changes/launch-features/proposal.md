# Proposal

## Why

The owner wants the launch version to be the app people keep: adding an item should be as quick as pointing the camera at it, and finding one as easy as photographing something like it. Four features planned for after 1.0 (docs/decisions/0026) move before it, with a design pass, higher test coverage and an app that is fully self-contained: offline from the first launch and usable on every Android 9+ phone, including phones without Google Play services.

## What Changes

- **Item recognition:** a photo of a new item (photo-first mode, or a new "Identify" button in the form) is read on the phone: image labels say what kind of thing it is and text recognition reads the packaging. The form is prefilled with a suggested name, categories, tags, description text and attribute fields, all editable and none saved until the user saves.
- **Attribute suggestions:** typing a name such as "Raspberry Pi 4 4GB" suggests fields (Brand: Raspberry Pi, RAM: 4 GB) from built-in rules in English and Portuguese, learning from what the user accepts, like category suggestions.
- **Photo search:** every photo gets on-device labels ("drill", "cable"), searchable as words; the Search tab can also take a photo and list items of the same kind.
- **Wide screens:** on tablets and landscape phones the app uses the space: a grid view of items and containers, and navigation suited to wide windows.
- **Self-contained scanning:** QR scanning moves from Google's code scanner (needs Play services and a one-time download) to a scanner bundled in the app with its own camera screen. **BREAKING (permission):** scanning now asks for the camera permission, once, when the user first scans; nothing else asks. Users who decline can still type a code into search.
- **Phones without Google Play services:** everything works except Google Drive sync, tips and rating, which explain that they need Google Play.
- **Design pass:** a project design skill adapted to Material 3 from the resources the owner chose, applied to every screen, with motion where it helps.
- **Quality:** line coverage of 90% counting UI code (today 80%, UI excluded); an APK size budget set after a trial build measures the bundled models.

## Capabilities

### New Capabilities
- `item-recognition`: identifying an item from a photo (image labels and packaging text) and prefilling the item form.

### Modified Capabilities
- `qr-codes`: scanning is bundled and works offline on every phone, with a camera permission asked on first scan.
- `app-shell`: offline operation now covers scanning and recognition; phones without Google Play services; wide-screen layouts and the grid view; touch targets and contrast.
- `search`: photo labels are searchable, and search can start from a photo.
- `custom-fields`: attribute suggestions from the item name.
- `items`: photo-first mode and the form offer recognition.
- `help-and-support`: the network list in "No tracking" no longer includes scanning; rating on phones without Google Play.

## Impact

- **New dependencies (approved by the owner on 2026-10-14):** ML Kit text recognition (bundled Latin model), ML Kit barcode scanning (bundled), ML Kit image labeling (bundled base model), CameraX for the scanner screen, and the Material 3 adaptive / window size class library. Google's code scanner (`play-services-code-scanner`) is removed.
- **Personal data (LGPD):** photos, labels and recognized text never leave the phone. Google's ML Kit sends Google usage metrics about itself (device and app details, timings, sizes; no content), which the owner chose to accept and disclose (2026-10-14). That and the new camera permission change PRIVACY.md, the in-app privacy text and the Play data safety form.
- **Database schema:** a new table of photo labels (version 7), derived on each device and never synced, so the sync format does not change.
- **Size and speed:** bundled models add several MB per phone type; the trial build sets the budget. Models load only when used, so cold start stays within the release gate (ADR 0028).
- **Release plan:** 1.0 moves after these features. The Play closed test starts now with 0.15.0, so the 14-day tester period runs while they are built.
- **Roadmap:** docs/product/roadmap.md, vision.md, ADR 0026 and the trecos-v1 non-goals are updated; house sharing and multiple users stay after 1.0.
