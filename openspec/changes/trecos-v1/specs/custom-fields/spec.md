# Spec Delta

## Purpose

Lets users record details the built-in item fields don't cover, either for
every item in a house or for a single item, with typed values and unit labels.

## ADDED Requirements

### Requirement: Field types
A custom field SHALL have a name and one type: Text, Number, Date or Yes/No. A
Number field MAY have a free-text unit label (for example "cm", "GB", "W"),
shown after its value. There is no unit conversion. A value that doesn't match
the field's type MUST be rejected.

#### Scenario: Number with unit
- **AS A** user with a Number field "RAM" with unit "GB"
- **WHEN** I enter 4
- **THEN** the item shows "RAM: 4 GB"

#### Scenario: Wrong type
- **AS A** user entering "four" in the Number field "RAM"
- **WHEN** I save
- **THEN** the item is not saved and the field shows it needs a number

### Requirement: House-wide fields
Users SHALL be able to define fields in Settings that apply to every item in
the current house. They appear under "More fields" on every item's form in that
house, and filling them in is always optional. Renaming a definition MUST keep
the existing values. Deleting one MUST first confirm how many items have a value,
then remove those values.

#### Scenario: Adding a house-wide field
- **AS A** user who defines "Purchase date" (Date) for "Apartment"
- **WHEN** I open any item form in Apartment
- **THEN** "Purchase date" is available under "More fields", empty

#### Scenario: Deleting a used field
- **AS A** user deleting "Purchase date", which 40 items have filled in
- **WHEN** I choose Delete
- **THEN** I am told 40 values will be removed, and nothing changes unless I confirm

### Requirement: Per-item fields
Users SHALL be able to add a field to a single item from its form, with a name,
type and value. It MUST NOT appear on other items.

#### Scenario: One-off field
- **AS A** user editing a 3D printer
- **WHEN** I add a Text field "Firmware" with value "Klipper"
- **THEN** only that item shows "Firmware: Klipper"

### Requirement: Custom fields in lists and search
Custom fields SHALL be selectable as detailed-view extra fields. The text of
custom field values MUST be matched by search as part of the description.

#### Scenario: Searching a custom value
- **AS A** user
- **WHEN** I search "Klipper" with match set to Description
- **THEN** the 3D printer with "Firmware: Klipper" is found
