# Spec Delta

## Purpose

Makes deletion safe and recoverable: confirm first, undo right away, keep in a
per-house trash for 30 days, and let users decide what survives when a container goes.

## ADDED Requirements

### Requirement: Confirm before deleting
Every delete action SHALL ask for confirmation, stating what will be deleted
and how many things it contains. Cancelling MUST leave everything unchanged.

#### Scenario: Deleting an item
- **AS A** user viewing an item
- **WHEN** I choose Delete
- **THEN** a confirmation names the item, and nothing happens until I confirm

#### Scenario: Cancelling
- **AS A** user shown the delete confirmation
- **WHEN** I tap Cancel
- **THEN** nothing is deleted

### Requirement: Undo
Right after a delete is confirmed, the app SHALL show a "Deleted" message with
Undo for at least 5 seconds. Undo MUST restore exactly what was deleted, to
where it was.

#### Scenario: Undoing a delete
- **AS A** user who just deleted "USB hub"
- **WHEN** I tap Undo
- **THEN** "USB hub" is back in its container, unchanged

### Requirement: Per-house trash with 30-day retention
Deleted items and containers SHALL go to their house's trash, reachable from the
house menu and from Settings. Trashed things MUST NOT appear in lists, search
results or value totals. They MUST be permanently deleted 30 days after they
were trashed, and their photos then removed from the device. "Empty trash" and
"Delete permanently" MUST each ask for confirmation.

#### Scenario: Automatic purge
- **AS A** the daily maintenance job
- **WHEN** an item has been in the trash for more than 30 days
- **THEN** it and its photos are permanently removed

#### Scenario: Trashed items hidden
- **AS A** user who trashed "Old mouse"
- **WHEN** I search for "mouse"
- **THEN** "Old mouse" is not in the results

### Requirement: Restore
Restoring SHALL return an item or container, with its contents, to its original
location. When that location no longer exists or is itself in the trash, the
app MUST ask where to restore it. When a restored thing's QR code is now used by
something else in the house, the restored copy MUST lose its code, and the app
MUST say so.

#### Scenario: Normal restore
- **AS A** user in the trash
- **WHEN** I restore "Cables bag"
- **THEN** it is back in "Box A" with all its items

#### Scenario: Original location gone
- **AS A** user restoring an item whose container was permanently deleted
- **WHEN** I tap Restore
- **THEN** the app asks me to choose a destination before restoring

### Requirement: Scanning a trashed code
Scanning a QR code that belongs to something in the trash SHALL open a notice
that it is in the trash, with a Restore option.

#### Scenario: Scanning a trashed box
- **AS A** user scanning the label of a trashed "Box C"
- **WHEN** the scan completes
- **THEN** I am told "Box C" is in the trash and offered Restore

### Requirement: Deleting a container
Deleting a container that has contents SHALL offer: "Move everything to trash",
"Choose what to keep", and Cancel. "Choose what to keep" MUST list the
container's sub-containers and items with checkboxes. A checked sub-container
moves whole, with its contents, and a sub-container's arrow opens it so
individual items can be picked from inside. "Move selected to…" MUST offer any
container, the parent level, or a new container at the parent level. Moved
things leave the list, so the user can repeat the step with other destinations.
"Finish" MUST confirm, then move the container and whatever is still listed to
the trash.

#### Scenario: Keeping some things
- **AS A** user deleting "Box A" with "Cables bag", "Raspberry Pi 4" and "Broken mouse"
- **WHEN** I move "Cables bag" to "Garage shelf", the Pi to "Office drawer", and tap Finish
- **THEN** after confirming, "Box A" and "Broken mouse" are in the trash, and the other two are at their new places

#### Scenario: Leaving the keep screen early
- **AS A** user who already moved some things out and then presses back
- **WHEN** I leave the keep screen without tapping Finish
- **THEN** "Box A" is not deleted, and the things already moved stay at their new places

### Requirement: Deleting a house
Deleting a house SHALL require typing the house's name to confirm, and it MUST
be permanent on the phone, removing the house, its contents, its trash and its
photos. With sync on, its copy in Drive is kept and can be restored (sync spec
"Deleted houses"). The confirmation MUST suggest exporting a backup first. The
last remaining house MUST NOT be deletable.

#### Scenario: Deleting a house
- **AS A** user with two houses
- **WHEN** I delete "Beach house" and type its name
- **THEN** it and everything in it are permanently removed

#### Scenario: Wrong name typed
- **AS A** user deleting "Beach house"
- **WHEN** the typed name doesn't match
- **THEN** the delete button stays disabled
