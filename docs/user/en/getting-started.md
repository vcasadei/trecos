# Getting started

Trecos records what you own, where it is stored and what it is worth. It works
without internet and keeps everything on your phone.

## Install

Until version 1.0, Trecos is installed from GitHub instead of Google Play
("sideloading").

1. On your phone, open <https://github.com/vcasadei/trecos/releases> and pick the latest release.
2. Download `trecos-<version>-universal.apk` (or the one for your phone's processor, usually `arm64-v8a`).
3. Open the file. Android asks to allow installs from your browser: allow it for this install.
4. Tap **Install**. Later versions install over it the same way and keep your data.

### Check that the download is genuine (optional)

Each release lists two things in its notes: a `SHA256SUMS` file and the
**signing certificate fingerprint**. On a computer with the Android SDK:

```sh
sha256sum -c SHA256SUMS
apksigner verify --print-certs trecos-<version>-universal.apk
```

The first command must print `OK`, and the `SHA-256 digest` from the second
must match the fingerprint in the release notes. Every Trecos release is signed
with the same key; Android refuses an update signed with another one.

## First steps

| Step | How |
|---|---|
| Name your first place | On first launch, confirm "My home" or type another name |
| Add a container | Tap **+**, then **Container**: a room, shelf, box or bag. Containers can go inside containers |
| Add an item | Tap **+** inside a container, then **Item**. Only the name is required |
| Add many items | Use **Save + new**: the next form stays in the same container |
| Add another house | Tap the house name at the top, then **Add house** |
| Switch houses | Tap the house name at the top and pick one |
| Go up | Tap any level of the path, such as "Apartment > Office" |
| Change the list | Tap the view button in the top bar: condensed or detailed. The choice applies everywhere |

## Values

An item's total is quantity × unit price. A container's value adds up
everything inside it, at every depth. You can type a manual value for a
container in its edit form; it then counts toward the containers above it.
**Clear override** goes back to the automatic value. Items without a price
count as zero, and the container tells you how many there are.

## Photos

Each item, container or house can have up to 3 photos. In its form, tap
**Add photo**: the first time, choose Camera or Gallery (tick **Remember my
choice** to skip the question next time). Tap a photo to make it the main one
or remove it, or long-press and drag to reorder. Photos are stored small
(1920 px) and without location data; Trecos never needs camera or storage
permission.
