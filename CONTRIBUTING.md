# Contributing to Trecos

Thanks for helping. Trecos is source-available under the
[PolyForm Noncommercial License 1.0.0](LICENSE), and everyone taking part
follows the [Code of Conduct](CODE_OF_CONDUCT.md).

## Before you start

| Step | Where |
|---|---|
| Report a bug or suggest a feature | GitHub issues |
| Report a security problem | Privately, as described in [SECURITY.md](SECURITY.md), never in a public issue |
| Discuss a larger change | Open an issue first, so the design can be agreed before you write code |

## Contributor License Agreement

Every pull request needs the CLA box in the pull request template ticked. The
[CLA](CLA.md) lets the maintainer also offer Trecos under commercial terms
([COMMERCIAL.md](COMMERCIAL.md)); you keep the copyright in your work. Pull
requests without it can't be merged.

## Development

- Set up the machine with [docs/dev/setup-headless-linux.md](docs/dev/setup-headless-linux.md).
- Build: `./gradlew assembleDebug`.
- Planned work and its specifications live in `openspec/`; changes of behaviour
  go through an OpenSpec change first.

## Pull requests

- **Commits:** [Conventional Commits](https://www.conventionalcommits.org/)
  (`type(scope): imperative summary`), linear history (rebase, don't merge).
- **Scope:** one topic per pull request; no unrelated reformatting.
- **Tests:** add or update unit and screenshot tests for what you change.
- **Docs:** update `docs/` in the same pull request when behaviour or configuration changes.
- **Dependencies:** a new third-party library needs the maintainer's approval
  first. Advertising, analytics and crash-reporting SDKs are never accepted.
- **Secrets:** never commit keys, keystores or passwords. The gitleaks
  pre-commit hook (`pre-commit install`) and the CI secret scan must pass.
- **Languages:** app text goes in string resources, in English and Portuguese (Brazil).
