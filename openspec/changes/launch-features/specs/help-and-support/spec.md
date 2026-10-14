# Spec Delta

## MODIFIED Requirements

### Requirement: No tracking
The app SHALL NOT include advertising, analytics or crash-reporting SDKs, and MUST NOT send usage data anywhere, with one disclosed exception: Google's ML Kit, which reads QR codes, text and photo labels on the phone, sends Google metrics about its own use (device and app details, timings, input and output sizes), never the photos, text or labels themselves. The privacy policy (PRIVACY.md and the page About links to) and the Play data safety form MUST state this. Other network access is limited to Google Drive sync, Google Play services (sign-in, tips, rating) and links the user opens.

#### Scenario: Offline user
- **AS A** user who never connects Google Drive
- **WHEN** I use the app for a month
- **THEN** the only data sent off the phone is ML Kit's usage metrics, and none of it contains my items, photos or text

#### Scenario: Recognition stays on the phone
- **AS A** user who identifies 20 items from photos and searches from a photo
- **WHEN** the network traffic of the app is recorded
- **THEN** none of it carries photos, labels or recognized text

#### Scenario: Reading the disclosure
- **AS A** user opening "Privacy policy" from About
- **WHEN** I read it
- **THEN** it names Google's ML Kit, what its metrics contain, and that photos and text are never sent

### Requirement: Rating
A "Rate Trecos" button SHALL open Trecos's Google Play page. The app MUST show the in-app rating prompt automatically at most once in the app's lifetime, and only after 14 days since install, with 20 or more items, over 5 or more separate sessions, and never during an add, edit, delete or sync-conflict flow. On a phone without Google Play services the automatic prompt MUST never appear, and the button MUST explain that rating needs Google Play.

#### Scenario: The single prompt
- **AS A** user who has met all the conditions
- **WHEN** I return to the Home screen after a session
- **THEN** the rating prompt may appear once, and never again afterwards

#### Scenario: Too early
- **AS A** user on day 3 with 50 items
- **WHEN** I use the app
- **THEN** no rating prompt is shown

#### Scenario: No Google Play
- **AS A** user on a phone without Google Play services
- **WHEN** I tap "Rate Trecos"
- **THEN** the app explains that rating needs Google Play, and no prompt ever appears on its own
