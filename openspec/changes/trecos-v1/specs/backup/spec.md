# Spec Delta

## Purpose

Gives every user, including those without a Google account, a local backup: a
single file they can save anywhere and import on any phone.

## ADDED Requirements

### Requirement: Export
The app SHALL export a single `.zip` backup, named
`trecos-backup-YYYY-MM-DD.zip`, containing all houses by default, or only the
houses the user selects. It MUST include every item, container, category, tag,
custom field and full-size photo, and MAY omit thumbnails. The user MUST choose
where to save it with the system file picker. When encryption is on, the app
MUST warn that the export is not encrypted before writing it.

#### Scenario: Exporting to Downloads
- **AS A** user
- **WHEN** I export and choose the Downloads folder
- **THEN** "trecos-backup-2026-09-28.zip" is saved there, with all my houses and photos

#### Scenario: Export with encryption on
- **AS A** user with encryption on
- **WHEN** I start an export
- **THEN** I am warned that the file will not be encrypted, and nothing is written unless I continue

#### Scenario: Not enough space
- **AS A** user saving to a location that runs out of space
- **WHEN** the export fails
- **THEN** the app reports the failure and removes the incomplete file

### Requirement: Import
Importing a backup SHALL first show a preview (houses, containers, items and
photos), then offer "Replace everything" or "Add as new houses". Replace MUST
ask for confirmation. Add MUST create the imported houses alongside the existing
ones, with new identities. An import MUST be all or nothing: on any error,
nothing on the phone changes.

#### Scenario: Adding houses from another phone
- **AS A** user importing a backup with 1 house and 120 items
- **WHEN** I choose "Add as new houses"
- **THEN** the house appears alongside my existing houses with its 120 items

#### Scenario: Corrupt file
- **AS A** user importing a damaged zip
- **WHEN** the file cannot be read
- **THEN** the app says the backup is invalid, and my data is unchanged

#### Scenario: Backup from a newer app version
- **AS A** user importing a backup made by a newer version of Trecos
- **WHEN** its format is newer than the app understands
- **THEN** the app asks me to update and imports nothing

### Requirement: Import with sync on
When sync is connected, "Replace everything" SHALL warn that the replaced data
will reach the user's other devices at the next sync.

#### Scenario: Replacing with sync on
- **AS A** user with sync connected
- **WHEN** I choose "Replace everything"
- **THEN** the confirmation says that my other devices will receive this data at the next sync
