# Spec Delta

## MODIFIED Requirements

### Requirement: Category suggestions
While a user types an item's name or description, and after an item is identified from a photo, the app SHALL suggest up to three categories not yet assigned, as chips that are added only when tapped. Suggestions MUST work offline, recognise English and Portuguese keywords and synonyms, use the labels and text recognized in the item's photos, and learn from the categories the user assigns in that house. Categories MUST NOT be assigned automatically.

#### Scenario: Keyword match
- **AS A** user typing the name "cabo usb-c para hdmi"
- **WHEN** I pause typing
- **THEN** chips suggest "Cables > USB-C" and "Cables > HDMI"
- **AND** neither is assigned until I tap it

#### Scenario: Learning my vocabulary
- **AS A** user who assigned "Computers > SBCs & dev boards" to several items named with "rpi"
- **WHEN** I type "rpi zero" in a new item
- **THEN** "Computers > SBCs & dev boards" is suggested

#### Scenario: Nothing recognised
- **AS A** user typing "misc thing"
- **WHEN** no keyword or learned word matches
- **THEN** no chips are shown and the form works normally

#### Scenario: Suggested from a photo
- **AS A** user who identified an item from a photo of a cordless drill, with no name typed yet
- **WHEN** the form is prefilled
- **THEN** "Tools > Power tools" is among the suggested chips, not yet assigned
