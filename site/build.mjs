// Builds the trecos.app website into site/_site.
//
// The home pages are HTML fragments in content/; the privacy policy and the
// commercial-license page are rendered from the repository's PRIVACY.md and
// COMMERCIAL.md (English) and from content/*.md (Portuguese), so the site never
// drifts from the documents in the repository.
import { cpSync, mkdirSync, readFileSync, rmSync, writeFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import { marked } from "marked";
import QRCode from "qrcode";
import { pixPayload } from "./pix.mjs";

const SITE = dirname(fileURLToPath(import.meta.url));
const REPO = join(SITE, "..");
const OUT = join(SITE, "_site");
const config = JSON.parse(readFileSync(join(SITE, "config.json"), "utf8"));

const read = (path) => readFileSync(path, "utf8");

const STRINGS = {
  en: {
    home: "/", privacy: "/privacy/", commercial: "/commercial/",
    privacyLabel: "Privacy", commercialLabel: "Commercial license", sourceLabel: "Source code",
    other: { lang: "pt-BR", label: "Português" },
    footer: "Free for noncommercial use under the PolyForm Noncommercial License 1.0.0.",
    contact: "Contact",
    copied: "Copied", copyKey: "Copy key", copyCode: "Copy Pix code",
    pixKey: "Key", pixQr: "Pix QR code: scan it with your bank app",
  },
  "pt-BR": {
    home: "/pt/", privacy: "/pt/privacidade/", commercial: "/pt/licenca-comercial/",
    privacyLabel: "Privacidade", commercialLabel: "Licença comercial", sourceLabel: "Código-fonte",
    other: { lang: "en", label: "English" },
    footer: "Gratuito para uso não comercial sob a PolyForm Noncommercial License 1.0.0.",
    contact: "Contato",
    copied: "Copiado", copyKey: "Copiar chave", copyCode: "Copiar Pix copia e cola",
    pixKey: "Chave", pixQr: "QR code do Pix: escaneie com o app do seu banco",
  },
};

// Each page and its counterpart in the other language.
const PAGES = [
  { out: "index.html", lang: "en", twin: "pt/index.html", kind: "home", body: () => home("en") },
  { out: "pt/index.html", lang: "pt-BR", twin: "index.html", kind: "home", body: () => home("pt-BR") },
  { out: "privacy/index.html", lang: "en", twin: "pt/privacidade/index.html", kind: "doc", body: () => doc(read(join(REPO, "PRIVACY.md"))) },
  { out: "pt/privacidade/index.html", lang: "pt-BR", twin: "privacy/index.html", kind: "doc", body: () => doc(read(join(SITE, "content/privacidade.md"))) },
  { out: "commercial/index.html", lang: "en", twin: "pt/licenca-comercial/index.html", kind: "doc", body: () => doc(read(join(REPO, "COMMERCIAL.md"))) },
  { out: "pt/licenca-comercial/index.html", lang: "pt-BR", twin: "commercial/index.html", kind: "doc", body: () => doc(read(join(SITE, "content/licenca-comercial.md"))) },
  { out: "delete-data/index.html", lang: "en", twin: "pt/excluir-dados/index.html", kind: "doc", body: () => doc(read(join(SITE, "content/delete-data.md"))) },
  { out: "pt/excluir-dados/index.html", lang: "pt-BR", twin: "delete-data/index.html", kind: "doc", body: () => doc(read(join(SITE, "content/excluir-dados.md"))) },
  { out: "testers/index.html", lang: "en", twin: "pt/testar/index.html", kind: "doc", body: () => doc(read(join(SITE, "content/testers.md"))) },
  { out: "pt/testar/index.html", lang: "pt-BR", twin: "testers/index.html", kind: "doc", body: () => doc(read(join(SITE, "content/testar.md"))) },
  { out: "404.html", lang: "en", twin: "pt/index.html", kind: "doc", body: () => doc(read(join(SITE, "content/404.md"))) },
];

const url = (out) => "/" + out.replace(/index\.html$/, "");

function escape(text) {
  return text.replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;").replace(/"/g, "&quot;");
}

// Markdown page: the first heading becomes the page title.
function doc(markdown) {
  const title = markdown.match(/^# (.+)$/m)[1];
  return { title, html: `<article class="doc">${marked.parse(markdown)}</article>` };
}

// {{shot name|alt}} becomes the light and dark screenshot pair; CSS shows the
// one matching the theme switch above the screenshots.
const shot = (name, alt) =>
  `<span class="shot"><img class="shot-light" src="/assets/screens/${name}.png" alt="${alt}" width="360" height="800" loading="lazy">` +
  `<img class="shot-dark" src="/assets/screens/${name}-dark.png" alt="${alt}" width="360" height="800" loading="lazy"></span>`;

async function home(lang) {
  const html = read(join(SITE, `content/home.${lang}.html`))
    .replace("{{support}}", await support(lang))
    .replace(/\{\{shot ([a-z-]+)\|([^}]+)\}\}/g, (_, name, alt) => shot(name, alt));
  return { title: null, html };
}

