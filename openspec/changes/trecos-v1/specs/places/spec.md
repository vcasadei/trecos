# Spec Delta

## Purpose

Lets users model where things physically are: one or more houses, each holding
containers nested to any depth, with colours, icons, a navigable path and value totals.

## ADDED Requirements

### Requirement: Houses
The app SHALL let users create any number of houses. A house MUST have a name
and MAY have a free-text address, a description, up to three photos, an icon
and a colour. The icon SHALL be chosen from a bundled set of place icons
(apartment, house, cottage, car and others), with a default preselected.
A house's value MUST always be calculated and cannot be overridden.

#### Scenario: Creating a second house
- **AS A** user with one house
- **WHEN** I add a house named "Casa dos meus pais" with an address
- **THEN** the house is saved with the default house icon
- **AND** the Home tab now lists both houses

#### Scenario: House without a name
- **AS A** user creating a house
- **WHEN** I try to save with an empty name
- **THEN** the house is not saved and the name field shows that a name is required

### Requirement: At least one house
The app SHALL always contain at least one house. On first launch it MUST ask
for the first house's name, pre-filled with "My home" (translated). The last
remaining house MUST NOT be deletable.

#### Scenario: First run
- **AS A** new user
- **WHEN** I open the app for the first time
- **THEN** I am asked to name my first place, with "My home" pre-filled
- **AND** after confirming I land inside that house

#### Scenario: Deleting the last house
- **AS A** user with a single house
- **WHEN** I open that house's menu
- **THEN** "Delete" is unavailable, with an explanation that at least one house is required

### Requirement: Home tab entry point
When exactly one house exists, the Home tab SHALL open directly inside it, and
the house name at the top MUST act as a switcher that also offers "Add house".
With two or more houses, the root of the Home tab MUST be a house list: each
house with its main photo or icon, its colour, its item count and its value,
plus "Add house". Tapping a house SHALL open its top level, and back MUST
return to the list. On app start it SHALL open the top level of the last-used
house above that list, so back from it shows the list. The house band MUST NOT
show on the house list, since no house is open there.

#### Scenario: Single house
- **AS A** user with one house
- **WHEN** I open the Home tab
- **THEN** I see that house's containers and items directly, not a house list

#### Scenario: Several houses
- **AS A** user with two houses who last used "Apartment"
- **WHEN** I open the app
- **THEN** the Home tab shows the top level of "Apartment"
- **AND** pressing back shows the list of both houses

#### Scenario: Opening a house from the list
- **AS A** user with two houses on the house list
- **WHEN** I tap "Parents' house"
- **THEN** I see the top level of "Parents' house"
- **AND** it becomes the last-used house

### Requirement: Nested containers
Containers SHALL nest to any depth inside a house, and items MAY be placed in
any container, including at the house's top level. A container MUST have a name
and MAY have a description, a QR code, up to three photos, an icon and a colour.
Items MUST NOT contain other items.

#### Scenario: Box inside a box
- **AS A** user inside "Office > Box A"
- **WHEN** I add a container named "Cables bag"
- **THEN** it appears under the "Containers" section of "Box A"

#### Scenario: Container without a name
- **AS A** user creating a container
- **WHEN** I try to save with an empty name
- **THEN** the container is not saved and the name field shows that a name is required

### Requirement: Container screen
A container screen SHALL show, in order: the top bar (back arrow, name, edit
action, overflow menu), a scrolling header (photo carousel with the main photo
first, or the container's icon when there are no photos; the description
limited to two lines, with tap to expand; the location path; the QR code; the
value), then a "Containers (n)" section and an "Items (n)" section. An empty
section MUST be hidden. The header MUST scroll away while the top bar stays
fixed. The overflow menu MUST offer Move, Copy, Duplicate, Delete, Search in
this container, and Print QR.

#### Scenario: Container with contents
- **AS A** user opening a container with 2 sub-containers and 12 items
- **WHEN** the screen loads
- **THEN** "Containers (2)" is listed above "Items (12)"

#### Scenario: Empty container
- **AS A** user opening a container with nothing inside
- **WHEN** the screen loads
- **THEN** neither section header is shown and a hint explains how to add something

