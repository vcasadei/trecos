# Spec Delta

## Purpose

Identifying an item from a photo on the phone, by what it looks like and the text on its packaging, so the item form starts filled in.

## ADDED Requirements

### Requirement: Recognition runs on the phone
Recognition SHALL run entirely on the phone with models included in the app. It MUST work with no network connection from the first launch, on phones without Google Play services, and MUST NOT send photos, labels or recognized text anywhere (the engine's usage metrics are covered by help-and-support "No tracking").

#### Scenario: First use in airplane mode
- **AS A** user who installed Trecos and never went online
- **WHEN** I identify an item from a photo
- **THEN** the form is prefilled as it would be online

#### Scenario: Phone without Google Play services
- **AS A** user on a phone without Google Play services
- **WHEN** I identify an item from a photo
- **THEN** recognition works and no message about Google Play appears

### Requirement: Prefilling the item form
When the user identifies an item from a photo, the app SHALL read the photo's labels and text and prefill only empty form fields: a suggested name, up to three suggested categories (shown as suggestions, as today), suggested tags, the recognized text appended to an empty description, and attribute suggestions (see custom-fields). Every prefilled value MUST be editable, MUST be visibly marked as suggested until the user edits or accepts it, and nothing MUST be saved until the user saves the form. Values the user already typed MUST NOT be overwritten.

#### Scenario: Photo of a packaged product
- **AS A** user adding an item in photo-first mode
- **WHEN** I photograph a box printed "Raspberry Pi 4 Model B 4GB"
- **THEN** the form opens with the name "Raspberry Pi 4 Model B", the category "SBCs & dev boards" among the suggestions, and Brand "Raspberry Pi" and RAM "4 GB" suggested

#### Scenario: Name already typed
- **AS A** user who typed "My drill" in the name field
- **WHEN** I tap Identify and pick a photo of a drill
- **THEN** the name stays "My drill" and only the empty fields are prefilled

#### Scenario: Nothing recognized
- **AS A** user photographing a plain object with no text
- **WHEN** neither labels nor text reach the confidence threshold
- **THEN** the photo is attached, no field is prefilled, and a short note says nothing was recognized

### Requirement: Identify from the form
The item form SHALL offer an "Identify" action that takes or picks a photo with the same sources as adding a photo (camera app or gallery), attaches it like any other photo, and prefills the form from it. It MUST be unavailable when the item already has 3 photos, with the same explanation as the add-photo control.

#### Scenario: Identify on an existing item
- **AS A** user editing an item with one photo
- **WHEN** I tap Identify and take a photo
- **THEN** the new photo is added as the second photo and empty fields are prefilled from it

#### Scenario: Photo cancelled
- **AS A** user who taps Identify
- **WHEN** I cancel the camera
- **THEN** the form is unchanged

### Requirement: Recognition in both languages
Labels SHALL be shown, suggested and searchable in the app's language (English or Portuguese), and text recognition MUST read Latin-script packaging in both languages, including accented letters.

#### Scenario: Portuguese app
- **AS A** user with the app in Portuguese
- **WHEN** I identify a photo of a drill
- **THEN** the suggested name and tags are in Portuguese, such as "Furadeira"

#### Scenario: Accented packaging
- **AS A** user photographing a box printed "Café Torrado"
- **WHEN** the text is recognized
- **THEN** the description keeps the accents exactly

### Requirement: Recognition stays responsive on low-end phones
Recognizing one photo SHALL take 3 seconds or less on the reference emulator (Android 9, 2 GB) and MUST show progress while it runs. The models MUST load only when recognition is first used, so cold start is unaffected.

#### Scenario: Slow phone
- **AS A** user on a 2 GB phone
- **WHEN** I identify a photo
- **THEN** the form shows a progress indicator and is prefilled within 3 seconds, and I can keep typing meanwhile

#### Scenario: Cold start
- **AS A** the release benchmark
- **WHEN** it cold-starts the app
- **THEN** no recognition model is loaded
