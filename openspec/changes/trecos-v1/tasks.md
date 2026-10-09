# Tasks

Documentation language follows the user's decision (#17), which overrides the
PT-BR default: user docs under `docs/user/` are written in both English and
Portuguese (Brazil); every other document is in English. Each release group
ends with its own docs and `CHANGELOG.md` entry. The Quality Gates (group 15)
run at the end of **every** release group before it is tagged.

## 1. Release 0.1 — Foundation

- [x] 1.1 Rename the GitHub repository `cubby` to `trecos` (`gh repo rename trecos`) and point the remote at it (`git remote set-url origin git@github.com:vcasadei/trecos.git`); verify with `gh repo view vcasadei/trecos` and `git push`
- [x] 1.2 Install JDK 17, Android SDK command-line tools, platform and build tools on the headless machine; verify with `java -version` and `sdkmanager --list_installed`
- [x] 1.3 Write `docs/dev/setup-headless-linux.md` (JDK, SDK, `kvm` group, emulator without a window); verify by following it in a fresh shell
- [x] 1.4 Create the Gradle project: `app` module (`app.trecos`, minSdk 28, current Play target SDK), `baselineprofile` module, a version catalog with only the approved dependencies, R8 full mode, and Android ignores in `.gitignore` outside the `openspec-casadei: secrets` block (`build/`, `.gradle/`, `.idea/`, `local.properties`, `keystore.properties`); verify with `./gradlew assembleDebug` and that `git status` shows no build output or `local.properties`
- [x] 1.5 Add `LICENSE` (PolyForm Noncommercial 1.0.0, plus a note that third-party components keep their own licenses), `COMMERCIAL.md`, `CLA.md`, `CONTRIBUTING.md`, `CODE_OF_CONDUCT.md`, `SECURITY.md`, a `PRIVACY.md` draft and `CHANGELOG.md`; verify that each is linked from the README
- [x] 1.6 Create the docs tree (`docs/product` vision/glossary/roadmap, `docs/architecture/overview.md`, `docs/decisions/` with one ADR per explore decision, `docs/dev/`, `docs/user/en` and `docs/user/pt-BR`), move `explore.md` to `docs/product/original-draft.md`, and rewrite the README as Trecos; verify with `grep -ri cubby --exclude-dir=.git` that no document calls the product "Cubby"; the original draft and historical references to the rename (the case study, the proposal, task 1.1 and ADR 0016) keep the old name, because the rename is part of the project's record: "Cubby" was dropped for being crowded, including a near-identical iOS "Cubby - Home Inventory" app (ADR 0016)
- [x] 1.7 Add a GitHub Actions CI workflow (build, lint, unit tests, screenshot verification) on push and pull request; verify a green run on `project-setup`
- [x] 1.8 Implement theme tokens (Follow system, White off-white, Dark pure black, no dynamic colour) and the 12-colour pastel palette with light, dark and band variants; verify with screenshot tests of both themes and a contrast test asserting at least 4.5:1 for text on every palette tint
- [x] 1.9 Set up English and Portuguese (Brazil) string resources and the in-app language switch; tests for scenarios "First launch on a Portuguese phone", "Unsupported device language" and "Changing language in the app"
- [x] 1.10 Build the custom bottom bar (Search | Home | Settings, text-only unselected tabs, sliding raised circle); tests for scenarios "Switching tabs" and "Reduced motion", plus screenshots of each tab in both themes
- [x] 1.11 Wire navigation with a back arrow on non-root screens; test for scenario "Back from a tab root"
- [x] 1.12 Build the shared text components (line limits, ellipsis, long-word wrapping) and the breadcrumb component with middle collapse, plus stress fixtures (200-character names, 40-character serials, 8-level paths, Portuguese strings); tests for scenarios "Very long name" and "Long unbroken serial number", and a component test for "… > Box A > Cables bag"
- [x] 1.13 Define motion tokens (150–250 ms, instant with "Remove animations"); verify with a test that transitions are instant when the animator scale is 0
- [x] 1.14 Set up Macrobenchmark and Baseline Profile generation; verify that `./gradlew :app:generateBaselineProfile` produces a profile on the KVM emulator (the current Baseline Profile plugin registers the task on the app module, not on `:baselineprofile`), and document it in `docs/dev/testing.md`
- [x] 1.15 Configure the release build to strip debug and verbose logs, and disable Auto Backup for the database and key files (`dataExtractionRules`); verify with a unit test on the merged manifest and a check that release bytecode has no `Log.d` calls
- [x] 1.16 Set a coverage threshold with AGP's built-in JaCoCo (80% lines for non-UI packages) and a verification task; verify that `./gradlew jacocoCoverageVerification` passes on the empty codebase
- [x] 1.17 Write `docs/architecture/overview.md`, `docs/dev/build.md` and `docs/dev/testing.md`, and add the 0.1 entry to `CHANGELOG.md`; verify that every command in them runs as written

