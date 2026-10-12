# Scenarios outside the device flows

Every `trecos-v1` scenario is checked by a Maestro flow (named in the flow's
`# covers:` header) or listed here with the reason it is not.
`scripts/check-scenario-coverage.py` fails when a scenario is in neither
place.

- **device test**: an instrumented test in `app/src/androidTest` checks it.
  Those tests run on the same emulator cells as the flows.
- **Robolectric only**: the behaviour is visual or timing-based, and Maestro
  can't assert it (colours, measured widths, animation), or an emulator can't
  reach the state. The named unit test or screenshot checks it on every
  pull request.
- **manual**: it needs hardware, an account or a store purchase that an
  emulator doesn't have. It is checked by hand before a release.

| Scenario | Covered by | Reason |
|---|---|---|
| places: Deep path on a small screen | Robolectric only | The collapse to "…" depends on the measured width; `PathScenariosTest.deepPathOnASmallScreen` fixes a small width |
| places: Inherited colour | Robolectric only | Colour tints can't be asserted by Maestro; `PlaceLookScenariosTest.inheritedColour` and the Roborazzi screenshots check them |
| places: Own colour wins | Robolectric only | Colour stripes; `PlaceLookScenariosTest.ownColourWins` |
| places: Two houses | Robolectric only | The band and pill colours; `PlaceLookScenariosTest.twoHouses` |
| places: Container without photos | Robolectric only | The placeholder icon is a drawable with no text; `PlaceLookScenariosTest.containerWithoutPhotos` |
| items: Scanning into an empty form | Robolectric only | Scanning needs the Play services scanner and a camera pointed at a label; `QrScenariosTest.scanningIntoAnEmptyForm` fakes the scanner |
| items: Scanning when a name exists | Robolectric only | As above; `QrScenariosTest.scanningWhenANameExists` |
| search: Cancelling a scan | Robolectric only | The emulator's Play services can't download the barcode scanner module; `QrScenariosTest.cancellingAScan` fakes the scanner. Checked by hand on a phone before release |
| trash: Automatic purge | Robolectric only | Needs 30 days to pass; `OrganizeStoreTest.automaticPurge` and `aPurgeRemovesPhotoRowsAndThenTheFiles` move the clock |
| trash: Scanning a trashed box | Robolectric only | Needs the barcode scanner (see "Cancelling a scan"); `QrScenariosTest.scanningATrashedBox` |
| qr-codes: Printing several labels | Robolectric only | The number of labels inside a print job isn't visible to Maestro; `QrScenariosTest.printingSeveralLabels` counts them. The device flow checks that printing opens the system dialog |
| qr-codes: Rename keeps the label valid | Robolectric only | Needs the barcode scanner; `QrScenariosTest.renameKeepsTheLabelValid` |
| qr-codes: Label from another program | Robolectric only | Needs the barcode scanner; `QrScenariosTest.labelFromAnotherProgram` |
| qr-codes: One match | Robolectric only | Needs the barcode scanner; `QrScenariosTest.oneMatch` |
| qr-codes: Several houses | Robolectric only | Needs the barcode scanner; `QrScenariosTest.severalHouses` |
| qr-codes: Unknown code | Robolectric only | Needs the barcode scanner; `QrScenariosTest.unknownCode` |
| app-shell: Following the system | Robolectric only | Maestro can't switch the phone's night mode; `ThemeModeTest.followSystemTracksNightMode` and the Dark screenshots |
| app-shell: Forcing a theme | Robolectric only | Same; `ThemeModeTest.forcedThemesIgnoreNightMode` |
| app-shell: First launch on a Portuguese phone | Robolectric only | Maestro can't change the phone's language; `LanguageTest.firstLaunchOnAPortuguesePhone` |
| app-shell: Portuguese (Portugal) phone | Robolectric only | Same; `LanguageTest.firstLaunchOnAPortugalPortuguesePhone` |
| app-shell: Unsupported device language | Robolectric only | Same; `LanguageTest.unsupportedDeviceLanguage` |
| app-shell: Reduced motion | Robolectric only | Animation timing; `TrecosBottomBarTest.reducedMotion`. The device flows run with system animations off |
| app-shell: Leaving a container | Robolectric only | Scroll position after back; `PathScenariosTest.leavingAContainer`. Back itself is exercised by most flows |
| app-shell: Very long name | Robolectric only | Ellipsis inside a measured line; `TextSafetyTest.veryLongName` |
| app-shell: Long unbroken serial number | Robolectric only | Wrapping inside a measured column; `TextSafetyTest.longUnbrokenSerialNumber` |
| app-shell: Opening a photo | Robolectric only | The 250 ms zoom; `PhotoScenariosTest.openingAPhotoAndZoomingIntoASerialLabel`. The photos flow opens the viewer |
| app-shell: Cold start benchmark | device test | `StartupBenchmark` (Macrobenchmark) on the reference emulator, task 8.1 |
| app-shell: Regression blocks a release | device test | The benchmark job gates `release.yml` through `scripts/check-benchmark.py`, task 8.1 |
