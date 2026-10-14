# Spec Delta

## MODIFIED Requirements

### Requirement: Scanning
Scanning SHALL read QR codes only, with a scanner included in the app that works with no network connection and on phones without Google Play services, and use the scanned text exactly, whatever program created it. Surrounding whitespace MUST be trimmed, and matching MUST be exact and case-sensitive. The app MUST ask for the camera permission only when the user first starts a scan, explaining that the camera is used only to read codes; nothing else in the app asks for it. If the permission is refused, scanning MUST explain how to allow it and offer typing the code instead.

#### Scenario: Label from another program
- **AS A** user scanning a QR code printed by a label maker, containing "XYZ-0042"
- **WHEN** the scan completes
- **THEN** the app looks up "XYZ-0042"

#### Scenario: First scan
- **AS A** user who has never scanned
- **WHEN** I tap the scan button
- **THEN** the app asks for the camera permission with a one-line reason, and the scanner opens once I allow it

#### Scenario: Permission refused
- **AS A** user who refused the camera permission
- **WHEN** I tap the scan button
- **THEN** the app explains that scanning needs the camera, offers to open the app's settings, and lets me type the code into search instead

#### Scenario: Offline phone without Google Play services
- **AS A** user on a phone without Google Play services, in airplane mode
- **WHEN** I scan a Trecos label
- **THEN** the item it belongs to opens