### Requirement: Add button
Container and house screens SHALL show a floating "+" button at the bottom
right. Tapping it MUST reveal two labelled options, "Item" and "Container",
floating to the left of the button. The chosen object is created inside the
current container or house.

#### Scenario: Adding from a container
- **AS A** user inside "Box A"
- **WHEN** I tap "+" and then "Item"
- **THEN** the new-item form opens with "Box A" as its location

#### Scenario: Dismissing the options
- **AS A** user who opened the "+" options
- **WHEN** I tap outside them or press back
- **THEN** the options close and nothing is created

### Requirement: Location path
Every item and container screen SHALL show its full location path starting with
the house, with levels separated by ">". Each level MUST be tappable and open
that level. When the path doesn't fit on one line, the levels before the last two MUST
collapse into a single "…", house included, because the house is already shown
by its colour band. Tapping "…" MUST show the full path.

#### Scenario: Jumping up the hierarchy
- **AS A** user viewing "Apartment > Office > Box A > Cables bag"
- **WHEN** I tap "Office"
- **THEN** the Office container screen opens

#### Scenario: Deep path on a small screen
- **AS A** user viewing an item eight levels deep
- **WHEN** the path doesn't fit
- **THEN** it shows "… > Box A > Cables bag"
- **AND** tapping "…" reveals every level, starting with the house

### Requirement: Container value
A container's value SHALL be the sum of quantity times unit price of every item
below it at every depth, unless the container has a manual override. An
override MUST replace that container's value and count toward every ancestor's
total. The value MUST be labelled as automatic or manual, and a manual value
MUST offer "Clear override". Items without a price count as zero, and the
container MUST show how many items below it have no price.

#### Scenario: Override counts upward
- **AS A** user whose "Office" holds a 3,500 laptop, "Box A" worth 220, and "Box B" with unpriced items overridden to 500
- **WHEN** I view "Office"
- **THEN** its value is 4,220, labelled automatic
- **AND** "Box B" shows 500, labelled manual

#### Scenario: Unpriced items
- **AS A** user viewing a container where 3 items have no price
- **WHEN** the value is shown
- **THEN** a hint reads that 3 items have no price

### Requirement: Colours
Users SHALL be able to give a container or house one colour from a fixed palette
of about 12 pastel colours, each with contrast-checked variants for the White
and Dark themes. A container's colour MUST tint its top bar and header and
appear as a stripe on its list row. A sub-container without its own colour
MUST inherit the nearest ancestor's colour. Colour MUST never be the only way
a place is identified; names and icons remain visible.

#### Scenario: Inherited colour
- **AS A** user who set "Office" to blue
- **WHEN** I open "Box A" inside Office, which has no colour of its own
- **THEN** Box A's top bar and list stripe are blue

#### Scenario: Own colour wins
- **AS A** user who set "Box B" inside a blue Office to orange
- **WHEN** I view Office's list
- **THEN** Box A shows a blue stripe and Box B an orange stripe

### Requirement: House indicator
When two or more houses exist, a band in the current house's colour SHALL be
shown behind the status bar on every screen, and whenever a location path shows
its house segment, that segment MUST be a pill with the house's name in its colour. With a single
house the band MUST be hidden, unless a setting forces it on. The setting can
also force it off.

#### Scenario: Two houses
- **AS A** user with houses "Apartment" (green) and "Parents" (pink)
- **WHEN** I browse inside "Parents"
- **THEN** a pink band is shown behind the status bar and paths start with a pink "Parents" pill

#### Scenario: Single house
- **AS A** user with one house and the setting on its default
- **WHEN** I browse the app
- **THEN** no status bar band is shown

### Requirement: Icons for places without photos
A house or container with no photos SHALL show its chosen icon wherever a photo
would appear, including list rows and the header. The icon SHALL be chosen from
a bundled set (for example box, shelf, drawer, wardrobe, bag), with a default
preselected.

#### Scenario: Container without photos
- **AS A** user who created "Drawer 2" with the drawer icon and no photo
- **WHEN** it appears in a list
- **THEN** its row shows the drawer icon instead of a thumbnail
