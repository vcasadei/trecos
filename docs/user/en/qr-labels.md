# QR labels

Stick a QR label on a box, then scan it to open the box in Trecos.

## Codes

Every item and container can have a QR code: any text up to 256 characters.
When you create one and leave the code empty, Trecos uses its name as the code,
unless something else in the same house already has that code; then it saves
without a code and tells you. Renaming never changes the code, so labels you
already printed keep working.

A code can only be used once per house. If you type one that is taken, the form
names what holds it, with **Open it** to go there. The same code can exist in
different houses.

## Showing, sharing and printing

- On a container, tap the code under its name. On an item, tap **QR code** in
  its details. The label opens in large with the code written underneath.
- **Share** sends the label as an image to any app. **Print** opens the system
  print dialog.
- To print many at once, long-press one row, select the others (or **Select
  all**) and tap the print icon. All labels go into one print job, 12 per page.
  Anything without a code is skipped, and Trecos says how many.
- A container's ⋮ menu also has **Print QR**.

## Scanning

Tap the QR button in Search. Trecos looks the code up in every house:

- One match: its screen opens.
- Matches in several houses: you choose the house.
- No match: you can create an item or container with that code and name, then
  pick where it goes.
- A match in the trash: you can restore it.

Labels from other programs work too: Trecos uses the scanned text exactly, only
trimming spaces around it. Upper and lower case count.

In an item or container form, the scan button in the **QR code** field fills in
the code, and the name too if it is still empty.

Scanning uses Google Play services and needs no camera permission. The first
time, the scanner may need a one-time download, so scan once while online.
Without it, you can still type a code into Search.
