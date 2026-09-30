# Changelog

All notable changes to Trecos are recorded here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and versions follow
[Semantic Versioning](https://semver.org/).

## [Unreleased]

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
