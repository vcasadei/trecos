# Spec Delta

## Purpose

Helps users learn the app, contact the author, rate and support Trecos, and see
its license and credits, without ads, tracking or pressure.

## ADDED Requirements

### Requirement: FAQ
Settings > Help SHALL show an FAQ as an expandable accordion, in the app
language, with these ten questions: how to add a first item; how QR labels work;
how to move things between containers or houses; how to print QR labels; how
backup and sync work; what happens when something is deleted; how to set up the
app lock; whether data is private; whether an account is needed and what that
means; and how to get data back after losing or changing phones.

#### Scenario: Reading an answer
- **AS A** user in Help
- **WHEN** I tap "Do I need an account?"
- **THEN** its answer expands and the other answers stay collapsed

### Requirement: Contact
Help and About SHALL offer "Contact us". It opens the user's e-mail app with a
message to hello@trecos.app, a subject type chosen by the user (Question,
License quote, Suggestion or Problem), and the app version and Android version
filled in. No other data MUST be added. If no e-mail app is installed, the app
MUST show the address so it can be copied.

#### Scenario: Asking for a quote
- **AS A** user
- **WHEN** I choose Contact us and then "License quote"
- **THEN** my e-mail app opens addressed to hello@trecos.app with subject "License quote" and the version details

#### Scenario: No e-mail app
- **AS A** user without an e-mail app
- **WHEN** I choose Contact us
- **THEN** the address is shown with a copy button

### Requirement: Rating
A "Rate Trecos" button SHALL open Trecos's Google Play page. The app MUST show
the in-app rating prompt automatically at most once in the app's lifetime, and
only after 14 days since install, with 20 or more items, over 5 or more separate
sessions, and never during an add, edit, delete or sync-conflict flow.

#### Scenario: The single prompt
- **AS A** user who has met all the conditions
- **WHEN** I return to the Home screen after a session
- **THEN** the rating prompt may appear once, and never again afterwards

#### Scenario: Too early
- **AS A** user on day 3 with 50 items
- **WHEN** I use the app
- **THEN** no rating prompt is shown

### Requirement: Tips
Settings > Support Trecos SHALL offer three one-off tips, small, medium and
large, paid through Google Play and repeatable. A tip MUST unlock nothing, since
all features are free, and MUST be followed by a thank-you message. The app MUST
NOT show ads or link to payment methods outside Google Play.

#### Scenario: Tipping
- **AS A** user
- **WHEN** I buy the medium tip through Google Play
- **THEN** a thank-you message is shown and nothing else changes

#### Scenario: Purchase cancelled
- **AS A** user
- **WHEN** I cancel the Google Play purchase
- **THEN** nothing is charged and no thank-you message appears

#### Scenario: Google Play unavailable
- **AS A** user on a phone without Google Play services
- **WHEN** I open Support Trecos
- **THEN** it explains that tips need Google Play

### Requirement: About
Settings > About SHALL show the app version; the license (PolyForm Noncommercial
1.0.0) and a note that commercial use requires a license from the author; links
to the source code, the website (trecos.app) and the privacy policy; "Contact
us"; "Rate Trecos"; and "Open-source licenses", which MUST list every
third-party component shipped in the app with its license text.

#### Scenario: Viewing licenses
- **AS A** user
- **WHEN** I open "Open-source licenses"
- **THEN** every bundled library is listed, and tapping one shows its license text

### Requirement: No tracking
The app SHALL NOT include advertising, analytics or crash-reporting SDKs, and
MUST NOT send usage data anywhere. Network access is limited to Google Drive
sync, Google Play services (scanning, sign-in, tips, rating) and links the user
opens.

#### Scenario: Offline user
- **AS A** user who never connects Google Drive
- **WHEN** I use the app for a month
- **THEN** Trecos itself has sent no data off the phone
