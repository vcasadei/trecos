# Spec Delta

## Purpose

Lets users rearrange their inventory: move, copy and duplicate items and
containers, within a house or between houses, one at a time or in bulk.

## ADDED Requirements

### Requirement: Move
Users SHALL be able to move an item or container to any container or to the top
level of any house. The destination picker MUST allow browsing the hierarchy
and searching by name, and MUST start at the current house. Moving a container
MUST move everything inside it. A container MUST NOT be movable into itself or
into any container inside it.

#### Scenario: Moving an item
- **AS A** user viewing "Raspberry Pi 4" in "Box A"
- **WHEN** I choose Move and pick "Office drawer"
- **THEN** the item is in "Office drawer", and its last-changed date is updated

#### Scenario: Moving a container into its own child
- **AS A** user moving "Box A"
- **WHEN** I browse to "Box A > Cables bag" in the picker
- **THEN** that destination cannot be selected, and the picker explains why

### Requirement: Move between houses
The destination picker SHALL let the user choose another house first and then a
container in it. When the moved things use custom categories or tags that don't
exist in the target house, those categories and tags MUST be created there
automatically. When a moved QR code is already used in the target house, the app
MUST stop and offer to edit the moved code, remove it, or cancel the move.

#### Scenario: Carrying custom categories along
- **AS A** user moving "Drill" (custom category "Power tools", tag "borrowed") from "Parents" to "Apartment"
- **WHEN** neither exists in Apartment
- **THEN** the drill arrives in Apartment with "Power tools" and "borrowed", now created in Apartment

#### Scenario: QR code clash
- **AS A** user moving an item with QR "Drill-01" to a house where "Drill-01" is taken
- **WHEN** I confirm the destination
- **THEN** I must choose to edit the code, remove it, or cancel, before anything moves

### Requirement: Copy
Users SHALL be able to copy an item or container to any destination, in the same
house or another one. A copied container MUST include copies of everything
inside it, photos included. Copies MUST get their own identity and MUST NOT
carry QR codes, since a label identifies one physical object. Copying between
houses MUST follow the same category and tag rules as moving.

#### Scenario: Copying a labelled box
- **AS A** user copying "Box A" (QR "BOX-A", 12 items) to "Garage"
- **WHEN** the copy completes
- **THEN** "Garage" contains a new "Box A" with copies of the 12 items and no QR codes
- **AND** the original "Box A" still has "BOX-A"

### Requirement: Duplicate
Duplicate SHALL create a copy in the same location, named with a translated
" (copy)" suffix, and open it for editing. It follows the copy rules, so it has
no QR code.

#### Scenario: Duplicating an item
- **AS A** user viewing "USB-C cable 1m"
- **WHEN** I choose Duplicate
- **THEN** "USB-C cable 1m (copy)" is created in the same container and opens in the edit form

### Requirement: Multi-select
A long press on a row in any container, house or search list SHALL start
selection mode. In selection mode, a tap toggles a row, the top bar shows the
number selected, and it offers Select all, Move, Copy and Delete for the
whole selection. Back or clearing the selection MUST leave selection mode
without changing anything.

#### Scenario: Moving several items
- **AS A** user in "Box A"
- **WHEN** I long-press three items, tap Move and choose "Drawer 2"
- **THEN** all three are in "Drawer 2" and selection mode ends

#### Scenario: Leaving selection
- **AS A** user with two rows selected
- **WHEN** I press back
- **THEN** the selection clears and nothing is moved, copied or deleted