// The donation links, each shown only once config.json names it.
async function support(lang) {
  const t = STRINGS[lang];
  const { githubSponsors, buyMeACoffee, pix } = config.donate;
  const cards = [];
  if (githubSponsors) {
    cards.push(`<a class="give" href="https://github.com/sponsors/${escape(githubSponsors)}"><strong>GitHub Sponsors</strong><span>github.com/sponsors/${escape(githubSponsors)}</span></a>`);
  }
  if (buyMeACoffee) {
    cards.push(`<a class="give" href="https://buymeacoffee.com/${escape(buyMeACoffee)}"><strong>Buy Me a Coffee</strong><span>buymeacoffee.com/${escape(buyMeACoffee)}</span></a>`);
  }
  if (pix?.key) {
    const code = pixPayload(pix);
    const qr = await QRCode.toString(code, { type: "svg", errorCorrectionLevel: "M", margin: 2, color: { dark: "#1C1828", light: "#FFFFFF" } });
    cards.push(`<div class="give pix"><strong>Pix</strong>` +
      `<div class="qr" role="img" aria-label="${t.pixQr}">${qr}</div>` +
      `<span>${t.pixKey}: <code id="pix-key">${escape(pix.key)}</code></span>` +
      `<button type="button" class="copy" data-copy="pix-key" data-done="${t.copied}">${t.copyKey}</button>` +
      `<code id="pix-code" hidden>${escape(code)}</code>` +
      `<button type="button" class="copy" data-copy="pix-code" data-done="${t.copied}">${t.copyCode}</button></div>`);
  }
  return cards.length ? `<div class="gives">${cards.join("")}</div>` : "";
}

function layout(page, { title, html }) {
  const t = STRINGS[page.lang];
  const name = "Trecos";
  const fullTitle = title ? `${title} · ${name}` : `${name}: ${page.lang === "en" ? "Home Inventory" : "Inventário da Casa"}`;
  const description = page.lang === "en"
    ? "Trecos is a free, offline Android app that records what you own, where it is stored and what it is worth. No ads, no account, no server."
    : "Trecos é um app Android gratuito e offline que registra o que você tem, onde está guardado e quanto vale. Sem anúncios, sem conta, sem servidor.";
  return `<!doctype html>
<html lang="${page.lang}">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>${escape(fullTitle)}</title>
<meta name="description" content="${escape(description)}">
<link rel="canonical" href="https://trecos.app${url(page.out)}">
<link rel="alternate" hreflang="${t.other.lang}" href="https://trecos.app${url(page.twin)}">
<link rel="icon" href="/assets/trecos-icon.svg" type="image/svg+xml">
<meta property="og:title" content="${escape(fullTitle)}">
<meta property="og:description" content="${escape(description)}">
<meta property="og:image" content="https://trecos.app/assets/trecos-logo.png">
<meta name="theme-color" content="#651FFF">
<link rel="preconnect" href="https://fonts.googleapis.com">
<link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
<link rel="stylesheet" href="https://fonts.googleapis.com/css2?family=Atkinson+Hyperlegible:ital,wght@0,400;0,700;1,400&family=Bricolage+Grotesque:opsz,wght@12..96,600;12..96,800&display=swap">
<link rel="stylesheet" href="/assets/site.css">
</head>
<body class="${page.kind}">
<header class="top">
  <a class="brand" href="${t.home}"><img src="/assets/trecos-icon.svg" alt="" width="36" height="36">Trecos</a>
  <nav>
    <a href="${t.privacy}">${t.privacyLabel}</a>
    <a href="${url(page.twin)}" hreflang="${t.other.lang}" lang="${t.other.lang}">${t.other.label}</a>
  </nav>
</header>
<main>
${html}
</main>
<footer class="bottom">
  <p>${t.footer}</p>
  <p class="links">
    <a href="${t.privacy}">${t.privacyLabel}</a>
    <a href="${t.commercial}">${t.commercialLabel}</a>
    <a href="https://github.com/vcasadei/trecos">${t.sourceLabel}</a>
    <span>${t.contact}: <a href="mailto:hello@trecos.app">hello@trecos.app</a></span>
  </p>
</footer>
<script src="/assets/site.js"></script>
</body>
</html>
`;
}

rmSync(OUT, { recursive: true, force: true });
for (const page of PAGES) {
  const target = join(OUT, page.out);
  mkdirSync(dirname(target), { recursive: true });
  writeFileSync(target, layout(page, await page.body()));
}
cpSync(join(SITE, "assets"), join(OUT, "assets"), { recursive: true });
for (const file of ["trecos-icon.svg", "trecos-logo.svg"]) {
  cpSync(join(REPO, "docs/brand", file), join(OUT, "assets", file));
}
mkdirSync(join(OUT, "assets/screens"), { recursive: true });
for (const [name, source] of Object.entries(config.screens)) {
  cpSync(join(REPO, "app/src/test/screenshots", source), join(OUT, "assets/screens", name));
}
writeFileSync(join(OUT, "CNAME"), "trecos.app\n");
console.log(`Built ${PAGES.length} pages into ${OUT}`);
