# Spec Delta

## ADDED Requirements

### Requirement: Attribute suggestions from the name
While a user types an item's name or description, and after an item is identified from a photo, the app SHALL suggest attribute values it can read from the text, as chips such as "Brand: Raspberry Pi" or "RAM: 4 GB", that are added only when tapped. Suggestions MUST come from built-in rules in English and Portuguese (known brands, and numbers with units such as GB, TB, mAh, W, V, mm, cm and inches), MUST work offline, and MUST NOT suggest an attribute the item already has a value for. Tapping a chip MUST fill the built-in Brand or Model field when it is one of those, fill a house-wide field with the same name (ignoring case and accents) when there is one, and otherwise add a field to this item only, with the type and unit the rule gives.

#### Scenario: Brand and memory
- **AS A** user typing the name "Raspberry Pi 4 4GB"
- **WHEN** I pause typing
- **THEN** chips suggest "Brand: Raspberry Pi" and "RAM: 4 GB", and nothing is filled until I tap one

#### Scenario: Existing house-wide field
- **AS A** user whose house has a Number field "RAM" with unit "GB"
- **WHEN** I tap the chip "RAM: 4 GB"
- **THEN** that field is filled with 4, and no new field is added

#### Scenario: New one-off field
- **AS A** user whose house has no "Capacity" field
- **WHEN** I tap the chip "Capacity: 10000 mAh" on a power bank
- **THEN** a Number field "Capacity" with unit "mAh" and value 10000 is added to this item only

#### Scenario: Ambiguous number
- **AS A** user typing "Cabo 2m"
- **WHEN** the rules read "2m" as a length
- **THEN** the chip suggests "Length: 2 m", and typing "2 metros" gives the same chip

#### Scenario: Nothing to suggest
- **AS A** user typing "old box"
- **WHEN** no rule matches
- **THEN** no attribute chips are shown

### Requirement: Attribute suggestions learn
When the user accepts an attribute chip and then renames the field it added, or fills a different field with the suggested value, the app SHALL remember that choice for that house and use it for later suggestions of the same kind.

#### Scenario: My own field name
- **AS A** user who accepted "RAM: 4 GB" and renamed the new field to "Memória"
- **WHEN** I later type "Orange Pi 8GB" in the same house
- **THEN** the chip suggests "Memória: 8 GB"
