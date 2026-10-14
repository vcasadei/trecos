# Spec Delta

## MODIFIED Requirements

### Requirement: Offline operation
Every feature except Google Drive sync, tips and the rating prompt SHALL work with no network connection, including QR scanning, item recognition and photo search, from the first launch. The app MUST NOT show errors or block any screen because the device is offline.

#### Scenario: Using the app in airplane mode
- **AS A** user with airplane mode on
- **WHEN** I add, edit, search, move and delete items
- **THEN** every action succeeds and is saved on the device

#### Scenario: Scanning offline on first launch
- **AS A** user who installed Trecos and has never been online
- **WHEN** I scan a Trecos label
- **THEN** the scan works, with no download prompt

### Requirement: List presentation
Lists of items and containers SHALL support three views: condensed (thumbnail and name on one line), detailed (a larger thumbnail, name, up to two lines of description, quantity, unit price and up to three extra fields chosen in Settings) and grid (photo tiles with the name below, as many columns as fit at 160 dp or more each). The view MUST be switchable from a control in the top bar of every list, and the choice MUST apply app-wide and persist. Fields with no value MUST be hidden rather than shown empty or as zero.

#### Scenario: Switching to detailed view
- **AS A** user browsing a container in condensed view
- **WHEN** I tap the view toggle in the top bar
- **THEN** every list in the app switches to the detailed view
- **AND** the choice is still in effect after restarting the app

#### Scenario: Item without price
- **AS A** user in detailed view
- **WHEN** an item has no unit price
- **THEN** its row shows no price, rather than "0,00"

#### Scenario: Grid on a phone
- **AS A** user on a 360 dp wide phone
- **WHEN** I choose the grid view
- **THEN** items show as two columns of photo tiles, and an item without a photo shows its category icon

### Requirement: Motion
Transitions SHALL last between 150 ms and 250 ms and use fades, short slides and shared-element moves (such as a photo zooming from its position into full screen). Small feedback motions on controls, such as a press scale or a checkmark drawing in, MUST last 250 ms or less and never delay the action. When the system "Remove animations" setting is on, all transitions and feedback motions MUST be instant.

#### Scenario: Opening a photo
- **AS A** user on a container screen
- **WHEN** I tap the main photo
- **THEN** it zooms from its position into full screen in 250 ms or less

#### Scenario: Animations removed
- **AS A** user with "Remove animations" on
- **WHEN** I open a photo or tap a button
- **THEN** the change is instant, with no motion

## ADDED Requirements

### Requirement: Phones without Google Play services
The app SHALL install and run on Android 9+ phones without Google Play services. Every feature MUST work there except Google Drive sync, tips and rating, which MUST stay visible and explain, when opened, that they need Google Play services.

#### Scenario: Huawei phone
- **AS A** user on a phone without Google Play services
- **WHEN** I add items, scan a label, identify a photo and search
- **THEN** everything works as on any other phone

#### Scenario: Opening sync without Play services
- **AS A** user on a phone without Google Play services
- **WHEN** I open Sync in Settings
- **THEN** the screen explains that Google Drive sync needs Google Play services, and offers the local backup instead

### Requirement: Wide screens
On windows 600 dp wide or more (tablets, foldables, phones in landscape), the app SHALL replace the bottom bar with a navigation rail on the side, keep text lines readable by limiting forms and detail screens to 640 dp wide, and default lists to the grid view until the user picks another view. Rotating or resizing MUST keep the current screen, scroll position and anything typed.

#### Scenario: Tablet
- **AS A** user on a 10-inch tablet
- **WHEN** I open a container
- **THEN** the tabs are on a side rail and the items fill the width as a grid

#### Scenario: Rotating while typing
- **AS A** user typing an item name on a phone
- **WHEN** I rotate the phone to landscape
- **THEN** the form keeps what I typed and the same scroll position

### Requirement: Touch targets and contrast
Every tappable control SHALL have a touch area of at least 48 × 48 dp, and text MUST have a contrast ratio of at least 4.5:1 against its background (3:1 for text 18 sp or larger) in both light and dark themes.

#### Scenario: Small icon button
- **AS A** user tapping the view toggle in the top bar
- **WHEN** I tap 20 dp from the centre of its 24 dp icon, outside the icon itself
- **THEN** the toggle responds

#### Scenario: Dark theme
- **AS A** user in dark theme
- **WHEN** I read secondary text such as a breadcrumb
- **THEN** it meets the 4.5:1 contrast ratio
