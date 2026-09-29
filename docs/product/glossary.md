# Glossary

| Term | Meaning |
|---|---|
| **House** | A special top-level place (an apartment, a parents' house, a car). It has a name, optional address, description, photos, icon and colour. Every other record belongs to exactly one house, which is also the future unit of sharing and sync |
| **Container** | A storage place inside a house: a room, a shelf, a box, a bag. Containers nest to any depth |
| **Item** | A thing you own. It sits in one container and never contains other items |
| **Location path** | The chain of containers from the house down to an item or container, shown as a tappable breadcrumb (`… > Box A > Cables bag`) |
| **Quantity** | A whole number of identical units of an item (0 to 999,999, default 1) |
| **Unit price** | The estimated value of one unit. Optional |
| **Total value** | Quantity × unit price. Calculated, never entered |
| **Container value** | The recursive sum of every item below a container, unless the user sets a manual **override**, which then counts upward |
| **QR code** | A per-house unique text code on an item or container. Defaults to the name when created and stays the same after renames. Can come from labels printed by other programs |
| **Category** | A two-level classification (top level and subcategory). Built-in categories are global and translated; custom ones belong to a house. An item can have several; the first is the main one |
| **Tag** | A free-text label the user invents (borrowed, broken…), per house |
| **Custom field** | A user-defined field (text, number with a unit label, date, yes/no) for items |
| **Trash** | A per-house holding area where deleted records stay for 30 days before being removed |
| **Band** | The strip in the house's colour behind the status bar, shown when there are two or more houses |
| **App lock** | An optional lock using the phone's own biometrics or PIN. There is no Trecos password |
| **Profile** | Optional name and e-mail, used only to label sync history |
| **Sync** | Optional copy of each house to the user's Google Drive, merged per field like git, with a conflict screen only for real conflicts |
| **Commit** | One sync snapshot of a house, recorded with device, user, time and a change summary |
| **Export** | A `.zip` backup of chosen houses, in the same format as sync |
