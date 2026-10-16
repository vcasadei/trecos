# trecos.app

The website, published on GitHub Pages by `.github/workflows/pages.yml` on
every push to `master` that touches it.

| Path | Source |
|---|---|
| `/` and `/pt/` | `content/home.en.html` and `content/home.pt-BR.html` |
| `/privacy/` | The repository's `PRIVACY.md` |
| `/pt/privacidade/` | `content/privacidade.md`, a translation kept in step with `PRIVACY.md` |
| `/commercial/` | The repository's `COMMERCIAL.md` |
| `/pt/licenca-comercial/` | `content/licenca-comercial.md`, a translation kept in step with `COMMERCIAL.md` |
| `/delete-data/` and `/pt/excluir-dados/` | `content/delete-data.md` and `content/excluir-dados.md`: how to delete your data, linked from the Play listing |
| Logo and icon | `docs/brand/` |
| Screenshots | The Roborazzi screenshots in `app/src/test/screenshots/`, named in `config.json` |

The donation links (GitHub Sponsors, Buy Me a Coffee, Pix) are set in
`config.json`; each one appears on the home pages only once it is filled in.
For Pix, the build turns the key, name and city into the static "copia e cola"
code (BR Code, no fixed amount, see `pix.mjs`) and draws its QR code.

When `PRIVACY.md` or `COMMERCIAL.md` changes, update the Portuguese page in the
same commit.

## Build locally

```bash
cd site
npm ci --ignore-scripts
npm run build
python3 -m http.server -d _site 8085
```

Then open <http://localhost:8085>.