## 2. Release 0.2 — Places and items

- [x] 2.1 Create the Room database on the SQLCipher engine (no key) with `House`, `Container` and `Item` (UUID ids, `houseId`, created/updated/deleted timestamps, a per-house unique `qrCode` index, money as minor units), and export schema v1; verify with DAO unit tests and the committed schema JSON
- [x] 2.2 Add `AppContainer` wiring (database, stores) and ViewModel factories; verify that the app starts in a Robolectric smoke test
- [x] 2.3 Implement the first-run house prompt ("My home" pre-filled); test for scenario "First run"
- [x] 2.4 Implement house create/edit (name required, address, description, icon from the place set, colour); tests for scenarios "Creating a second house", "House without a name" and "Deleting the last house"
- [x] 2.5 Implement the Home tab entry point (single house opens directly, house list at 2 or more, last-used house, switcher with "Add house"); tests for scenarios "Single house" and "Several houses"
- [x] 2.6 Implement nested container create/edit (name required, description, QR text, icon, colour); tests for scenarios "Box inside a box" and "Container without a name"
- [x] 2.7 Build the container screen (fixed top bar, scrolling header, Containers and Items sections with counts, empty-state hint, overflow menu); tests for scenarios "Container with contents" and "Empty container", plus screenshots in both themes
- [x] 2.8 Build the "+" speed dial with Item and Container options floating to its left; tests for scenarios "Adding from a container" and "Dismissing the options"
- [x] 2.9 Implement item fields and validation (name required, quantity a whole number from 0 to 999,999 with default 1, unit price of 0 or more, brand, model, serial, QR, description); tests for scenarios "Minimal item", "Invalid quantity" and "Missing name"
- [x] 2.10 Implement the total value (quantity × unit price, none without a price) and added/changed dates; tests for scenarios "Screws" and "Editing updates the date"
- [x] 2.11 Build the item form (essentials, "More fields", Save, Save + new keeping the location); verify with a UI test that Save + new keeps the container
- [x] 2.12 Build the item screen, with price formatting by app language and currency symbol; tests for scenarios "Price formatting in Portuguese" and "Price formatting in English"
- [x] 2.13 Show the location path on item and container screens, with tappable levels and the collapse rule; tests for scenarios "Jumping up the hierarchy", "Deep path on a small screen" and "Leaving a container"
- [x] 2.14 Implement the container value fold (recursive sum, override counting upward, auto/manual label, clear override, unpriced count); unit tests for scenarios "Override counts upward" and "Unpriced items"
- [x] 2.15 Implement colours (header tint, row stripe, inheritance) and the house indicator (status bar band with Automatic/Always/Never, house pill); tests for scenarios "Inherited colour", "Own colour wins", "Two houses" and "Single house"
- [x] 2.16 Show icons for places without photos; test for scenario "Container without photos"
- [x] 2.17 Implement condensed and detailed list views with an app-wide persisted toggle, hiding empty fields; tests for scenarios "Switching to detailed view" and "Item without price"
- [x] 2.18 Test scenario "Using the app in airplane mode" for everything shipped so far
- [ ] 2.19 Install SOPS and age, generate the age identity at `~/.config/sops/age/keys.txt` (mode 600), and back it up offline (printed and on a USB drive), never next to the encrypted file; verify with `sops --version`, `age --version` and a decrypt using only the backup copy
- [ ] 2.20 Generate the release keystore (PKCS12, RSA 4096, 30-year validity) and random passwords in `/dev/shm`, encrypt them into `release-signing.sops.yaml` (`keystore_base64`, `store_password`, `key_alias`, `key_password`), commit it with `.sops.yaml` to the private repository `vcasadei/trecos-signing`, copy it to the Google Drive folder, then shred the plaintext; verify that `sops -d` works from both copies and that no plaintext keystore is left (`find / -xdev -name '*.p12' -o -name '*.jks' 2>/dev/null`)
- [ ] 2.21 Set the repository secrets `TRECOS_KEYSTORE_BASE64`, `TRECOS_KEYSTORE_PASSWORD`, `TRECOS_KEY_ALIAS` and `TRECOS_KEY_PASSWORD` from the decrypted file through stdin (`sops -d --extract '["store_password"]' release-signing.sops.yaml | gh secret set TRECOS_KEYSTORE_PASSWORD`), make the release `signingConfig` read only those environment variables, and document them with placeholder values in `docs/dev/release.md`; verify with `gh secret list` and that `git check-ignore -v keystore.properties release.p12` reports both ignored
- [ ] 2.22 Make `vcasadei/trecos` public (`gh repo edit vcasadei/trecos --visibility public --accept-visibility-change-consequences`) after a full-history gitleaks scan passes; turn on secret scanning and push protection; move the four secrets into a `release` Environment limited to `v*` tags with the developer as required reviewer, and delete the repository-level copies; verify with `gh api repos/vcasadei/trecos --jq .security_and_analysis` and `gh secret list --env release`
- [ ] 2.23 Add the release workflow (on a `v*` tag: decode the keystore into `$RUNNER_TEMP`, build per-ABI and universal APKs, `apksigner verify --print-certs`, publish to GitHub Releases with `SHA256SUMS` and the certificate fingerprint in the notes) and release 0.2; verify that the published APK installs on the emulator with `adb install`, that its certificate fingerprint matches the notes, and that `sha256sum -c SHA256SUMS` passes
- [x] 2.24 Write `docs/architecture/data-model.md`, `docs/user/{en,pt-BR}/getting-started.md` (including how to sideload and check the fingerprint), and the key-recovery procedure in `docs/dev/release.md` (restore the age key from its backup, decrypt from either copy), and add the 0.2 entry to `CHANGELOG.md`
- [ ] 2.25 Tune the bottom bar's neon outline with the user: try colour variations, line width, glow width and intensity for each theme, and whether the Dark disc should match the glow; show each option as screenshot renders, apply the chosen one, and update the baselines and `docs/design/visual-review.md`
- [ ] 2.26 Limit Dependabot to what ships: set `DEPENDENCY_GRAPH_INCLUDE_CONFIGURATIONS: .*RuntimeClasspath` in `.github/workflows/dependency-graph.yml`, turn off GitHub's automatic dependency submission (through the API, or by the user in Settings > Advanced Security if the API refuses), and dismiss the build-tool-only alerts (Bouncy Castle, jdom2, jose4j, httpclient, wire, commons-lang3, all from the Gradle plugin classpath) as not shipped; verify that the next Dependabot run on `master` succeeds and that `./gradlew :app:dependencies --configuration releaseRuntimeClasspath` matches the submitted graph

