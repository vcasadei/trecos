# Spec Delta

## Purpose

Lets users attach a few light, private photos to items, containers and houses,
and view them quickly in lists, carousels and full screen.

## ADDED Requirements

### Requirement: Photo limit and sources
Items, containers and houses SHALL each accept up to 3 photos, taken with the
phone's camera app or picked from the gallery, where several can be picked at
once up to the remaining slots. Adding photos MUST NOT require camera or storage
permission. When 3 photos are attached, the add-photo control MUST be
unavailable and explain the limit.

#### Scenario: Picking from the gallery
- **AS A** user with one photo on an item
- **WHEN** I pick photos from the gallery
- **THEN** I can select at most 2 more

#### Scenario: Limit reached
- **AS A** user with 3 photos on an item
- **WHEN** I look for the add-photo control
- **THEN** it is disabled with a note that 3 photos is the maximum

### Requirement: Image source preference
The first time a user adds a photo, the app SHALL ask "Camera" or "Gallery",
with "Remember my choice" checked by default. If it stays checked, that source
MUST be used from then on without asking. If unchecked, the app MUST keep asking
every time. The choice MUST be changeable in Settings, with the options Camera,
Gallery or Ask every time.

#### Scenario: Remembering
- **AS A** user adding my first photo
- **WHEN** I choose Camera and leave "Remember my choice" checked
- **THEN** the next photo opens the camera directly

#### Scenario: Keep asking
- **AS A** user adding my first photo
- **WHEN** I uncheck "Remember my choice" and choose Gallery
- **THEN** the next photo asks again

### Requirement: Stored size and privacy
Photos SHALL be stored at most 1920 pixels on the longest side, in a compressed
format, with orientation corrected. Location and all other embedded metadata
MUST be removed, and the original file MUST NOT be kept by the app. Photos taken
through the camera app MUST NOT be left in the phone's gallery by Trecos.

#### Scenario: Photo from a 12-megapixel camera
- **AS A** user taking a photo
- **WHEN** it is saved to an item
- **THEN** the stored photo is at most 1920 px on its long side and contains no location data

#### Scenario: Unreadable file
- **AS A** user picking a corrupted image from the gallery
- **WHEN** it cannot be decoded
- **THEN** the app says that photo could not be added, and any other picked photos are added normally

### Requirement: Thumbnails
Lists and carousel previews SHALL show small thumbnails generated on the device.
A missing thumbnail MUST be regenerated from the stored photo without user action.

#### Scenario: Thumbnail regenerated
- **AS A** user whose thumbnails were lost after a restore from sync
- **WHEN** I open a list
- **THEN** thumbnails appear, regenerated from the stored photos

### Requirement: Main photo and order
The first photo SHALL be the main photo, used in lists and shown first in
carousels. The user MUST be able to reorder photos by dragging, choose "Set as
main", and remove a photo.

#### Scenario: Setting the main photo
- **AS A** user with three photos on an item
- **WHEN** I choose "Set as main" on the third
- **THEN** it becomes first, and list rows show it

### Requirement: Viewing photos
Tapping a photo SHALL open it full screen, with pinch-to-zoom and swiping between
that owner's photos. Carousels on item, container and house screens MUST be
landscape and MUST show the main photo first.

#### Scenario: Zooming into a serial label
- **AS A** user viewing a photo full screen
- **WHEN** I pinch to zoom
- **THEN** the photo enlarges up to its stored resolution
