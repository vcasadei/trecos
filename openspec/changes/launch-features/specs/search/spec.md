# Spec Delta

## MODIFIED Requirements

### Requirement: Scope and match fields
Results SHALL be limited by a scope, Items (default), Containers or Both, and by where to match: Name and description (default), Name only, Description only, or Photos. Matching on "Name" MUST also match brand, model, serial number and QR code. Matching on "Photos" MUST match the labels recognized in an item's or container's photos, in the app's language; "Name and description" MUST include them too.

#### Scenario: Finding by serial number
- **AS A** user
- **WHEN** I type "SN4478" with match set to Name
- **THEN** the item whose serial number is "SN4478X21" is found

#### Scenario: Containers only
- **AS A** user
- **WHEN** I set the scope to Containers and type "box"
- **THEN** only containers are listed

#### Scenario: Finding by what the photo shows
- **AS A** user whose item "Bosch GSB 13" has a photo of a drill and no description
- **WHEN** I type "drill"
- **THEN** "Bosch GSB 13" is found

#### Scenario: Photos only
- **AS A** user with an item named "Drill bits" and no photo
- **WHEN** I type "drill" with match set to Photos
- **THEN** "Drill bits" is not listed, and items whose photos show a drill are

## ADDED Requirements

### Requirement: Search from a photo
The Search tab SHALL offer a photo button that takes or picks a photo (camera app or gallery), recognizes its labels on the phone, and searches for items and containers whose photos have the same labels, ordered by how many labels they share, best first. The photo used for searching MUST NOT be saved, and its labels MUST be shown as the query so the user can edit them.

#### Scenario: Finding the drill
- **AS A** user who has catalogued a drill with a photo
- **WHEN** I search from a photo of the drill
- **THEN** the drill is the first result, and the query shows labels such as "drill, tool"

#### Scenario: Nothing similar
- **AS A** user searching from a photo of something I never catalogued
- **WHEN** no photo shares a label with it
- **THEN** the count reads 0, the recognized labels stay in the query, and the photo is not kept

### Requirement: Labels for existing photos
Photos added before this version SHALL get labels in the background, a batch at a time, without slowing the app, and only while the phone is not in battery saver. Until a photo has labels it MUST simply not match photo searches.

#### Scenario: Updating with 500 photos
- **AS A** user with 500 photos who installs this version
- **WHEN** I keep using the app
- **THEN** browsing and typing stay smooth, and photo search finds more items as labelling progresses