## 3. Database migrations

- [x] 3.1 Implement the pre-migration safety copy (`pre-migration-v<N>.db` before any migration, restored automatically if the migration fails, deleted after the next successful launch); verify with a test that a deliberately failing migration leaves the database readable at the previous version
- [x] 3.2 Set up the migration test harness (`MigrationTestHelper` over exported schemas, run in CI); verify with a sample v1 → v2 test
- [x] 3.3 Document the expand-and-contract rule and the per-release migration checklist in `docs/architecture/data-model.md`
- [x] 3.4 Run the down path: restore the pre-migration copy in a test and verify that the prior schema version and row counts are back

## 4. Release 0.3 — Categories and tags

- [x] 4.1 Migration v1 → v2 (expand): add `Category`, `ItemCategory` (ordered), `Tag`, `ItemTag` and `TokenCategoryCount`; verify with a migration test that preserves existing items
- [x] 4.2 Seed the built-in category tree from a bundled asset (stable keys, icons, English and Portuguese names), matching the full list in the spec; tests that every listed category exists, that built-ins cannot be renamed or deleted, and for scenario "Built-ins in Portuguese"
- [x] 4.3 Build the category picker (search box, several categories per item, order, set main); tests for scenarios "Cable with two ends" and "Changing the main category"
- [x] 4.4 Implement per-house custom categories (top level or sub, icon picker with empty default, delete removes assignments); tests for scenarios "New subcategory", "Deleting a used custom category" and "Other houses don't see it"
- [x] 4.5 Implement category suggestions (keyword dictionary asset in English and Portuguese, normalisation, learned counts, top 3 chips, never automatic); unit tests for scenarios "Keyword match", "Learning my vocabulary" and "Nothing recognised"
- [x] 4.6 Implement tags (per house, completion, case- and accent-insensitive dedupe, rename and delete everywhere); tests for scenarios "Reusing a tag" and "Same tag, different case"
- [x] 4.7 Offer to copy custom categories and tags when creating a house; test for scenario "Copying from another house" (field definitions are added in 7.6)
- [x] 4.8 Show categories with suggestion chips on the item form, and make Save + new keep categories; test for scenario "Cataloguing a box of cables"
- [x] 4.9 Write `docs/user/{en,pt-BR}/categories-and-tags.md` and add the 0.3 entry to `CHANGELOG.md`

