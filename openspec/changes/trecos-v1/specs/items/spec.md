# Spec Delta

## Purpose

Defines what an item is, which of its fields are required or optional, how
values are calculated and shown, and how items are added, viewed and edited.

## ADDED Requirements

### Requirement: Item fields
An item SHALL require only a name. It MAY also have: one or more categories,
a quantity, a unit price, brand, model, serial number, QR code, description,
tags, up to three photos, and custom fields. Quantity MUST be a whole number
from 0 to 999,999 and default to 1. Unit price MUST be a decimal of 0 or more,
with the display currency's decimal places.

#### Scenario: Minimal item
- **AS A** user adding an item
- **WHEN** I enter only the name "Raspberry Pi 4" and save
- **THEN** the item is saved with quantity 1 and no price

#### Scenario: Invalid quantity
- **AS A** user editing an item
- **WHEN** I enter 2.5 or -1 as the quantity
- **THEN** the item is not saved and the field shows that quantity must be a whole number from 0

#### Scenario: Missing name
- **AS A** user adding an item
- **WHEN** I try to save without a name
- **THEN** the item is not saved and the name field shows that a name is required

### Requirement: Total value
An item's total value SHALL be its quantity times its unit price, calculated
by the app and never entered by the user. An item without a unit price MUST
have no total value, not zero.

#### Scenario: Screws
- **AS A** user with an item "M3 screws", quantity 200, unit price 0.10
- **WHEN** I view it
- **THEN** its total value is shown as 20.00 in the display currency

### Requirement: Dates added and changed
The app SHALL record when each item was added and when it was last changed, and
MUST show both on the item screen. Changing any field, including a move, MUST
update the last-changed date.

#### Scenario: Editing updates the date
- **AS A** user who changes an item's quantity today
- **WHEN** I open the item
- **THEN** "Last changed" shows today's date and "Added" still shows the original date

### Requirement: Add and edit form
The item form SHALL show the essential fields first: photos, name, categories
(with suggestions), quantity and unit price. The remaining fields MUST be
available behind a "More fields" control. The form MUST offer "Save" and
"Save + new". "Save + new" saves the item and opens an empty form that keeps
the same location and categories. A scan button in the form MUST fill the QR
code field from a scanned code, and also the name when the name is empty.

#### Scenario: Cataloguing a box of cables
- **AS A** user adding cables to "Box A" with the category "Cables > USB-C"
- **WHEN** I tap "Save + new"
- **THEN** the item is saved and a new empty form opens, still in "Box A" and with "Cables > USB-C" selected

#### Scenario: Scanning into an empty form
- **AS A** user in a new-item form with no name
- **WHEN** I scan a label containing "Armario#1"
- **THEN** both the QR code and the name fields contain "Armario#1"

#### Scenario: Scanning when a name exists
- **AS A** user in a form whose name is "Wardrobe"
- **WHEN** I scan "Armario#1"
- **THEN** only the QR code field changes

### Requirement: Form or photo first
The app SHALL let the user choose, in Settings, whether adding an item starts
with the form (default) or with taking a photo. In photo-first mode, the image
source MUST open immediately. After a photo is chosen, the form opens with it
attached and the name field focused. Cancelling the photo MUST still open the
form.

#### Scenario: Photo first
- **AS A** user with "Photo first" enabled
- **WHEN** I tap "+" and then "Item"
- **THEN** the camera or gallery opens, and after taking a photo the form opens with it attached

#### Scenario: Photo cancelled
- **AS A** user in photo-first mode
- **WHEN** I cancel the camera
- **THEN** the form opens with no photo

### Requirement: Item screen
The item screen SHALL show a photo carousel (main photo first), the name, the
location path, the categories, and every field that has a value. Its top bar
MUST have a back arrow, an edit action and an overflow menu with Move, Copy,
Duplicate and Delete. Prices MUST be formatted with the display currency's
symbol and the app language's number format.

#### Scenario: Price formatting in Portuguese
- **AS A** user with the app in Portuguese and currency BRL
- **WHEN** I view an item with unit price 1234.5
- **THEN** it is shown as "R$ 1.234,50"

#### Scenario: Price formatting in English
- **AS A** user with the app in English and currency BRL
- **WHEN** I view the same item
- **THEN** it is shown as "R$1,234.50"
