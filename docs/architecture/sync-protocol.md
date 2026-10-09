# Sync protocol

How Trecos keeps houses in step across a user's devices through their own
Google Drive (design D14). The code is in `app/src/main/kotlin/app/trecos/sync/`.
Sync is behind the `FEATURE_DRIVE_SYNC` build flag (off by default; build with
`-Ptrecos.driveSync=true`).

## Storage layout

Everything lives in a visible `Trecos` folder in My Drive. With the `drive.file`
scope the app sees only files it created.

```
Trecos/
  houses/<houseId>/commits/<commitId>.jsonl.gz   full snapshots, one per sync
  houses/<houseId>/refs/<deviceId>.json          each device's latest commit
  houses/<houseId>/deleted.json                  present while the house is deleted
  objects/<sha256>.webp                          full-size photos, stored once
```

- **Refs**: `{"commitId", "time", "formatVersion"}`. Only the device named in
  the file writes it, so two devices never race on the same file.
- **Commits**: gzip. The first line is `{"commit": meta}`, and the rest are
  snapshot rows `{"t": table, "r": row}`, sorted by table then key, in the
  backup snapshot format (`SnapshotFormat`). The meta holds:
  - the id and parent ids;
  - the time;
  - the device id and name;
  - the Google user's name and e-mail;
  - the format version;
  - the number of changed rows and of conflicts.
- **Commit ids** are a hash of the parents, the device id and the content, so
  an interrupted sync that is repeated writes the same commit.
- **Photos** are named by their SHA-256 and uploaded only if missing.

## A sync of one house

1. Read the house's rows from the database (trashed rows included).
2. Read every device's ref. If any ref is newer than this app's format, stop
   and ask the user to update.
3. For each other device whose head this device hasn't merged yet, load its
   commit (from the local cache or Drive) and find the **merge base**: the
   nearest commit in both histories, found by walking parents. If there is
   none (for example, it was garbage-collected), use an empty base.
4. **Merge** (`Merge.fold`), three-way and per field, one device at a time:
   - A field changed on one side only takes that side.
   - The same field changed to different values on both sides is a
     **conflict**. The local value stays until the user resolves it.
   - An edit on one side against a delete, or a move to the trash, on the
     other is a conflict too.
   - Rows added on either side are kept. Rows deleted on one side and
     unchanged on the other are deleted.
5. **Repair** links a merge can break. For example, an item added on one
   device into a container deleted on another moves to the top level.
6. **Apply** the result in one transaction, but only if the house didn't change
   on this device since step 1. Otherwise the next sync picks the change up.
7. **Photos**: upload the ones Drive lacks and download the ones this device
   lacks. With "Photos only on Wi-Fi" on a metered network, they wait.
8. If the merged rows differ from this device's previous commit, write a commit
   (parents: the previous commit, then the merged heads), cache it locally, and
   then move this device's ref. The ref moves last, so an interrupted sync never
   points at a missing commit. If nothing changed, no commit is written and the
   history gains no entry.
9. Delete this device's commits beyond the newest 30.

## Conflicts

Conflicts are kept on the device (`SyncState.conflicts`) and shown in Settings >
Sync > Conflicts, with a badge on the Settings row.

- **Keep mine**: removes the conflict. The next sync shares the local value.
- **Keep theirs**: writes the other value into the row, or restores or deletes
  the row for an edit-versus-delete conflict.

The next commit has the other device's head as a parent. That device's next
merge base therefore already includes its own value, and it doesn't see the
same conflict again.

## Restoring onto a device

When an account is connected and Drive has houses the device doesn't have:

- If the device has only the empty first house, the app offers to **restore**
  them, which replaces that empty house.
- If the device has data, the app offers to **merge** (keep both) or to **keep
  only the Drive data**, which deletes the device's houses first.

## Deleted houses

Deleting a house never deletes anything from Drive. The device that deletes it
writes `houses/<houseId>/deleted.json` (`{"time", "deviceId"}`, no house name,
so nothing readable leaks when encryption is on) and forgets its own sync state
for the house. When it is offline, the house waits in `SyncState.pendingDeletes`
and is marked at the next sync. A house that never reached Drive is just dropped
from that list.

At each sync, for every local house with the marker:

| This device | What it does |
|---|---|
| No changes since its last sync, and another house left | Removes the house locally, forgets its sync state and remembers it in `SyncState.droppedHouses` |
| Unsynced changes, the house is its only one, or it never synced the house | Keeps the house, deletes the marker and syncs as usual: an edit wins over a delete |

Houses with the marker are left out of the restore offer when connecting.
Settings > Sync > Deleted houses lists them, with the name read from each
house's newest commit. **Restore** deletes the marker and pulls the house from
its newest commits, as on a new phone. Each device that had removed it sees, at
its next sync, that a house in `droppedHouses` is no longer marked, and pulls it
back the same way.

## Safety

- **Interruptions**: every step can fail and be repeated. The commit id is
  deterministic, writes are idempotent, and the ref moves last.
  `SyncEngineTest.interruptedSync` injects a failure at each remote call in
  turn.
- **A deleted or altered folder**: a missing `Trecos` folder, or a commit that
  can't be read, stops the merge. Nothing is merged from files the app can't
  read, and the user is offered **Upload my data again**.
- **Format**: every commit and ref carries `formatVersion`. A device refuses
  newer formats and must still read the previous one for at least one release.
- **Scheduling**: WorkManager runs a periodic job (every day by default; 5, 15
  or 30 days; or never) with a network constraint and exponential backoff.
  "Sync now" runs on an app-wide scope, so it finishes even if the screen
  closes.
- **Auth**: Google Identity's `AuthorizationClient` with the `drive.file` and
  `drive.appdata` scopes. The app never stores a password or a token; a
  background sync gets a token silently or waits for the user.
- **Device identity**: an app-generated UUID, never a hardware id. The device
  name is editable, and no location is recorded.
