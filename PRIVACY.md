# Privacy policy

> **Draft.** This policy is completed before Trecos is published on Google
> Play (release 1.0). Until then it describes the planned behaviour.

Trecos is a home inventory app that works offline. It has **no server**, no
ads, no analytics and no crash-reporting SDK. The developer never receives
your data.

## What the app stores

| Data | Where | Required |
|---|---|---|
| Houses, containers, items, values, serial numbers, tags, custom fields | On your phone | Only what you enter |
| House addresses | On your phone | No |
| Photos (location data removed) | On your phone | No |
| Profile: name and e-mail | On your phone | No |
| Settings | On your phone | - |

## Google services

| Feature | What is shared, and with whom | When |
|---|---|---|
| Google Drive sync | Your inventory and photos, copied to **your own** Google Drive (a visible `Trecos` folder; the encryption key, if used, in the hidden app folder) | Only if you connect a Google account and turn sync on |
| QR scanning | Camera images are processed on the phone by Google Play services' code scanner | When you scan |
| Tips | Purchases go through Google Play Billing | Only if you tip |
| Rating | Google Play's in-app review | At most once, if you choose to rate |

Trecos requests access only to the Drive files it creates (`drive.file`) and its
hidden app folder (`drive.appdata`).

### What sync puts in your Drive

When you connect sync, a visible **Trecos** folder in your My Drive holds:

- every house's data, including what is in its trash, as one file per sync;
- one small file per device, naming its latest sync;
- your full-size photos, each stored once.

Each sync record also carries your Google account's name and e-mail, the
device's name (which you can edit) and an app-generated device id. It never
records a hardware id or your location.

Drive keeps each device's last **30 syncs**; older ones are deleted
automatically. Disconnecting stops sync and keeps everything on your phone. To
remove the data from Drive, delete the Trecos folder in Drive.

## What Trecos does not do

- No location collection.
- No tracking, profiling or selling of data.
- No camera or storage permission: photos come from the system camera or the
  Android photo picker.

## Your rights (LGPD)

All data is under your control on your device: edit or delete it in the app,
export it, or uninstall the app to remove it. Data in your Google Drive is
deleted from Drive itself. Questions: open an issue at
<https://github.com/vcasadei/trecos/issues>.

## Changes

Changes to this policy are recorded in this file's history and in `CHANGELOG.md`.
