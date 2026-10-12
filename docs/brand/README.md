# Brand

The Trecos mark is a cardboard box with a lid and a white label, drawn in flat
kraft tones with a dark brown outline. "Trecos" means "stuff", and the box is
where stuff goes.

| File | Use |
|---|---|
| [trecos-icon.svg](trecos-icon.svg) | App icon: the box with a handwritten "t" on the label, on the app violet (#651FFF). The full 108 × 108 adaptive-icon layer; launchers crop it to a circle or squircle. |
| [trecos-logo.svg](trecos-logo.svg) | Website and Play listing: a larger box with "trecos" handwritten on the label, on a transparent background. |
| [generate.py](generate.py) | Draws both SVGs and the launcher icon vectors. |

The letters are pen strokes, not a font, so the files render the same
everywhere.

## Changing the drawing

Edit `generate.py`, then run:

```bash
python3 docs/brand/generate.py
```

It rewrites the two SVGs and these app resources:

- `app/src/main/res/drawable/ic_launcher_foreground.xml`: the coloured box.
- `app/src/main/res/drawable/ic_launcher_monochrome.xml`: the outline only, which Android 13+ tints for themed icons.

The violet background is `ic_launcher_background` in `values/colors.xml`, and
`mipmap-anydpi/ic_launcher.xml` combines the three layers. Every supported
Android version (9 and later) uses the adaptive icon, so the app ships no PNG
icons.
