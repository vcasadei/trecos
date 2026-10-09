# Spec Delta

## Purpose

Keeps the inventory in step across the user's devices through a visible folder
in their own Google Drive, merging automatically and asking the user only when
the same detail changed in two places.

## ADDED Requirements

### Requirement: Connecting Google Drive
Sync SHALL be optional and set up in Settings > Sync by connecting a Google
account. Connecting MUST create a visible "Trecos" folder in the user's My Drive
and run a first sync. The app MUST only access files it created itself.
Disconnecting MUST stop sync and keep all data on the phone.

#### Scenario: First connection
- **AS A** user
- **WHEN** I connect my Google account
- **THEN** a "Trecos" folder appears in my Drive and my data is uploaded

#### Scenario: Sign-in cancelled
- **AS A** user
- **WHEN** I cancel the Google sign-in
- **THEN** sync stays off and nothing changes

### Requirement: Restoring onto a device
When a Google account is connected on a device where that account already has
Trecos data in Drive, the app SHALL offer to restore it. If the device already
has local data, the app MUST offer to either merge the two, or keep only the
Drive data.

#### Scenario: New phone
- **AS A** user who has only the default empty house on a new phone
- **WHEN** I connect my Google account
- **THEN** I am offered to restore my houses from Drive

### Requirement: Schedule
Sync SHALL run automatically at the chosen frequency: every day (default), every
5, 15 or 30 days, or never. It MUST run only when a network is available. A
"Sync now" button MUST always be available. With "Photos only on Wi-Fi" on (the
default), photos MUST only upload and download on unmetered networks, while data
still syncs on any network.

#### Scenario: Daily sync
- **AS A** user with the default schedule
- **WHEN** a day has passed and the phone has internet
- **THEN** a sync runs in the background

#### Scenario: Mobile data
- **AS A** user on mobile data with "Photos only on Wi-Fi" on
- **WHEN** a sync runs
- **THEN** data changes sync, and new photos wait for Wi-Fi

### Requirement: Sync never blocks the app
Sync SHALL run in the background, without blocking any screen. Settings > Sync
MUST show the last successful sync, the current status, and any error in plain
language. Failed syncs MUST be retried automatically.

#### Scenario: Drive unreachable
- **AS A** user whose sync fails because Drive is down
- **WHEN** I open Settings > Sync
- **THEN** I see when the last sync succeeded and that a retry is scheduled

### Requirement: Merging and conflicts
Sync SHALL merge changes field by field. When different fields of the same thing
changed on different devices, both changes MUST be kept without asking. A
conflict arises only when the same field changed on both sides, or when one side
edited something the other deleted. Each conflict MUST be resolved on a conflict
screen that shows both values and offers "Keep mine" or "Keep theirs". Until it's
resolved, the local value MUST stay in effect, and Settings MUST show a badge.

#### Scenario: No conflict
- **AS A** user who changed a Pi's quantity on the phone and its description on the tablet
- **WHEN** both devices sync
- **THEN** both devices show the new quantity and the new description, and nothing is asked

#### Scenario: Same field
- **AS A** user who set quantity 2 on the phone and 5 on the tablet
- **WHEN** the phone syncs after the tablet
- **THEN** a conflict asks "Quantity: 2 or 5?"

#### Scenario: Edit versus delete
- **AS A** user who trashed an item on one device and edited it on another
- **WHEN** they sync
- **THEN** a conflict asks whether to delete it or keep it

### Requirement: Safe and repeatable
Sync SHALL be safe to interrupt and repeat. A sync interrupted at any point MUST
leave local data intact and resume correctly next time. A sync with no changes
MUST NOT create a new history entry. Each photo MUST be uploaded at most once,
identified by its content.

#### Scenario: Interrupted sync
- **AS A** user whose phone loses signal during a sync
- **WHEN** the next sync runs
- **THEN** it completes without duplicates or lost changes

#### Scenario: Nothing changed
- **AS A** user
- **WHEN** I tap "Sync now" twice with no changes in between
- **THEN** the history gains at most one entry

### Requirement: Sync history
Settings > Sync SHALL list the history of syncs, each with its time, Google user
(name and e-mail), device name and a summary of changes and conflicts. Location
MUST NOT be recorded. The device name MUST be editable. Devices are identified by
an app-generated identifier, not a hardware identifier. Drive keeps each device's
last 30 syncs, and older entries are removed.

#### Scenario: Viewing history
- **AS A** user
- **WHEN** I open the sync history
- **THEN** I see entries such as "27 Sep 21:14, Vitor, Pixel 6, 3 changes"

### Requirement: Format compatibility
Every sync record SHALL carry a format version. A device MUST refuse to merge
data in a newer format than it understands, and ask the user to update the app.
A device MUST still read the previous format for at least one release.

#### Scenario: Old app, new data
- **AS A** user whose tablet runs an older Trecos version
- **WHEN** it finds data written in a newer format
- **THEN** it asks me to update and does not merge

### Requirement: Deleted houses
With sync connected, deleting a house SHALL NOT delete anything from Drive.
The app MUST instead mark the house as deleted in Drive, at once or at the next
sync when offline, and every other synced device MUST remove the house at its
next sync. Settings > Sync MUST list the deleted houses with "Restore", which
brings a house back as of its last sync onto this device and, through sync,
onto the devices that removed it. A device that has unsynced changes to a house
deleted elsewhere, or for which it is the only house, MUST keep the house and
sync it, which takes it off the deleted list: an edit wins over a delete, as
with items. A house marked as deleted MUST NOT be offered for restore when
connecting.

#### Scenario: Deleting a synced house
- **AS A** user with sync on and two houses
- **WHEN** I delete "Beach house" on my phone
- **THEN** it is removed from my phone, and its data stays in my Drive
- **AND** my tablet removes it at its next sync

#### Scenario: Restoring a deleted house
- **AS A** user who deleted "Beach house"
- **WHEN** I open Settings > Sync > Deleted houses and tap "Restore" next to it
- **THEN** "Beach house" is back on my phone as of its last sync
- **AND** it comes back on my tablet at its next sync

#### Scenario: Unsynced changes on another device
- **AS A** user whose tablet changed "Beach house" without syncing yet
- **WHEN** the tablet syncs after I deleted the house on my phone
- **THEN** the tablet keeps "Beach house" and syncs its changes
- **AND** "Beach house" is no longer on the deleted list

### Requirement: Folder changed outside the app
If the Trecos folder in Drive is deleted or its files are altered outside the
app, the next sync SHALL detect it, refuse to merge anything it cannot read, and
offer to re-upload the phone's data.

#### Scenario: Folder deleted
- **AS A** user who deleted the Trecos folder in Drive
- **WHEN** the next sync runs
- **THEN** the app says the Drive copy is missing and offers to upload my data again