## 5. Release 0.4 — Organize and trash

- [x] 5.1 Build the destination picker (browse and search, starting in the current house, other houses) and Move, blocking moves into a container's own subtree; tests for scenarios "Moving an item" and "Moving a container into its own child"
- [x] 5.2 Implement moving between houses (creating missing categories and tags, and edit/remove/cancel on a QR clash); tests for scenarios "Carrying custom categories along" and "QR code clash"
- [x] 5.3 Implement Copy (new identities, no QR, containers with their contents) and Duplicate (" (copy)" suffix, opens the edit form); tests for scenarios "Copying a labelled box" and "Duplicating an item"
- [x] 5.4 Implement multi-select (long press, count, Select all, bulk Move, Copy and Delete, back clears); tests for scenarios "Moving several items" and "Leaving selection"
- [x] 5.5 Migration v2 → v3 (expand): add `TrashEntry`; verify with a migration test
- [x] 5.6 Implement delete confirmation, Undo for at least 5 s, and the per-house trash screen (from the house menu and Settings), hiding trashed things from lists and values; tests for scenarios "Deleting an item", "Cancelling", "Undoing a delete" and "Trashed items hidden"
- [x] 5.7 Implement restore (original location or a picker, QR removed when taken, with a notice); tests for scenarios "Normal restore" and "Original location gone"
- [x] 5.8 Add the daily purge job (older than 30 days, including photo files later); test for scenario "Automatic purge" with a fake clock
- [x] 5.9 Build the container delete flow ("Move everything to trash" or "Choose what to keep", checkboxes, drilling into sub-containers, several destinations including a new container at the parent level, Finish with confirmation); tests for scenarios "Keeping some things" and "Leaving the keep screen early"
- [x] 5.10 Implement house deletion (type the name, permanent, suggest export, never the last house); tests for scenarios "Deleting a house" and "Wrong name typed"
- [x] 5.11 Update the user docs on moving and deleting and draft FAQ answers 3 and 6 (both languages), and add the 0.4 entry to `CHANGELOG.md`

