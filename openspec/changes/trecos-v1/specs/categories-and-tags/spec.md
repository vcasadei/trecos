# Spec Delta

## Purpose

Gives items a shared classification (two-level categories, several per item,
with offline suggestions) and a personal one (free-form tags for the user's own system).

## ADDED Requirements

### Requirement: Built-in categories
The app SHALL ship a built-in, two-level category tree, translated into both
app languages, with an icon for every category. It MUST contain at least these
top-level categories and subcategories:

- Computers: Desktops, Laptops, SBCs & dev boards, Components, Peripherals
- Storage: Pendrives, SD & microSD cards, SSDs, HDDs, Enclosures & docks
- Cables: USB-A, USB-B, USB-C, Micro-USB, Mini-USB, Lightning, HDMI,
  Mini/Micro HDMI, DisplayPort, Mini DisplayPort, VGA, DVI, Ethernet,
  Serial/UART, Audio, SD/microSD, Power cords
- Adapters: the same connector subcategories as Cables (except Power cords),
  plus USB hubs & docks
- Power: Chargers, Power supplies, Plug adapters, Power strips & extensions,
  Batteries, Power banks
- Electrical: Wire nuts, Wago & lever connectors, Heat shrink, Terminals &
  crimps, Plugs (male/female), Outlets & sockets, Switches, Wire & cable,
  Electrical tape, Breakers & fuses
- Electronics parts: Components, Modules & sensors, Prototyping, Soldering supplies
- Networking: Routers, Switches, Access points & mesh, Modems
- Smart home: Smart plugs, Smart bulbs, Sensors, Hubs & bridges, Cameras
- Lighting: Lamps, Light bulbs, Flashlights, LED strips
- 3D printing: Filaments, Nozzles, Replacement parts, Tools, Cleaning &
  maintenance, Build plates
- Devices: Phones & tablets, Audio & video, Games & consoles, Cameras, Wearables
- Tools: Hand tools, Power tools, Measuring, Hardware, Safety & PPE
- Home: Kitchen, Appliances, Furniture, Decor, Bedding & towels, Cleaning,
  Household supplies
- Personal: Clothing, Shoes, Bags, Jewelry & watches
- Documents: Manuals, Warranties, Personal documents
- Hobbies: Books, Music instruments, Sports, Crafts, Collectibles, Toys
- Outdoor: Garden, Camping, Car & bike
- Health: Medicine, First aid
- Other

Built-in categories MUST NOT be renamed or deleted by the user.

#### Scenario: Built-ins in Portuguese
- **AS A** user with the app in Portuguese
- **WHEN** I open the category picker
- **THEN** built-in categories are shown in Portuguese, for example "Cabos > USB-C"

#### Scenario: Trying to delete a built-in
- **AS A** user viewing a built-in category
- **WHEN** I look for rename or delete actions
- **THEN** neither is offered

### Requirement: Custom categories
Users SHALL be able to create custom categories, either as a new top level or
as a subcategory under any top-level category. A custom category belongs to the
house it was created in. It MUST have a name, and its icon SHALL be chosen from
the bundled icon set, with an empty default icon. Custom categories MUST be
shown exactly as typed, in every language. Deleting a custom category MUST
remove it from every item that has it, and those items keep their other
categories.

#### Scenario: New subcategory
- **AS A** user
- **WHEN** I create "Keyboards (mechanical)" under "Computers"
- **THEN** it appears under Computers in that house's picker, with the empty icon

#### Scenario: Deleting a used custom category
- **AS A** user whose custom category is assigned to 5 items
- **WHEN** I delete it and confirm
- **THEN** the 5 items no longer have that category and keep their others

#### Scenario: Other houses don't see it
- **AS A** user who created a custom category in "Apartment"
- **WHEN** I open the category picker while in "Parents"
- **THEN** that custom category is not listed

### Requirement: Several categories per item
An item SHALL accept one or more categories, in order. The first category MUST
be the main one: its icon represents the item where a single icon is shown. The
user MUST be able to make any assigned category the main one. The category
picker MUST include a search box.

#### Scenario: Cable with two ends
- **AS A** user adding a USB-C to USB-A cable
- **WHEN** I pick "Cables > USB-C" and then "Cables > USB-A"
- **THEN** the item has both categories, and "Cables > USB-C" is the main one

#### Scenario: Changing the main category
- **AS A** user editing that cable
- **WHEN** I set "Cables > USB-A" as main
- **THEN** list rows show the USB-A category's icon for it

### Requirement: Category suggestions
While a user types an item's name or description, the app SHALL suggest up to
three categories not yet assigned, as chips that are added only when tapped.
Suggestions MUST work offline, recognise English and Portuguese keywords and
synonyms, and learn from the categories the user assigns in that house.
Categories MUST NOT be assigned automatically.

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

### Requirement: Copy categories to a new house
When creating a house, the app SHALL offer to copy the custom categories, tags
and custom field definitions from an existing house. Declining MUST create the
house with only the built-in categories.

#### Scenario: Copying from another house
- **AS A** user creating "Beach house"
- **WHEN** I choose to copy from "Apartment"
- **THEN** Apartment's custom categories, tags and field definitions exist in Beach house, and none of its items do

### Requirement: Tags
Users SHALL be able to add any number of free-text tags to an item, for their
own system (for example "borrowed" or "broken"). Tags belong to the house.
While typing a tag, existing tags MUST be offered for completion, and tags that
differ only in letter case or accents MUST be treated as the same tag. Renaming
or deleting a tag MUST apply to every item that has it.

#### Scenario: Reusing a tag
- **AS A** user who already has the tag "borrowed"
- **WHEN** I type "Borr" on another item
- **THEN** "borrowed" is offered, and choosing it adds the existing tag

#### Scenario: Same tag, different case
- **AS A** user typing "Borrowed" on an item
- **WHEN** a tag "borrowed" already exists
- **THEN** the existing tag is used and no duplicate is created
