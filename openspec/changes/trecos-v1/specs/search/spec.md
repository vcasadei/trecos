# Spec Delta

## Purpose

Lets users find anything across all houses by typing or scanning, then narrow
and order the results, from one Search tab that also serves as the full item list.

## ADDED Requirements

### Requirement: Search tab
The Search tab SHALL show a search field, a QR scan button, scope and match
controls, filters, a sort control, and the results with a live count. With an
empty query, it MUST list every item in all houses. Opening the tab MUST NOT
open the keyboard automatically.

#### Scenario: Browsing everything
- **AS A** user with 342 items
- **WHEN** I open the Search tab without typing
- **THEN** all 342 items are listed and the count reads "342 items"
- **AND** the keyboard is not shown until I tap the field

#### Scenario: No results
- **AS A** user
- **WHEN** my query and filters match nothing
- **THEN** the count reads 0, and a message offers to clear the filters

### Requirement: Scope and match fields
Results SHALL be limited by a scope, Items (default), Containers or Both, and by
where to match: Name and description (default), Name only, or Description only.
Matching on "Name" MUST also match brand, model, serial number and QR code.

#### Scenario: Finding by serial number
- **AS A** user
- **WHEN** I type "SN4478" with match set to Name
- **THEN** the item whose serial number is "SN4478X21" is found

#### Scenario: Containers only
- **AS A** user
- **WHEN** I set the scope to Containers and type "box"
- **THEN** only containers are listed

### Requirement: Matching rules
Search SHALL ignore letter case and accents, match each typed word as the
beginning of a word, and require every typed word to match. Results MUST update
while typing, without waiting for a submit action. Search text MUST NOT be
stored or kept as history.

#### Scenario: Accent-insensitive
- **AS A** user
- **WHEN** I type "cabeca"
- **THEN** an item named "Cabeça de impressão" is found

#### Scenario: Partial words
- **AS A** user
- **WHEN** I type "rasp 4"
- **THEN** "Raspberry Pi 4" is found, and "Raspberry Pi 3" is not

### Requirement: Filters
Results SHALL be filterable by house (default: all houses), by categories and by
tags. Choosing several values in one filter MUST match any of them. Using
several filters MUST match all of them. Choosing a top-level category MUST include
all its subcategories. Filters MUST stay applied until cleared with "Clear all",
including across app restarts.

#### Scenario: Any within, all across
- **AS A** user
- **WHEN** I filter categories "SBCs & dev boards" and "USB-C", and tag "borrowed"
- **THEN** results are items tagged "borrowed" that have either of those categories

#### Scenario: Top-level includes subcategories
- **AS A** user
- **WHEN** I filter by "Cables"
- **THEN** items in "Cables > HDMI" and "Cables > USB-C" are included

### Requirement: Sorting
Results SHALL be sortable by name (default, A to Z), date added, or unit price,
each with a direction toggle. Items without a price MUST be listed last in both
price directions.

#### Scenario: Most expensive first
- **AS A** user
- **WHEN** I sort by price, high to low
- **THEN** the most expensive item is first, and items without a price come last

### Requirement: Result location across houses
Each result SHALL show its location path starting with a pill in its house's
colour and name. When the path doesn't fit, the levels between the house pill
and the last level MUST collapse into "…", so the house stays visible.

#### Scenario: Same box name in two houses
- **AS A** user with "Box A" in both "Apartment" and "Parents"
- **WHEN** a search returns a USB-C cable from each
- **THEN** one row reads "(Apartment) > … > Box A" and the other "(Parents) > … > Box A"

### Requirement: Search in this container
The "Search in this container" action on a container SHALL open the Search tab
limited to that container and everything inside it, shown as a removable chip
with the container's name.

#### Scenario: Searching inside a box
- **AS A** user in "Box A" who chooses "Search in this container"
- **WHEN** I type "usb"
- **THEN** only matches inside "Box A" and its sub-containers are listed
- **AND** removing the "Box A" chip widens the search to all houses

### Requirement: Scan from search
The QR button SHALL open the scanner. The scanned code MUST then be handled by
the QR lookup rules in the `qr-codes` capability. Cancelling the scan MUST return
to the Search tab unchanged.

#### Scenario: Cancelling a scan
- **AS A** user who opened the scanner from Search
- **WHEN** I cancel
- **THEN** the Search tab shows the same query, filters and results as before