## 6. Release 0.5 — Photos

- [ ] 6.1 Migration v3 → v4 (expand): add `Photo` (owner, sha256, position); verify with a migration test
- [ ] 6.2 Implement the photo store (decode to at most 1920 px with orientation corrected, WebP q80 without metadata, sha256 file names, 320 px square thumbnail, original discarded); tests for scenarios "Photo from a 12-megapixel camera" (no GPS in the output) and "Unreadable file"
- [ ] 6.3 Integrate the camera app through `FileProvider` (never saving into the gallery) and the Photo Picker (multi-select up to the remaining slots), with no permissions requested; tests for scenarios "Picking from the gallery" and "Limit reached", plus a manifest test that no camera or storage permission is declared
- [ ] 6.4 Implement the first-use image source prompt ("Remember my choice" checked by default); tests for scenarios "Remembering" and "Keep asking"
- [ ] 6.5 Implement main photo, drag reorder, "Set as main" and remove; test for scenario "Setting the main photo"
- [ ] 6.6 Build the landscape carousels and the full-screen viewer (pinch zoom, swipe, zoom-from-thumbnail transition); tests for scenarios "Zooming into a serial label" and "Opening a photo"
- [ ] 6.7 Load thumbnails in every list with Coil, regenerating missing ones; test for scenario "Thumbnail regenerated"
- [ ] 6.8 Implement photo-first add mode; tests for scenarios "Photo first" and "Photo cancelled"
- [ ] 6.9 Include photos in Copy and Duplicate, and delete photo files on purge; tests that a copied item has its own photo rows and that a purge removes orphan files
- [ ] 6.10 Write `docs/architecture/photos.md` and update the user docs, and add the 0.5 entry to `CHANGELOG.md`

## 7. Release 0.6 — Search

- [ ] 7.1 Migration v4 → v5 (expand): add the FTS4 table (`unicode61`, `remove_diacritics=1`) with sync triggers and backfill; verify with a migration test that existing items are searchable
- [ ] 7.2 Build the Search tab (field, QR button, scope and match controls, filters, sort, live count, no automatic keyboard, empty-state message); tests for scenarios "Browsing everything" and "No results"
- [ ] 7.3 Implement matching (case and accent insensitive, word prefixes, all words required, debounced, no history stored); tests for scenarios "Accent-insensitive" and "Partial words"
- [ ] 7.4 Implement scope and match fields (Name also covers brand, model, serial and QR); tests for scenarios "Finding by serial number" and "Containers only"
- [ ] 7.5 Implement filters (house, categories including subcategories, tags; any within a filter, all across filters; persisted until "Clear all"); tests for scenarios "Any within, all across" and "Top-level includes subcategories"
- [ ] 7.6 Implement sorting (name, date added, unit price, direction toggle, unpriced items last); test for scenario "Most expensive first"
- [ ] 7.7 Show result paths starting with the house pill, collapsed between the pill and the last level; test for scenario "Same box name in two houses"
- [ ] 7.8 Implement "Search in this container" with a removable chip; test for scenario "Searching inside a box"
- [ ] 7.9 Write the search user docs (both languages), and add the 0.6 entry to `CHANGELOG.md`

## 8. Release 0.7 — QR codes

