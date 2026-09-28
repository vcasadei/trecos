# Spec Delta

## Purpose

Collects the user's preferences in one Settings tab with sensible defaults, so
the app adapts to language, currency, habits and taste without extra setup.

## ADDED Requirements

### Requirement: Settings structure and defaults
The Settings tab SHALL group its options into these sections, in order:
General, Appearance, Items & photos, Custom fields, Security, Sync & backup,
Trash, Help, Support Trecos, and About. The defaults MUST be:

| Setting | Options | Default |
|---|---|---|
| Language | English, Português (Brasil) | device language if supported, else English |
| Currency | any ISO currency | the phone's region currency |
| Start on | Home, Search | Home |
| Theme | Follow system, White, Dark | Follow system |
| House colour band | Automatic, Always, Never | Automatic (two or more houses) |
| List view | Condensed, Detailed | Condensed |
| Detailed-view extras | up to 3 fields | Categories |
| New item starts with | Form, Photo | Form |
| Image source | Camera, Gallery, Ask every time | set by the first-use prompt |

Settings MUST apply immediately, without a restart, and are kept on the device
only (they are not synced).

#### Scenario: Fresh install
- **AS A** new user in Brazil with a phone in Portuguese
- **WHEN** I open Settings for the first time
- **THEN** language is Português (Brasil), currency is BRL, start is Home and theme is Follow system

#### Scenario: Immediate effect
- **AS A** user
- **WHEN** I change the theme to Dark
- **THEN** the app turns dark at once

### Requirement: Changing the currency
Changing the currency SHALL relabel every price with the new symbol without
converting the amounts, after a warning that prices won't be converted.
Cancelling the warning MUST keep the previous currency.

#### Scenario: Relabelling
- **AS A** user with currency BRL and an item at R$ 3.500,00
- **WHEN** I switch to USD and accept the warning
- **THEN** the item shows US$ 3.500,00, the same number with a new symbol

#### Scenario: Cancelling
- **AS A** user shown the currency warning
- **WHEN** I cancel
- **THEN** the currency stays BRL

### Requirement: Detailed-view extra fields
Users SHALL choose up to three extra fields for the detailed list view from:
categories, tags, total value, brand, model, serial number, QR code, date added,
last changed, and custom fields. Choosing a fourth MUST be prevented, with an
explanation.

#### Scenario: Picking extras
- **AS A** user
- **WHEN** I choose tags and brand in addition to categories
- **THEN** detailed rows show categories, tags and brand when those have values

#### Scenario: Too many
- **AS A** user with three extras selected
- **WHEN** I try to add a fourth
- **THEN** it cannot be selected and a note says the limit is 3
