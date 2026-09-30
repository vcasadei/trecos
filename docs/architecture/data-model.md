# Data model

Trecos stores everything in one Room database, `trecos.db`, in app-private
storage. It always runs on SQLCipher's SQLite build, without a key until
encryption is turned on (design D3). The exported schema of every version is
committed in `app/schemas/app.trecos.data.TrecosDatabase/<version>.json`.

## Conventions

| Rule | Why |
|---|---|
| Primary keys are random UUIDs stored as TEXT | Records created on different devices never collide when synced (D14) |
| Every row has `houseId` | The house is the unit of sync and future sharing |
| Every row has `createdAt`, `updatedAt` and `deletedAt` (epoch ms) | Change tracking for sync; deletion is a marker, not a row removal |
| Queries never return rows with `deletedAt` set | Deleted rows stay for sync and the trash (0.4) |
| Money is a `Long` in minor units of the display currency | No floating-point rounding; the currency only labels amounts |
| No foreign keys | Soft deletes and sync merges apply rows in any order; relations are enforced in code |
| Inserts fail on a clash; updates are explicit | Room's upsert silently drops a row whose unique QR code clashes |

## Tables (schema version 1)

### `house`

| Column | Type | Notes |
|---|---|---|
| `id` | TEXT PK | UUID |
| `name` | TEXT | Required |
| `address` | TEXT? | Free text |
| `description` | TEXT? | |
| `icon` | TEXT | Place icon key (`house`, `apartment`, …) |
| `colorKey` | TEXT? | Palette colour key (`rose`, `sky`, …) |
| `createdAt`, `updatedAt`, `deletedAt` | INTEGER | `deletedAt` null while the house exists |

### `container`

| Column | Type | Notes |
|---|---|---|
| `id` | TEXT PK | UUID |
| `houseId` | TEXT | Its house |
| `parentId` | TEXT? | Parent container; null at the house's top level |
| `name` | TEXT | Required |
| `description` | TEXT? | |
| `qrCode` | TEXT? | Unique in the house across containers and items |
| `icon` | TEXT | Container icon key (`box`, `drawer`, …) |
| `colorKey` | TEXT? | Own colour; null inherits the nearest ancestor's |
| `valueOverride` | INTEGER? | Manual value in minor units; null means automatic |
| `createdAt`, `updatedAt`, `deletedAt` | INTEGER | |

Indexes: `(houseId, parentId)`; unique `(houseId, qrCode)`.

### `item`

| Column | Type | Notes |
|---|---|---|
| `id` | TEXT PK | UUID |
| `houseId` | TEXT | Its house |
| `containerId` | TEXT? | Its container; null at the house's top level |
| `name` | TEXT | Required |
| `quantity` | INTEGER | Whole number, 0 to 999,999; default 1 |
| `unitPrice` | INTEGER? | Minor units; null when unknown (no total, not zero) |
| `brand`, `model`, `serial`, `description` | TEXT? | |
| `qrCode` | TEXT? | Unique in the house across containers and items |
| `createdAt` | INTEGER | Shown as "Added" |
| `updatedAt` | INTEGER | Shown as "Last changed"; set on every change, moves included |
| `deletedAt` | INTEGER? | |

Indexes: `(houseId, containerId)`; unique `(houseId, qrCode)`.

## Rules enforced in code

| Rule | Where |
|---|---|
| QR codes unique per house across both tables | `QrDao.countUses`, checked by the container and item forms; each table's unique index is the backstop |
| At least one house exists | The house menu refuses to delete the last one |
| Container value = override, or the recursive sum of quantity × unit price below | `PlaceTree` (design D5), folded in memory per house |
| Colour inheritance: own colour, else the nearest ancestor's | `PlaceTree.colorKey` |
| Corrupt parent cycles never hang the app | `PlaceTree` stops at the first repeated container |

## Device preferences

Not in the database and never synced (DataStore, design D13):

| Key | Values | Default |
|---|---|---|
| `last_house_id` | a house id | the first house |
| `list_view` | `Condensed`, `Detailed` | `Condensed` |
| `house_band` | `Automatic`, `Always`, `Never` | `Automatic` (two or more houses) |
| `currency` | ISO 4217 code | the phone region's currency |

## Migrations

### Safety copy and the "down" path

Android won't install an older app over a newer one, so rollback is
data-level (`MigrationGuard`):

| When | What happens |
|---|---|
| Opening a database older than the app's schema | The file is copied to `pre-migration-v<N>.db` first |
| The migration fails | The copy is put back, the failure is logged (no personal data), and `MigrationFailedException` is raised |
| The next launch that opens without migrating | Every `pre-migration-v*.db` is deleted |

### Expand and contract

A rename or removal is never done in one release, because devices on
adjacent versions may sync the same house:

1. **Expand** (release N): add the new column or table; write both the old and
   the new shape; read the new one, falling back to the old.
2. **Contract** (release N+1 or later): stop reading and writing the old shape,
   then drop it in a migration.

Adding a nullable column or a new table needs only step 1.

### Checklist for every schema change

1. Bump `version` in `@Database` and `TrecosDatabase.VERSION` together.
2. Write the `Migration` (or an `AutoMigration` when Room can infer it).
3. Build once, so Room exports `app/schemas/.../<version>.json`, and commit it.
4. Add a test in `MigrationTest`: create the previous version with
   `MigrationTestHelper`, insert rows, run the migration, validate against the
   new schema and check the rows.
5. If the change renames or removes something, follow expand and contract.
6. Bump the sync `formatVersion` if the snapshot shape changes (design D14).
7. Update the tables on this page.
