# Spec Delta

## Purpose

Defines the frame every Trecos screen lives in: navigation, themes, languages,
list presentation, text safety, motion, offline behaviour and performance targets.

## ADDED Requirements

### Requirement: Bottom navigation bar
The app SHALL show a floating bottom bar with exactly three tabs in this order:
Search, Home, Settings. An unselected tab MUST show only its text label. The
selected tab MUST show its label plus a circle containing the tab's icon
(magnifier, house, gear) that rises above the bar's top edge. When the
selection changes, the circle SHALL move to the new tab in 250 ms or less.

#### Scenario: Switching tabs
- **AS A** user on the Home tab
- **WHEN** I tap "Search"
- **THEN** the Search screen opens
- **AND** the raised circle with the magnifier icon moves under the "Search" label
- **AND** "Home" is shown as a text label only

#### Scenario: Reduced motion
- **AS A** user who turned on Android's "Remove animations" setting
- **WHEN** I switch tabs
- **THEN** the circle appears at the new tab immediately, with no movement

### Requirement: Back navigation
Every screen other than the three tab roots SHALL show a back arrow at the top
left, and the system back gesture MUST behave the same as that arrow.

#### Scenario: Leaving a container
- **AS A** user viewing a container
- **WHEN** I tap the back arrow or use the system back gesture
- **THEN** the previous screen is shown with its scroll position preserved

#### Scenario: Back from a tab root
- **AS A** user on the root of the Search or Settings tab
- **WHEN** I press system back
- **THEN** the app returns to the Home tab
- **AND** pressing back again on the Home root leaves the app

### Requirement: Themes
The app SHALL offer three theme options: Follow system (default), White and
Dark. White MUST use an off-white background. Dark MUST use pure black (#000000)
backgrounds so OLED pixels are off. The app MUST NOT take colours from the
device wallpaper.

#### Scenario: Following the system
- **AS A** user with the theme left on "Follow system"
- **WHEN** I switch my phone to night mode
- **THEN** Trecos switches to the Dark theme without being restarted

#### Scenario: Forcing a theme
- **AS A** user who picked "White"
- **WHEN** my phone is in night mode
- **THEN** Trecos stays on the White theme

### Requirement: Languages
The app SHALL be fully available in English and Portuguese (Brazil), with an
in-app language setting that works on every supported Android version. On first
launch the app MUST use the device language when it is one of these two, and
English otherwise.

#### Scenario: First launch on a Portuguese phone
- **AS A** new user whose phone is set to Portuguese (Brazil)
- **WHEN** I open Trecos for the first time
- **THEN** every screen, built-in category and FAQ entry is shown in Portuguese

#### Scenario: Unsupported device language
- **AS A** new user whose phone is set to German
- **WHEN** I open Trecos for the first time
- **THEN** the app is shown in English

#### Scenario: Changing language in the app
- **AS A** user on Android 9
- **WHEN** I choose "English" in Settings
- **THEN** the whole app switches to English and keeps it after a restart

### Requirement: List presentation
Lists of items and containers SHALL support two views: condensed (thumbnail
and name on one line) and detailed (a larger thumbnail, name, up to two lines
of description, quantity, unit price and up to three extra fields chosen in
Settings). The view MUST be switchable from a control in the top bar of every
list, and the choice MUST apply app-wide and persist. Fields with no value MUST
be hidden rather than shown empty or as zero.

#### Scenario: Switching to detailed view
- **AS A** user browsing a container in condensed view
- **WHEN** I tap the view toggle in the top bar
- **THEN** every list in the app switches to the detailed view
- **AND** the choice is still in effect after restarting the app

#### Scenario: Item without price
- **AS A** user in detailed view
- **WHEN** an item has no unit price
- **THEN** its row shows no price, rather than "0,00"

### Requirement: Text never breaks layouts
Any text shown in lists, bars, chips or headers SHALL be limited to a
defined number of lines and end with "…" when it doesn't fit. A single word
longer than the available width MUST wrap inside the word instead of pushing
other content off-screen.

#### Scenario: Very long name
- **AS A** user whose item is named with 200 characters
- **WHEN** it is shown in a list
- **THEN** the name ends with "…" within its line limit
- **AND** the quantity and price of that row remain fully visible

#### Scenario: Long unbroken serial number
- **AS A** user viewing an item whose serial number is 40 characters with no spaces
- **WHEN** the detailed view shows the serial number
- **THEN** the serial wraps or ends with "…" inside its column, and no content is hidden behind it

### Requirement: Motion
Transitions SHALL last between 150 ms and 250 ms and use only fades and short
slides, plus a zoom when opening a photo. When the system "Remove animations"
setting is on, all transitions MUST be instant.

#### Scenario: Opening a photo
- **AS A** user on a container screen
- **WHEN** I tap the main photo
- **THEN** it zooms from its position into full screen in 250 ms or less

### Requirement: Offline operation
Every feature except Google Drive sync, QR scanning, tips and the rating prompt
SHALL work with no network connection. The app MUST NOT show errors or block
any screen because the device is offline.

#### Scenario: Using the app in airplane mode
- **AS A** user with airplane mode on
- **WHEN** I add, edit, search, move and delete items
- **THEN** every action succeeds and is saved on the device

#### Scenario: Scanning offline
- **AS A** user whose phone never downloaded the Play services scanner and is offline
- **WHEN** I tap the QR scan button
- **THEN** the app explains that scanning needs a one-time download over the internet
- **AND** typing a code into search still works

### Requirement: Performance on low-end phones
On a reference phone with Android 9 and 2 GB RAM, cold start to an interactive
Home screen SHALL take 1.5 seconds or less (median of 10 runs). Scrolling a list
of 1,000 items MUST keep janky frames under 5%.

#### Scenario: Cold start benchmark
- **AS A** the release benchmark
- **WHEN** it cold-starts the app 10 times on the reference device with 1,000 items stored
- **THEN** the median time to the first interactive Home frame is 1.5 s or less

#### Scenario: Regression blocks a release
- **AS A** the release benchmark
- **WHEN** the median cold start exceeds 1.5 s
- **THEN** the release checklist fails and the release is not published