- [ ] 8.1 Implement QR identity rules (at most 256 characters, filled from the name only if free, unchanged on rename, per-house uniqueness error linking to the holder); tests for scenarios "Code follows the name at creation", "Rename keeps the label valid", "Name already used as a code" and "Entering a taken code"
- [ ] 8.2 Render labels with ZXing (large view with the text underneath, Share as an image); verify with a test that decodes the rendered image back to the same text
- [ ] 8.3 Implement printing single and bulk labels through `PrintHelper`; tests for scenarios "Printing one label", "Printing several labels" and "Nothing to print"
- [ ] 8.4 Integrate the Google code scanner (QR only, trimmed, exact case-sensitive text, a message when the scanner module can't be downloaded offline); tests with a fake scanner for scenarios "Label from another program" and "Scanning offline"
- [ ] 8.5 Implement lookup after scanning (one match, several houses, unknown code with create-and-place, trashed code); tests for scenarios "One match", "Several houses", "Unknown code" and "Scanning a trashed box"
- [ ] 8.6 Add scanning to the item form and to Search; tests for scenarios "Scanning into an empty form", "Scanning when a name exists" and "Cancelling a scan"
- [ ] 8.7 Register the Google Play developer account (US$25, the project's only cost) and create the app `app.trecos`; enrol in Play App Signing by uploading the existing release key with PEPK (not a Play-generated key), create the upload key the same way as 2.20 and store it as `upload_*` fields in `release-signing.sops.yaml` and as the repository secrets `TRECOS_UPLOAD_KEYSTORE_BASE64`, `TRECOS_UPLOAD_KEYSTORE_PASSWORD`, `TRECOS_UPLOAD_KEY_ALIAS` and `TRECOS_UPLOAD_KEY_PASSWORD`, and make the release workflow also build the App Bundle; verify that a sideloaded 0.6 install updates to the Play build without reinstalling
- [ ] 8.8 Check Play's current closed-testing requirement, recruit testers and move to the closed track; record the process in `docs/dev/release.md`
- [ ] 8.9 Write the user docs and FAQ answers 2 and 4 on QR labels (both languages), and add the 0.7 entry to `CHANGELOG.md`

## 9. Release 0.8 — Custom fields and settings

- [ ] 9.1 Migration v5 → v6 (expand): add `FieldDef` (house-wide or per item) and `FieldValue`; verify with a migration test
- [ ] 9.2 Implement field types (Text, Number with unit label, Date, Yes/No) and validation; tests for scenarios "Number with unit" and "Wrong type"
- [ ] 9.3 Implement house-wide field definitions in Settings (rename keeps values, delete confirms the count); tests for scenarios "Adding a house-wide field" and "Deleting a used field"
- [ ] 9.4 Implement per-item fields; test for scenario "One-off field"
- [ ] 9.5 Index custom text values as part of the description, and offer custom fields as detailed-view extras; test for scenario "Searching a custom value"
- [ ] 9.6 Include field definitions in "copy from another house"; extend the test for scenario "Copying from another house"
- [ ] 9.7 Build the Settings structure (sections in order) with DataStore defaults applied immediately and kept on the device only; tests for scenarios "Fresh install" and "Immediate effect"
- [ ] 9.8 Implement the currency change warning and relabelling; tests for scenarios "Relabelling" and "Cancelling"
- [ ] 9.9 Implement the detailed-view extras picker (limit of 3), start screen, form-or-photo-first setting and image source setting; tests for scenarios "Picking extras" and "Too many"
- [ ] 9.10 Write the user docs on custom fields and settings (both languages), and add the 0.8 entry to `CHANGELOG.md`

## 10. Release 0.9 — App lock and profile

- [ ] 10.1 Implement the app lock with the phone's biometrics or credential, requiring a screen lock to enable; tests for scenarios "Turning it on" and "Phone without a screen lock"
- [ ] 10.2 Implement lock timing (Immediately, 1 minute default, 5 or 15 minutes), the locked screen, and hiding content from recent apps; tests for scenarios "Returning quickly", "Failed unlock" and "Recent-apps preview"
- [ ] 10.3 Turn the lock off automatically when the phone's screen lock is removed; test for scenario "User removed their phone PIN"
- [ ] 10.4 Implement the optional profile (name and e-mail, editable, deletable) with a log-redaction check that no e-mail or name reaches logs; tests for scenarios "No profile" and "Deleting the profile"
- [ ] 10.5 Update `SECURITY.md`, write FAQ answers 7 and 9 (both languages), and add the 0.9 entry to `CHANGELOG.md`

## 11. Release 0.10 — Local backup

- [ ] 11.1 Implement the JSON Lines snapshot serializer shared with sync (every entity, sorted output, `formatVersion`); verify with round-trip unit tests for every entity type
- [ ] 11.2 Implement export (all or selected houses, `trecos-backup-YYYY-MM-DD.zip`, full photos, system file picker, cleanup on failure); tests for scenarios "Exporting to Downloads" and "Not enough space"
- [ ] 11.3 Implement import (preview, Replace with confirmation, Add with new ids, all or nothing, newer format refused); tests for scenarios "Adding houses from another phone", "Corrupt file" and "Backup from a newer app version"
- [ ] 11.4 Write the backup user docs and FAQ answer 5 (backup part, both languages), and add the 0.10 entry to `CHANGELOG.md`

## 12. Release 0.11 — Google Drive sync (behind `FEATURE_DRIVE_SYNC`)

- [ ] 12.1 Add the `FEATURE_DRIVE_SYNC` build flag (default false) and hide every sync entry point behind it; verify with a test that Settings shows no Sync section when it's off
- [ ] 12.2 Create the Google Cloud OAuth client for `app.trecos` (debug and release signing certificates, `drive.file` and `drive.appdata` scopes, consent screen), with no secret in the repository; document it in `docs/dev/release.md` and verify sign-in on a debug build
- [ ] 12.3 Implement connect and disconnect with the Google authorization client, creating the visible "Trecos" folder; tests for scenarios "First connection" and "Sign-in cancelled"
- [ ] 12.4 Implement the Drive REST client over `HttpsURLConnection` (list, create, upload, download, retry with backoff); tests against a local JDK HTTP server, including error and retry cases
- [ ] 12.5 Implement the merge engine in plain Kotlin (three-way per field, same-field and edit-versus-delete conflicts, folding several devices, trash state); exhaustive unit tests including scenarios "No conflict", "Same field" and "Edit versus delete"
- [ ] 12.6 Implement commits, refs and content-addressed objects (idempotent, no commit when nothing changed, each photo uploaded once, garbage collection keeping 30 commits per device); tests for scenarios "Interrupted sync" (fault injection at each step) and "Nothing changed"
- [ ] 12.7 Build the conflict screen (both values, Keep mine / Keep theirs, local value kept until resolved, Settings badge); UI tests for resolving each conflict type
- [ ] 12.8 Implement scheduling (every day default, 5, 15, 30 days or never, network required, photos only on Wi-Fi by default, expedited Sync now); tests with the WorkManager test helpers for scenarios "Daily sync" and "Mobile data"
- [ ] 12.9 Build the sync status and history screens (last success, errors in plain language, retries, entries with time, user, device name and summary, editable device name, app-generated device id); tests for scenarios "Drive unreachable" and "Viewing history"
- [ ] 12.10 Implement restore onto a device (offer restore, merge or keep Drive data), the format-version refusal, and detection of a deleted or altered folder; tests for scenarios "New phone", "Old app, new data" and "Folder deleted"
- [ ] 12.11 Add the replace-with-sync warning to import; test for scenario "Replacing with sync on"
- [ ] 12.12 Write `docs/architecture/sync-protocol.md`, update `PRIVACY.md` (Drive data, 30-sync retention), write FAQ answers 5 (sync part) and 10 (both languages), and add the 0.11 entry to `CHANGELOG.md`

## 13. Release 0.12 — Encryption (behind `FEATURE_ENCRYPTION`)

- [ ] 13.1 Add the `FEATURE_ENCRYPTION` build flag (default false); verify with a test that the setting is hidden when it's off
- [ ] 13.2 Implement key generation, Keystore wrapping and upload to `appDataFolder`, requiring a connected account; tests for scenarios "Turning encryption on" and "No Google account", plus a test that the key never appears in logs or exports
- [ ] 13.3 Implement re-encryption with `sqlcipher_export` and an atomic swap that survives interruption; test for scenario "Interrupted encryption", with fault injection before and after the swap
- [ ] 13.4 Encrypt Drive snapshots with AES-GCM when on, leaving photos unencrypted; test for scenario "Drive copy"
- [ ] 13.5 Implement recovery on a new phone; test for scenario "Lost phone" with two emulated installs sharing a fake Drive
- [ ] 13.6 Implement turning encryption off and block disconnecting Google while it's on; tests for scenarios "Disabling" and "Disconnecting with encryption on"
- [ ] 13.7 Add the unencrypted-export warning; test for scenario "Export with encryption on"
- [ ] 13.8 Write `docs/architecture/encryption.md`, update `SECURITY.md` and FAQ answer 8 (both languages), and add the 0.12 entry to `CHANGELOG.md`

## 14. Release 1.0 — Launch

- [ ] 14.1 Build the FAQ accordion with the 10 questions from `docs/user/{en,pt-BR}/faq.md`; test for scenario "Reading an answer"
- [ ] 14.2 Implement "Contact us" (mailto hello@trecos.app, subject types, version details only, copy fallback); tests for scenarios "Asking for a quote" and "No e-mail app"
- [ ] 14.3 Implement "Rate Trecos" and the single in-app review prompt (14 days, 20 or more items, 5 or more sessions, never during a flow, once ever); tests for scenarios "The single prompt" and "Too early", with a fake clock
- [ ] 14.4 Implement the tip jar with Play Billing (three repeatable consumables, thank-you, unlocks nothing, no external payment links); tests for scenarios "Tipping", "Purchase cancelled" and "Google Play unavailable"
- [ ] 14.5 Build the About screen (version, license note, source, website, privacy policy, contact, rate, open-source licenses through AboutLibraries); test for scenario "Viewing licenses", checking that every shipped library is listed
- [ ] 14.6 Add a CI dependency allowlist check that fails on any advertising, analytics or crash-reporting SDK; test for scenario "Offline user" by asserting no network calls without sync
- [ ] 14.7 Remove the `FEATURE_DRIVE_SYNC` and `FEATURE_ENCRYPTION` flags once their removal criteria (design D19) are met; verify that the flags no longer exist in the build and that the related tests still pass
- [ ] 14.8 Run the performance pass on a 2 GB Android 9 reference phone (Baseline Profile, a median cold start of 1.5 s or less, janky frames under 5% on 1,000 items) and gate the release on it; tests for scenarios "Cold start benchmark" and "Regression blocks a release"
- [ ] 14.9 Publish the site at trecos.app on GitHub Pages (enforced HTTPS, privacy policy, commercial-license page, links to external donations) and set up forwarding of hello@trecos.app on Porkbun together with the user; verify the site loads over HTTPS and a test e-mail arrives
- [ ] 14.10 Prepare the Play listing ("Trecos: Home Inventory", English and Portuguese texts and screenshots, data safety form, privacy policy URL) and publish 1.0 to production; verify that the listing is live
- [ ] 14.11 Complete the user guide in both languages, update `docs/product/roadmap.md` (1.1 OCR to multi-user), and add the 1.0 entry to `CHANGELOG.md`

## 15. Quality Gates

- [ ] 15.1 Build compiles cleanly: `./gradlew assembleRelease`
- [ ] 15.2 Linter and static analysis pass with no errors: `./gradlew lintRelease`
- [ ] 15.3 Strict type checking passes, with Kotlin warnings treated as errors: `./gradlew compileReleaseKotlin`
- [ ] 15.4 Unit, Robolectric and migration tests pass: `./gradlew testDebugUnitTest`
- [ ] 15.5 Screenshot tests match the recorded baselines: `./gradlew verifyRoborazziDebug`
- [ ] 15.6 The coverage threshold is maintained or raised: `./gradlew jacocoCoverageVerification`
- [ ] 15.7 OpenSpec artifacts remain valid: `openspec validate trecos-v1 --strict`
- [ ] 15.8 No secrets in the branch: `pre-commit run gitleaks --all-files`
