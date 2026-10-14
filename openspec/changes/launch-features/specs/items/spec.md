# Spec Delta

## MODIFIED Requirements

### Requirement: Form or photo first
The app SHALL let the user choose, in Settings, whether adding an item starts with the form (default) or with taking a photo. In photo-first mode, the image source MUST open immediately. After a photo is chosen, the form opens with it attached, prefilled from the photo as described in item-recognition, and the name field focused. Cancelling the photo MUST still open the form.

#### Scenario: Photo first
- **AS A** user with "Photo first" enabled
- **WHEN** I tap "+" and then "Item"
- **THEN** the camera or gallery opens, and after taking a photo the form opens with it attached and prefilled from it

#### Scenario: Photo cancelled
- **AS A** user in photo-first mode
- **WHEN** I cancel the camera
- **THEN** the form opens with no photo and nothing prefilled
