# Changelog

All notable changes to Trecos are recorded here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and versions follow
[Semantic Versioning](https://semver.org/).

## [Unreleased]

## [0.12.0] - Unreleased

### Added

- Optional database encryption, behind the `FEATURE_ENCRYPTION` build flag (off by default). It needs Google Drive sync: the random 256-bit key is protected by the Android Keystore on the phone and kept in Drive's hidden app folder for recovery.
- The database is re-encrypted with SQLCipher into a new file and swapped in when the app restarts; an interruption leaves the data unencrypted and intact.
- Drive data copies are encrypted with AES-GCM when encryption is on; photos stay unencrypted and still sync.
- A new phone signed in with the same account fetches the key, restores the latest synced data and encrypts itself.
- Turning encryption off decrypts the phone's data, removes the key from Drive and uploads later copies unencrypted.
- Disconnecting Google is refused while encryption is on, and exports warn that backup files aren't encrypted.

## [0.11.0] - Unreleased

### Added

- Google Drive sync, behind the `FEATURE_DRIVE_SYNC` build flag (off by default). The data lives in a visible Trecos folder in your own Drive, and the app has access only to files it created.
- Connecting offers to restore your houses on a new phone, or to merge with (or replace by) the Drive data on a phone that has its own.
- Three-way merge per field. Only the same detail changed on two devices, or an edit against a delete, asks you; the conflict screen offers Keep mine / Keep theirs, and Settings shows a badge while conflicts wait.
- Automatic sync every day (or every 5, 15 or 30 days, or never), only with a network, retried on failure, plus Sync now. Photos wait for Wi-Fi by default and are uploaded once each.
- Sync status with plain-language errors, the sync history (time, Google user, device, changes), and an editable device name.
- Detection of a deleted or altered Drive folder, with an offer to upload your data again, and a refusal to merge data from a newer app version.
- Import's "Replace everything" warns that, with sync on, other devices receive the data.

## [0.10.0] - Unreleased

### Added

- Export a backup from Settings > Sync & backup: one `trecos-backup-YYYY-MM-DD.zip` with all or chosen houses, every item, container, category, tag, custom field and full-size photo, saved with the system file picker. A failed save removes the incomplete file.
- Import a backup with a preview, then "Add as new houses" (new identities) or "Replace everything" after confirming. Imports are all or nothing; damaged backups and backups from newer versions change nothing.
- The snapshot format (JSON Lines, sorted, versioned) that sync will share.

### Fixed

- "More fields" could close again when tapped while an item's edit form was still loading.

## [0.9.0] - Unreleased

### Added

- App lock, off by default, using the phone's own fingerprint, face, PIN, pattern or password; turning it on needs a phone screen lock and a successful unlock.
- Lock timing: at start and after Immediately, 1 minute (default), 5 or 15 minutes in the background; while locked only an Unlock button shows, and the recent-apps preview hides content.
- The lock turns itself off, with a note, when the phone's screen lock is removed.
- An optional profile (name and e-mail) in Settings, editable and deletable, never written to logs.

## [0.8.0] - Unreleased

### Added

- Custom fields on items: Text, Number (with a unit label), Date and Yes/No, validated by type.
- House-wide fields in Settings (rename keeps values; delete says how many values go and needs confirming) and one-off fields added from an item's form.
- Custom values on the item screen and as detailed-view extras; Text values found by Search as part of the description; values kept when moving, copying or duplicating, and field definitions copied with a new house.
- The Settings tab: General (language, currency, start on), Appearance (theme, house colour band, list view, detailed-view extras), Items & photos (new item starts with, image source, tags), Custom fields, Trash and About. Settings apply at once and stay on the device.
- Changing the currency relabels prices after a warning that nothing is converted; Cancel keeps the old one.
- Up to three detailed-view extras from categories, tags, total value, brand, model, serial number, QR code, dates and custom fields.

### Changed

- Database schema 6: custom field definitions and values, indexed for search.

## [0.7.0] - Unreleased

### Added

- QR codes on items and containers, up to 256 characters: filled from the name when free, kept on rename, unique within a house, with a link to whatever already holds a typed code.
- A large label view with the code written underneath, Share as an image and Print through the system dialog.
- Printing the labels of a selection (or from a container's menu) in one job, 12 per page, skipping things without a code.
- Scanning with the Google code scanner, with no camera permission: from Search it opens the one match, asks which house, offers to create an item or container with the code and place it, or offers Restore for a trashed match; in forms it fills the code, and the name when empty.
- A message when the scanner can't be downloaded offline.

## [0.6.0] - Unreleased

### Added

- The Search tab: with nothing typed it lists every item, with a live count; the keyboard opens only when the field is tapped.
- Accent- and case-insensitive matching on word beginnings, all words required, updating as you type; typed text is never stored.
- Scope (Items, Containers, Both) and match (Name and description, Name, Description); Name covers brand, model, serial number and QR code.
- Filters by house, category (a group includes its subcategories) and tag: any within a filter, all across filters, kept until "Clear all", even across restarts.
- Sorting by name, date added or unit price, reversible, with unpriced items last.
- Result paths start with a pill in the house's colour.
- "Search in this container" with a removable chip.

### Changed

- Database schema 5: a full-text index kept up to date by triggers.

## [0.5.0] - Unreleased

### Added

- Up to 3 photos per item, container and house, from the camera app or the photo picker, with no camera or storage permission.
- First-use Camera or Gallery prompt with "Remember my choice".
- Photos stored at up to 1920 px as WebP without any metadata (no location), named by SHA-256; 320 px thumbnails, regenerated when missing.
- Main photo, "Set as main", remove and drag to reorder.
- Landscape carousels on item, container and house screens, and a full-screen viewer with swipe and pinch zoom.
- Main photo thumbnails in every list.
- Photo-first add mode.
- Copies and duplicates keep their photos; purged things free their photo files.

### Changed

- Database schema 4 (photos).

## [0.4.0] - Unreleased

### Added

- Move items and containers anywhere, including another house, with a destination picker (browse, search, switch house); a container can't go inside itself.
- Moving between houses carries custom categories and tags along and asks what to do when a QR code is already used there.
- Copy (new identity, no QR code, containers with their contents) and Duplicate (" (copy)", opens the edit form).
- Multi-select by long press: Select all, Move, Copy, Delete; back clears the selection.
- Delete confirmation, Undo, and a per-house trash kept for 30 days with Restore (to the original place or a chosen one), Delete permanently and Empty trash; a daily job purges old trash.
- "Choose what to keep" when deleting a container with contents, with several destinations and Finish.
- Permanent house deletion after typing the house's name; never the last house.

### Changed

- Database schema 3 (trash). QR codes are unique among things not in the trash: a new item can reuse a trashed item's code, which is then dropped if that item is restored.

## [0.3.0] - Unreleased

### Added

- Built-in categories: 20 groups and 118 subcategories with icons, in English and Portuguese; they can't be renamed or deleted.
- Several categories per item, in order; the first is the main one and its icon shows in lists. The detailed view lists them.
- Category picker with search, "Set as main", and custom categories per house (under a group or as a new group, with an icon).
- Deleting a custom category removes it from every item; items keep their other categories.
- Offline category suggestions from the name and description (English and Portuguese keywords, plus words learned in the house), as chips added only when tapped.
- Tags per house, offered as you type, the same tag whatever the case or accents; rename and delete apply to every item.
- New houses can copy another house's custom categories and tags.
- "Save + new" keeps the categories.

### Changed

- Database schema 2, with a tested migration and a safety copy before any migration.

## [0.2.0] - Unreleased

First release meant for testers, as a sideloadable APK.

### Added

- Houses: a first-run prompt ("My home"), any number of houses with address, description, icon and colour; at least one house always exists.
- Home tab opens the last-used house; the house name switches houses or adds one.
- Containers nested to any depth, with description, QR code text, icon, colour and an optional manual value.
- Items with name, quantity, unit price, brand, model, serial number, QR code and description; total value and the dates added and last changed.
- Item form with "More fields" and "Save + new", which keeps the location.
- Container screen: header that scrolls away, "Containers (n)" and "Items (n)", an empty-state hint, and the "+" button with Item and Container.
- Location path with tappable levels, collapsing to "… > last two" on small screens.
- Container values: recursive totals, manual overrides that count upward, "Clear override", and a count of unpriced items.
- Colours: tinted container headers, row stripes, inheritance, a house band behind the status bar with two or more houses, and a house pill in paths.
- Icons for houses and containers without photos.
- Condensed and detailed list views, remembered app-wide.
- Prices formatted in the app language with the display currency.
- Database on SQLCipher (no key until encryption arrives in 0.12).

### Changed

- Phones in any Portuguese variant get Portuguese (Brazil).
- The bottom bar has a neon outline instead of a shadow.

## [0.1.0] - 2026-09-29

Foundation release. Not distributed: the first sideloadable APK is 0.2.

### Added

- Android project (`app.trecos`): Kotlin, Jetpack Compose, minimum Android 9, target Android 16.
- White (off-white) and Dark (pure black) themes with a Follow system option; no wallpaper colours.
- A 12-colour pastel palette with White, Dark and band variants, all contrast-tested at 4.5:1 or more.
- English and Portuguese (Brazil), following the device language, with an in-app switch that works on Android 9 and later.
- Floating bottom bar (Search, Home, Settings) with a raised circle that slides between tabs.
- Navigation with Home as the start: back from Search or Settings returns Home; back on Home leaves the app.
- Text that truncates with "…" and a location breadcrumb that collapses to its last two levels.
- Motion tokens (150-250 ms) that become instant with Android's "Remove animations".
- Baseline Profile generation and a cold-start benchmark.
- Release builds strip debug logs; Android backup excludes the database and key files.
- License (PolyForm Noncommercial 1.0.0), CLA, contribution, security and privacy documents, and the documentation tree.
- CI: build, lint, unit and screenshot tests, coverage threshold, release log check, secret scan.
