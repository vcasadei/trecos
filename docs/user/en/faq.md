# Frequently asked questions

This file is the source of the in-app FAQ (Settings > Help). Answers are added
as their features ship.

## 2. How do QR labels work?

Each item or container can have a QR code, which starts as its name. Print it,
stick it on, and later tap the QR button in Search and scan it: Trecos opens
the thing, even if you renamed it. Codes are unique within a house. Labels from
other programs work too. See [QR labels](qr-labels.md).

## 3. How do I move things between containers or houses?

Open the item or container, tap ⋮ > **Move**, and pick a destination. To move
to another house, switch houses at the top of the picker first. To move several
things, long-press one row, tap the others, then tap **Move**. Containers move
with everything inside them.

## 4. How do I print QR labels?

Tap the QR code on an item or container, then **Print** (or **Share** to send
the image). To print many, long-press one row, select the others and tap the
print icon: they all go in one print job, 12 labels per page. Things without a
code are skipped.

## 5. How do backup and sync work?

**Backup**: Settings > **Sync & backup** > **Backup** saves one `.zip` file
with your houses and photos wherever you choose. Import it on any phone, adding
its houses as new ones or replacing everything. It needs no account, and the
file is not encrypted. See [Backup](backup.md).

**Sync**: Settings > **Sync & backup** > **Sync** > **Connect Google Drive**
keeps your devices in step through a visible **Trecos** folder in your own
Drive. Trecos only sees files it created. It syncs every day by default (or
every 5, 15 or 30 days, or never), and you can tap **Sync now** at any time.
Photos wait for Wi-Fi unless you turn that off. Changes to different details
merge by themselves. If the same detail changed on two devices, Trecos asks
which to keep. Drive keeps each device's last 30 syncs.

## 6. What happens when I delete something?

It goes to the house's trash for 30 days, and you can tap **Undo** right away.
From the trash (house menu ⋮ > **Trash**) you can restore it or delete it for
good. After 30 days it is removed permanently. Deleting a whole house is
different: it is permanent right away, so export a backup first.

## 7. How do I set up the app lock?

Settings > **Security** > **App lock**. Trecos uses your phone's own lock (fingerprint, face, PIN, pattern or password), so there is no extra password to remember. Your phone needs a screen lock first. Under **Lock after** choose when Trecos asks again: immediately, after 1 minute (default), 5 or 15 minutes in the background. If you later remove the phone's screen lock, Trecos turns its lock off and tells you.

## 9. Do I need an account?

No. Everything except Google Drive sync works without any account, name or e-mail. Sync (when it arrives) uses your own Google account. In Settings you can add an optional name and e-mail to label your sync records; you can change or delete them at any time.

## 10. How do I get my data back after losing or changing phones?

On the new phone, install Trecos and either:

- **With sync**: Settings > **Sync & backup** > **Sync** > **Connect Google
  Drive** with the same Google account. Trecos offers to restore your houses
  from Drive.
- **With a backup file**: Settings > **Sync & backup** > **Backup** > **Import
  a backup**, and pick your `trecos-backup-….zip`.

Without sync or a backup file, data on a lost phone can't be recovered.
