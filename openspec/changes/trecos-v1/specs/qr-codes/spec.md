# Spec Delta

## Purpose

Connects physical labels to the inventory: every item or container can carry a
QR code that stays stable through renames, can be printed, and can be scanned
to find or create things, even when the label came from another program.

## ADDED Requirements

### Requirement: QR code identity
An item or container MAY have a QR code, which is a plain text value of up to
256 characters. When one is created, its QR code SHALL be filled with its name
if that code is free in the house, and otherwise left empty with a note. Renaming
MUST NOT change the QR code. QR codes MUST be unique within a house, and the
same code MAY exist in different houses.

#### Scenario: Code follows the name at creation
- **AS A** user creating a container named "Armario#1"
- **WHEN** I save it
- **THEN** its QR code is "Armario#1"

#### Scenario: Rename keeps the label valid
- **AS A** user who renames "Armario#1" to "Wardrobe"
- **WHEN** I scan the old printed label
- **THEN** "Wardrobe" opens

#### Scenario: Name already used as a code
- **AS A** user creating a second "Box 1" in a house where "Box 1" is already a code
- **WHEN** I save it
- **THEN** the new container is saved with no QR code, and a note says the code was already in use

#### Scenario: Entering a taken code
- **AS A** user editing a QR code
- **WHEN** I enter a code another item in the same house already has
- **THEN** the change is not saved, and the message names the item that holds it, with a link to it

### Requirement: Showing, sharing and printing labels
The QR code on an item or container screen SHALL open a large view of the code
with its text printed underneath, offering Share (as an image) and Print (the
system print dialog). Selecting several items or containers MUST allow printing
all their labels on one print job.

#### Scenario: Printing one label
- **AS A** user on the "Box A" screen
- **WHEN** I tap the QR code and choose Print
- **THEN** the system print dialog opens with a label showing the QR code and the text "Box A"

#### Scenario: Printing several labels
- **AS A** user who selected 6 containers
- **WHEN** I choose Print QR
- **THEN** one print job contains 6 labels

#### Scenario: Nothing to print
- **AS A** user who selected 3 containers, 1 of them without a QR code
- **WHEN** I choose Print QR
- **THEN** 2 labels are printed, and a note says 1 had no code

### Requirement: Scanning
Scanning SHALL read QR codes only, without asking for camera permission, and use
the scanned text exactly, whatever program created it. Surrounding whitespace
MUST be trimmed, and matching MUST be exact and case-sensitive.

#### Scenario: Label from another program
- **AS A** user scanning a QR code printed by a label maker, containing "XYZ-0042"
- **WHEN** the scan completes
- **THEN** the app looks up "XYZ-0042"

### Requirement: Lookup after scanning
After a scan, the app SHALL search every house. With one match, it MUST open that
item or container. With matches in several houses, it MUST ask which house. With
no match, it MUST offer "Create item" or "Create container" with the QR code and
the name pre-filled with the scanned text, followed by a location picker. A code
that matches something in the trash follows the `trash` capability.

#### Scenario: One match
- **AS A** user scanning "BOX-A"
- **WHEN** only "Apartment" has it
- **THEN** that container's screen opens

#### Scenario: Several houses
- **AS A** user scanning "Box 1"
- **WHEN** both "Apartment" and "Parents" have it
- **THEN** I choose the house, and that one opens

#### Scenario: Unknown code
- **AS A** user scanning "XYZ-0042"
- **WHEN** nothing has it
- **THEN** I am offered to create an item or container with QR code and name "XYZ-0042"
