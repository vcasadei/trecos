# Spec Delta

## Purpose

Keeps other people out of the app using the phone's own unlock, with no
Trecos password to forget, and keeps the optional profile minimal and under the user's control.

## ADDED Requirements

### Requirement: Optional app lock
The app SHALL offer an app lock, off by default, that unlocks with the phone's
own fingerprint, face, PIN, pattern or password. Turning it on MUST require the
phone to have a screen lock set. When none is set, the app MUST explain that one
is needed and leave the lock off. There is no Trecos-specific password.

#### Scenario: Turning it on
- **AS A** user whose phone has a fingerprint set up
- **WHEN** I enable App lock and confirm with my fingerprint
- **THEN** the lock is on

#### Scenario: Phone without a screen lock
- **AS A** user whose phone has no screen lock
- **WHEN** I try to enable App lock
- **THEN** the app explains that a phone screen lock is required, and the setting stays off

### Requirement: When the app locks
With the lock on, the app SHALL require unlocking when it starts, and when it
returns to the foreground after being in the background longer than the chosen
timeout: Immediately, 1 minute (default), 5 minutes or 15 minutes. While locked,
no inventory content MUST be visible, including in the recent-apps screen.
Cancelling or failing the unlock MUST keep the app locked.

#### Scenario: Returning quickly
- **AS A** user with the 1-minute timeout
- **WHEN** I switch to another app for 30 seconds and come back
- **THEN** Trecos opens without asking to unlock

#### Scenario: Failed unlock
- **AS A** user opening a locked Trecos
- **WHEN** my fingerprint is rejected and I cancel
- **THEN** the app stays locked and shows only an Unlock button

#### Scenario: Recent-apps preview
- **AS A** user with the lock on
- **WHEN** I open the recent-apps screen
- **THEN** Trecos's preview shows no inventory content

### Requirement: Screen lock removed later
If the phone's screen lock is removed while the app lock is on, the app SHALL
open without unlocking, turn the app lock off, and tell the user why.

#### Scenario: User removed their phone PIN
- **AS A** user who removed the phone's screen lock
- **WHEN** I open Trecos
- **THEN** it opens and tells me the app lock was turned off because the phone has no screen lock

### Requirement: Optional profile
The app SHALL work fully without any profile. A user MAY enter a name and an
e-mail address, which are used only to label sync records and, later, house
sharing. Both MUST be editable and deletable at any time, stay on the device
(and in the user's own Drive when sync is on) until the user deletes them or
uninstalls the app, and MUST NOT appear in logs.

#### Scenario: No profile
- **AS A** user who never opens the profile settings
- **WHEN** I use every non-sync feature
- **THEN** nothing asks me for a name, e-mail or account

#### Scenario: Deleting the profile
- **AS A** user with a saved name and e-mail
- **WHEN** I delete my profile
- **THEN** both are removed from the device, and future sync records use only the Google account shown by Google
