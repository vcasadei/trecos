# Spec Delta

## Purpose

Offers optional encryption of the inventory database, with a key tied to the
user's Google account, so data is protected on the phone and in Drive yet still
recoverable on a new phone.

## ADDED Requirements

### Requirement: Optional encryption
Database encryption SHALL be a setting, off by default. Turning it on MUST
require a connected Google account, and MUST show progress while the data is
encrypted. If encryption fails at any point, the data MUST remain unencrypted,
intact and usable, and the app MUST report the failure. Until design D19's
removal criteria are met, the setting MUST be labelled "Experimental", and the
confirmation MUST warn that it has been tested on few phones and suggest
exporting a backup first.

#### Scenario: Turning encryption on
- **AS A** user with Google Drive sync connected
- **WHEN** I enable encryption
- **THEN** a progress indicator is shown, and afterwards the setting reads "On"

#### Scenario: Experimental warning
- **AS A** user with Google Drive sync connected
- **WHEN** I tap the encryption setting, which is labelled "Experimental"
- **THEN** the confirmation warns that encryption is experimental and suggests exporting a backup first
- **AND** nothing is encrypted until I confirm

#### Scenario: No Google account
- **AS A** user who never connected Google Drive
- **WHEN** I try to enable encryption
- **THEN** the app explains that a Google account is required for key recovery and offers to connect one

#### Scenario: Interrupted encryption
- **AS A** user whose phone shuts down while encryption is running
- **WHEN** I reopen the app
- **THEN** all my data is present, unencrypted, and the setting reads "Off"

### Requirement: What is encrypted
With encryption on, the database on the phone and the data copies in Google
Drive SHALL be encrypted. Photos MUST NOT be encrypted, and MUST still be
backed up and synced. The key MUST NOT be shown, exported or logged.

#### Scenario: Drive copy
- **AS A** user with encryption on
- **WHEN** I download a data file from the Trecos folder in Drive
- **THEN** its contents are unreadable without the app

### Requirement: Recovery on a new phone
When a user installs Trecos on a new or reset phone and signs in with the same
Google account, the app SHALL retrieve the key and restore the latest synced
data. Changes that were never synced MUST be reported as unrecoverable.

#### Scenario: Lost phone
- **AS A** user with encryption on who lost my phone
- **WHEN** I sign in on a new phone with the same Google account
- **THEN** my houses, containers, items and photos are restored as of the last sync

### Requirement: Turning encryption off
Turning encryption off SHALL decrypt the local database, remove the key from
Google Drive, and store later Drive copies unencrypted. While encryption is on,
disconnecting the Google account MUST be blocked, with an explanation that
encryption must be turned off first.

#### Scenario: Disabling
- **AS A** user with encryption on
- **WHEN** I turn it off and confirm
- **THEN** the local data is decrypted, and the next sync uploads unencrypted copies

#### Scenario: Disconnecting with encryption on
- **AS A** user with encryption on
- **WHEN** I try to disconnect my Google account
- **THEN** the app refuses and explains that encryption must be turned off first
