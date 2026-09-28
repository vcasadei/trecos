# From a one-page idea to a reviewable plan: a spec-driven development case study

**Project:** Trecos (formerly Cubby), an offline-first home inventory app for Android
**Author:** Vitor Casadei, working with Claude Code (Claude Opus 5.5)
**Method:** spec-driven development (SDD) with OpenSpec and the custom `casadei` schema
**Period:** 26 to 28 September 2026
**Status at the end of this document:** planning complete (proposal, 16 specs,
design, 151 tasks); implementation not started

This document records, step by step, how a free-form product idea became a
complete, validated and version-controlled development plan through a
conversation with an AI coding agent. It is written to be reused as teaching
material: every phase lists what was done, the questions asked and the answers
given, the commands run, and what worked or didn't. The metrics section gives
the actual time, tokens, context size and cost of the process.

---

## Contents

1. [Summary](#1-summary)
2. [Metrics](#2-metrics)
3. [The setup](#3-the-setup)
4. [The workflow at a glance](#4-the-workflow-at-a-glance)
5. [Phase 0: installing the tooling](#5-phase-0-installing-the-tooling)
6. [Phase 1: the draft](#6-phase-1-the-draft)
7. [Phase 2: the first exploration pass](#7-phase-2-the-first-exploration-pass)
8. [Phase 3: the 19 questions](#8-phase-3-the-19-questions)
9. [Phase 4: writing the artifacts in stages](#9-phase-4-writing-the-artifacts-in-stages)
10. [Phase 5: secrets, signing and distribution](#10-phase-5-secrets-signing-and-distribution)
11. [Phase 6: CI housekeeping](#11-phase-6-ci-housekeeping)
12. [Command reference](#12-command-reference)
13. [Lessons learned](#13-lessons-learned)
14. [Friction and mistakes](#14-friction-and-mistakes)
15. [Metrics worth adding next time](#15-metrics-worth-adding-next-time)
16. [Appendix: commit history](#16-appendix-commit-history)

---

## 1. Summary

| | |
|---|---|
| Input | `explore.md`: a 1,690-word, single-paragraph product draft written by the author |
| Output | OpenSpec change `trecos-v1`: proposal (1,254 words), 16 capability specs (95 requirements, 174 BDD scenarios, 12,381 words), design (22 decisions, 3,585 words), tasks (151 tasks in 15 groups, 4,795 words) |
| Decisions | 19 top-level questions plus 18 follow-ups, each answered explicitly by the author; 44 closing questions asked by the agent |
| Human time | about **7.5 hours** in total, of which about **1 hour** writing the draft (estimate) and about **6.5 hours** of active conversation |
| Tokens | about **63.5 million** tokens processed (98.5% of input served from the prompt cache), **392 thousand** output tokens |
| Peak context | **316 thousand** tokens in a single request; the conversation was never compacted |
| Cost | about **US$25** at API list prices, as tracked by Claude Code |
| Commits | 16 commits on the `project-setup` branch, each pushed |

The most important result isn't the size of the plan but its provenance: every
requirement can be traced to a decision the author made, and every detail the
agent filled in on its own was listed and confirmed before it stayed in the plan.

---

## 2. Metrics

### 2.1 How the numbers were collected

Claude Code keeps a JSON Lines transcript of every session in
`~/.claude/projects/<project>/<session-id>.jsonl`. Each line is an event (a user
message, an assistant message with API token usage, a tool call or result, a
cost snapshot) with a timestamp. The script in
[`session-metrics.py`](session-metrics.py) reads these files and computes
everything below. Three sessions are involved:

| Session | Folder it ran in | What it covered |
|---|---|---|
| `3cd8aeac` | `~/cubby` | Phase 0: installing the OpenSpec fork and project rules |
| `b2421586` | `~/cubby` | Phases 2, 3, 4 and 6: exploration, questions, artifacts, CI |
| `53814769` | `~/workspace/openspec-casadei` | Improving the schema tool (not counted), then Phase 5 applied to this project (counted from 01:44 on 28 Sep) |

**Definitions:**

- **Wall-clock time**: from the first to the last event of a phase, breaks included.
- **Active time**: the sum of the gaps between consecutive messages that are
  15 minutes or shorter. A longer gap is treated as a break and not counted.
- **Your turn**: time from the agent's last output to the author's next message,
  which covers reading, thinking and typing. After a break, the typing time is
  estimated at 40 words per minute instead.
- **Waiting on the agent**: time from the author's message to the agent's last
  output before the next message (model generation plus tool execution).
- **Tokens**: summed from the `usage` block of each API response, deduplicated
  by message id. "Processed" means input + cache writes + cache reads + output.
- **Context size**: input + cache writes + cache reads of a single request,
  meaning everything the model saw at that moment.

### 2.2 Time

| Phase | When (UTC) | Wall-clock | Active | Your turn | Waiting on the agent | Your messages |
|---|---|---|---|---|---|---|
| 0. Tooling setup | 26 Sep 22:07 to 27 Sep 00:25 | 2 h 17 min | 76 min | 28 min | 47 min | 18 |
| 1. Writing the draft (estimate) | 26 Sep 23:22 to 27 Sep 00:23 | about 1 h | **about 60 min** | 60 min | 0 | - |
| 2. First exploration pass | 27 Sep 00:25 to 01:08 | 43 min | 3 min (+ reading, see note) | 1 min | 3 min | 3 |
| 3. The 19 questions | 27 Sep 20:16 to 28 Sep 00:52 | 4 h 36 min | 214 min | 188 min | 54 min | 49 |
| 4. Artifacts (proposal to tasks) | 28 Sep 00:52 to 01:45 | 53 min | 50 min | 34 min | 17 min | 8 |
| 5. Secrets and signing | 28 Sep 01:44 to 02:21 | 37 min | 37 min | 22 min | 15 min | 8 |
| 6. CI fixes | 28 Sep 02:12 to 02:20 | 8 min | 3 min | 1 min | 2 min | 2 |
| **Total for Trecos** | | | **about 443 min (7.4 h)** | | | 88 |

Notes:

- **The draft estimate.** `explore.md` was created and saved once, at 00:23:11
  on 27 Sep (its filesystem birth and modification times are identical). The
  author's previous message in the setup session was at 23:21:58, and the next
  one at 00:24:01. That gives a window of at most 61 minutes. Typing 1,690 words
  at 40 words per minute takes about 42 minutes, which leaves about 20 minutes
  for thinking. So the estimate is about one hour.
- **The first exploration pass** shows only 3 active minutes. The author read
  the agent's long first overview (about 2,000 words) inside a 40-minute gap,
  which the 15-minute rule counts as a break. A realistic figure is 10 to 15
  more minutes.
- **The rest of the tool session** (`53814769`, 37 active minutes before 01:44)
  was spent adding secret protection to the `openspec-casadei` tool itself. It
  isn't counted for Trecos.
- The pause between phases 2 and 3 (27 Sep 01:08 to 20:16) was a night's break,
  bridged by the agent's persistent memory (see [Phase 2](#7-phase-2-the-first-exploration-pass)).

### 2.3 Tokens

| Phase | API calls | Fresh input | Cache writes | Cache reads | Output (of which thinking) | Processed |
|---|---|---|---|---|---|---|
| 0. Tooling setup | 156 | 312 | 468,295 | 23,568,011 | 144,405 (43,390) | 24.2 M |
| 2 to 4, 6. Explore to tasks | 191 | 382 | 278,412 | 30,142,473 | 200,732 (54,918) | 30.6 M |
| 5. Secrets and signing | 49 | 98 | 182,138 | 8,476,372 | 46,983 (19,022) | 8.7 M |
| **Total** | **396** | **792** | **928,845** | **62,186,856** | **392,120 (117,330)** | **63.5 M** |

Within the explore session, the split by phase was: first pass 0.5 M, the 19
questions 16.8 M, the artifacts 9.6 M, and CI fixes 3.8 M. The CI fixes used a
lot of tokens for little work, because each request re-read the whole
316-thousand-token conversation.

**Why the input numbers look strange.** "Fresh input" is almost zero because
Claude Code caches the conversation prefix: each request sends only the new part
as a cache write, and the rest is read back from the cache. **98.5% of all input
tokens were cache reads.** Cache reads cost about a tenth of fresh input, so
cache writes and output drive the cost, not the 62 million cache reads.

**Words.** The author typed 2,556 words in the conversation (plus 1,690 in the
draft). The agent wrote about 33,700 words of visible replies, a ratio of 1 to
13. The final artifacts total about 22,000 words.

### 2.4 Context size

| Session | Median context per request | Peak context | Compactions |
|---|---|---|---|
| Tooling setup | 146 k | 263 k | 0 |
| Explore to tasks | 151 k | **316 k** | 0 |
| Secrets and signing | 186 k | 214 k | 0 |

The explore conversation grew steadily from 39 k tokens (the system prompt,
project rules and the draft) to 316 k at the end. It was never summarised or
compacted. That matters for the method: every decision stayed verbatim in
context through to the last task written. It is also why late, trivial requests
were expensive (see 2.3).

### 2.5 Cost and API time

| Session | Cost at list price | API time | Lines written by the agent |
|---|---|---|---|
| Tooling setup | US$11.86 | 28.6 min | 1,658 |
| Explore to tasks | US$10.67 | 34.5 min | 2,382 |
| Tool session, including Phase 5 | US$2.85 | 9.6 min | 135 |
| **Total** | **about US$25.4** | **72.7 min** | |

Costs come from Claude Code's own cost tracker (`cost-state` events), at API
list prices; on a subscription plan the actual charge differs. The explore
session's last snapshot was taken before Phase 6, so it slightly undercounts.

### 2.6 Tools the agent used (explore session)

| Tool | Calls | Used for |
|---|---|---|
| Bash | 114 | `openspec` CLI, git and GitHub, store and domain checks, memory updates |
| Write | 21 | artifact files, memory files |
| WebSearch | 16 | name research, existing apps |
| WebFetch | 7 | Google Play search pages, a design reference image |
| Edit | 5 | targeted artifact edits |
| Read / ToolSearch | 4 | reading files, loading deferred tools |

---

## 3. The setup

| Element | Choice |
|---|---|
| Agent | Claude Code (CLI) with Claude Opus 5.5, on a headless x86 Ubuntu machine over SSH |
| SDD framework | [OpenSpec](https://github.com/Fission-AI/OpenSpec), through the author's fork `vcasadei/openspec-casadei` |
| Schema | `casadei`: proposal, then specs and design, then tasks; with engineering rules (no invented requirements, approval of new dependencies, BDD scenarios with SHALL/MUST, LGPD data-protection notes, migration plans, quality gates) |
| Project rule | `CLAUDE.md`: commits are authored by the human only, with no AI co-author trailer |
| Persistence | Claude Code's file-based memory (`~/.claude/projects/.../memory/`), plus git commits pushed to GitHub |

OpenSpec separates **changes** (work in flight, in `openspec/changes/<name>/`)
from **specs** (the durable description of what the system does, in
`openspec/specs/`). A change holds its artifacts. When it is archived, its spec
deltas merge into the main specs.

---

## 4. The workflow at a glance

```
  explore.md (free-form idea)
        |
        v
  /opsx:explore  ----------------------------------------------+
   - first overview: scope, contradictions, risks, questions    |
   - pause; decisions + open questions saved to memory          |
   - 19 questions, one at a time, with a recommendation each    |  thinking only:
   - final sweep of "proposed, not confirmed" details           |  no files written
        |                                                       |  except memory
        v                                                       |
  capture, in stages, each reviewed by the author  <-----------+
   1. openspec new change "trecos-v1"
   2. proposal.md   -> review, dependency approval -> commit + push
   3. design.md     -> review, 2 more approvals    -> commit + push
   4. specs x16     -> 4 batches, each with a list of details the agent
                       filled in, confirmed or corrected -> commit + push
   5. tasks.md      -> review                      -> commit + push
        |
        v
  openspec validate trecos-v1 --strict   (after every stage)
        |
        v
  /opsx:apply  (next: release 0.1)
```

---

## 5. Phase 0: installing the tooling

**Goal:** use OpenSpec with the author's own fork and schema, and make sure
commits carry only the human's authorship.

What happened, from the author's prompts in session `3cd8aeac`:

| Time | Author's request | Result |
|---|---|---|
| 22:07 | Use my custom OpenSpec fork | The agent inspected the fork and its installer |
| 22:09 | `run ~/workspace/openspec-casadei/tools/install.sh --user` | User-level install |
| 22:11 to 22:17 | Does the authorship rule apply here? Add it to the installer behind a `--claude` flag | The installer learns to write a project `CLAUDE.md` with the authorship rule |
| 22:23 to 22:39 | Add a build and test process to the fork; add an action to sync with upstream | CI and an upstream-sync workflow in the fork |
| 23:01 to 23:14 | Set up the sync token; review what's worth bringing from upstream | First sync; selected upstream changes merged |
| 23:19 to 23:21 | Install into this project; push; merge | Commit `f7fd6bd chore: set up OpenSpec with the casadei schema` |
| 00:24 | `/opsx:<something>` doesn't autocomplete | The session was restarted so the new slash commands loaded |

**Lesson:** invest in the tooling before the product. Encoding the rules in the
schema (for example, "list new dependencies for approval" and "every scenario
needs an actor") meant the agent applied them later without being reminded.

---

## 6. Phase 1: the draft

The author wrote `explore.md` in about an hour: one continuous paragraph of
about 1,690 words that mixes requirements, UI details, nice-to-haves and open
questions to the agent. It covers inventory, hierarchy, QR codes, photos,
categories, search, sync "similar to git", login, themes, FAQ, donations,
licensing, monetization and naming.

It is a good example of an honest SDD input: it isn't a spec, and it doesn't
need to be. It contains contradictions ("default is ask every time, but ask on
first use"), duplicate fields ("quantity" and "number of items"), undefined
terms ("metric") and missing concepts (multiple houses came up only later). The
exploration phase exists to surface exactly these.

---

## 7. Phase 2: the first exploration pass

**Command:** `/opsx:explore read the draft in ./explore.md`

The agent first checked the OpenSpec state (`openspec list --json`,
`openspec list --specs`, `openspec/config.yaml`), read the schema, and checked
the machine (Java, Android SDK, KVM). It then replied with one long overview:

- **Scope:** the draft is about 10 changes, not one; a first dependency sketch
  of the phases.
- **Contradictions and gaps:** a table of 9 issues (QR codes as names, duplicate
  quantity and price fields, the recursive value, search defaults, rooms as
  containers, items inside items, the undefined "metric", currency conversion,
  a trash for deletes).
- **The data model,** with UUIDs and timestamps from day one, because sync would
  need them.
- **A git-like sync design,** using JSONL snapshots, content-addressed photos
  and a three-way merge.
- **Google Play services versus a free-software build**, and how licensing ties in.
- **Login reframed as an app lock.**
- **Direct answers** to the draft's questions: a 4th tab, the name, the license,
  monetization and docs structure.
- **One blocking question** to start: the license.

**The author:** "did you read it all? it was quite extensive.. Also, I'll have to
put a pause on this now, it's late. Can you ask everything again next time?"

The agent admitted that its overview had skipped about a dozen draft points
(category icons, list views, the container screen and others), listed them, and
offered to save a memory note. On "yes, do 2" it wrote
`memory/cubby-explore-open-questions.md` with 19 open questions, the skipped
points and the machine state.

**Lesson:** a long overview helps to see the shape of the problem, but only
focused questions resolve it. Admitting what an answer didn't cover, and
writing it down, is what made the next session productive.

---

## 8. Phase 3: the 19 questions

The next evening the author ran `/opsx:explore` again. The agent loaded the
memory note and asked the questions **one at a time**. Each question stated why
it mattered, showed the options (often as ASCII diagrams), gave a
recommendation, and ended with one bold question. Decisions were written to the
memory note after every answer.

The table records every question and answer. Quotes are the author's words, and
"(rec.)" marks the agent's recommendation.

### 8.1 Foundations

| # | Question | Options | Author's answer | Decision |
|---|---|---|---|---|
| 1 | License: is "no one may charge money without my license" a firm rule? | PolyForm Noncommercial (rec.), or AGPL plus a commercial license | "i dont care for fdroid, but I will kep the code available" | PolyForm Noncommercial 1.0.0; source-available |
| 2 | One build using Google Play services, or also a Google-free build? | Follows from 1 | (confirmed in the final sweep) | One build with Google Play services |
| 3 | Is the QR code the name, or its own field? | A: the name; B: its own unique field, filled from the name at creation (rec.) | "I wanna go with B" | Separate field; stable through renames |
| - | *Process:* "Each one of these definitions should stay in your memory, but also be written to disk… Is there on openspec a way to save this?" | Create a change now and log decisions in `design.md` | "at the end I want to have cubby v1, but it can and should be split in various .0something versions" then "I wanted to go through all decisions on the explore phase first and then write the proposals with everything in mind" | One change, released as 0.x versions; **no artifacts until every question is answered** |
| 4 | Launch languages | English + Portuguese (Brazil) (rec.) | "english and portuguese brazil" | Both, with an in-app language picker |
| 5 | Merge duplicate quantity and price fields? Whole or decimal quantities? | One quantity and one unit price (rec.) | "yes merge them, quantity should be whole numbers and price decimal" | Whole-number quantity, decimal unit price, calculated total |
| 6 | Container value: recursive? Does an override count upward? | Recursive, and override counts upward (rec.) | "yes, recursive sum and override counts upward" | As recommended |

### 8.2 The hierarchy, where the author changed the model

| # | Question | Options | Author's answer | Decision |
|---|---|---|---|---|
| 7 | One kind of container at any depth, with the Home tab as the top level? Can items hold items? | A: generic containers (rec.) | "I want to have multiple Houses. That's something I did not mention… I would like to in the future share houses with other people… So Houses are a special top level container… the computer bag… should be a simple container" | **New requirement:** houses as a special top-level entity and the future unit of sharing |
| 7a | What belongs to a house versus to the user (categories, tags, fields)? | Per user, per house, or hybrid (rec.) | "yes, go with the hybrid" | Built-ins global; custom categories, tags and fields per house |
| 7b | QR uniqueness scope; moving between houses | Unique per house, and allow moving (rec.) | "yes, unique per house and allow moving" | As recommended |
| 7c | House fields | name, address, description, photos, value | "keep it simple… value is auto. Also, if no picture is set, we could use icons… apartments, houses, cars" | Adds house icons with defaults |
| 7d | Single house: skip the house list? | Skip (rec.) | "yes, skip the house list with one house" | As recommended |

### 8.3 Data details

| # | Question | Author's answer | Decision |
|---|---|---|---|
| 8 | What is "metric" for? (rec.: drop it; unit labels on custom fields instead) | "drop 'metric' from v1 (A)" | Dropped |
| 9 | Currency: display only, with no conversion? | "yes… relabeling without converting, defaulting to phone's region" | As recommended |
| 10 | A 30-day trash? | "I like the trash and 30-day option. I also want to ask before deleting. Also, when deleting a container… a screen where the user can select one or several items… with checkboxes" | Trash, confirmation, **and the author's own "choose what to keep" flow** |
| 10a | How does the keep screen treat sub-containers and multiple destinations? | "yes, i like the idea" | Sub-containers move whole; several destinations |
| 11 | Login: an app lock using the phone's own lock, or a Cubby password? | "I like A, but I also want database encryption… they may use the app without creating a profile even" | App lock, optional profile, plus encryption |
| 11a | Encryption: always on? Photos too? | "I dont want to encrypt photos, but the photos should be backed up, encryption should be a setting. Default is off and the encryption is linked to the user's Google Account" | A setting, off by default, with the key recoverable through Google |
| 11b | Require Google, and store the key on Drive? | "yes to both. If the user has their google account hacked, that's googles fault" | As recommended |
| 11c | Photo size (the author added "720p or max 1080p… optimized thumbnails") | "yes, I agree with your suggestion" | 1080p WebP, 320 px thumbnails, location data removed |
| 12 | Record location in sync entries? | "yea, no location needed" | No location |
| 13 | The sync design and folder visibility | "yes, visible folder (A)" | Git-like per-house snapshots in a visible Drive folder |
| 13a | A local export and import? | "yes, I like the idea of local export import" | Zip backup, released before sync |
| 13b | Sync frequency default | "remove every day, it will slow things down… The default option should be sync every day", then "i wrote it wrong. Remove whenever the app opens" | The agent **asked instead of guessing** at the contradiction |

### 8.4 Product shape

| # | Question | Author's answer | Decision |
|---|---|---|---|
| 14 | 4th tab: Scan (rec.) | "Instead of scan, it could be the Search option… it could open the search page with the default being text based search, but the person may click to scan" then "A three tabs. and the search in this container in the container menu" | **The author's alternative won:** Search, Home, Settings |
| 15 | Keep the name "Cubby"? (the agent researched and found a near-identical iOS "Cubby - Home Inventory") | "I want to change cubby… Whereabox and tuckaway… already exist. Can you come up with three options that do not exist?" | The agent checked candidates against Google Play, the App Store API, the web and registries. Result: **Trecos**, with the domain `trecos.app` |
| 16 | Monetization without ads | "lets go with 1, 2 and 3" | Play Billing tip jar, external donations off-app, commercial licenses |
| 17 | Docs structure | "this works for me. Nothing to add or drop" | As proposed |
| 18a | Categories: flat or two levels; the default list | "I like two levels… I would like cables separated from chargers… adapters… Electrics category (wire nuts, heatshrink, wago…)…", then "I think a better option is for an item to have one or more categories, so a C to A cable could have a USB-C and a USB-A category" | **The author replaced the agent's connector-field design** with multiple categories per item; offline suggestion chips added |
| 18b | List views | "I dont think we need a grid view… One important thing, we need to trim long words and add '…' (I learned this from experience)" | Truncation became a cross-cutting requirement |
| 18c | Container screen | "The + asks for item or container, floating with options floating at the left… add colors to containers… Do you think it's a good idea?" | Pastel palette, tint and stripe, inheritance; house colour as a status bar band plus a pill |
| 18d | Add flow and photos | "i would change 3 photos max. Categories should be shown by default… 'Remember my choice'… auto checked by default" | Three corrections applied |
| 18e | Search rules | "I agree with all" | Accent-insensitive prefix search; filters combine as any-within, all-across |
| 18f | Start screen | "yes, default Home" | Home |
| 18g | Themes, motion, FAQ, rating | "the Home option in the menu could be the middle one… a circle integrated intersecting the top of the name rectangle" (with a reference image), "remove the white… off-white labeled as White", "the app should run on phones with 2gb of ram", and a contact e-mail | Custom bottom bar; one light theme; performance targets; FAQ grew to 10 questions |
| 18h | Order of the nice-to-haves | "i agree with all" | 1.1 OCR, 1.2 sharing, 1.3 attributes, 1.4 grid, 1.5 photo search, then multi-user |
| 19 | The 0.x release plan | "the order works" | 0.1 Foundation to 1.0 Launch |

### 8.5 The final sweep

Before writing anything, the agent listed the **nine details it had proposed
but the author had never explicitly confirmed**, for example "copying between
houses follows the move rules" and "the house band only shows with two or more
houses". Silence isn't agreement. **Author:** "all yes, write it in stages".

---

## 9. Phase 4: writing the artifacts in stages

### 9.1 Scaffolding

```bash
openspec list --json                       # confirm the project root exists
openspec new change "trecos-v1"            # replaces an earlier empty "cubby-v1"
openspec status --change trecos-v1 --json  # proposal: ready; the rest: blocked
openspec instructions proposal --change trecos-v1 --json   # template + rules
```

### 9.2 Stage 1: proposal

The agent wrote `proposal.md` (Why, What Changes, 16 capabilities, Impact).
Following the schema, **Impact** listed personal data under LGPD, the new
database, external services, and **every new third-party dependency for
approval**. The agent also raised an undiscussed point, crash reporting, rather
than assuming it.

**Author:** "regarding the dependencies, do they impact my license choice? are
they open source or close to it? We should have a section on the app showing
all dependencies… from now on, commit on every step, create project-setup
branch or something. So that there is no way to lose anything."

- The agent answered with a license table (all the open-source dependencies are
  permissive; the Google Play libraries are proprietary but freely
  distributable), added an open-source licenses screen, and proposed
  AboutLibraries (a new dependency, flagged for approval).
- `git switch -c project-setup`, then commit. Commits are authored by the human
  and carry no AI trailer, per `CLAUDE.md`.
- A persistent "commit every step" feedback memory was saved.

### 9.3 Stage 2: design

The design has 21 decisions (a 22nd was added in Phase 5), each with the
alternatives considered. Examples: no dependency-injection framework; Room
always on SQLCipher's engine; container values folded in memory; FTS4 with
accents removed; the Drive REST API called directly without the client library;
build-time flags for sync and encryption; a pre-migration database copy as the
"down" path, because Android cannot install an older version over a newer one.
Two more libraries were flagged as **pending approval** instead of being slipped
in. **Author:** "approve both, push after each commit, go with batches".

### 9.4 Stage 3: specs in four batches

| Batch | Specs | Details the agent filled in and listed | Author's response |
|---|---|---|---|
| 1 | app-shell, places, items, categories-and-tags | 10 (for example quantity 0 to 999,999; built-ins not editable; the path collapse) | "7 as we show which house with color, we don't need to show it on bigger paths, so … > Box A > Cables bag. everything else ok" |
| 2 | organize, trash, photos (search held back) | 9, plus one conflict: search results come from all houses, but the band shows only one | "yes to all" (house pill kept in search results) |
| 3 | qr-codes, custom-fields, settings, app-lock | 15 | "yes to all" |
| 4 | encryption, backup, sync, help-and-support | 13 | "yes to all" |

Every batch was checked with `openspec validate trecos-v1 --strict`, then
committed and pushed. Each requirement body contains SHALL or MUST, and every
scenario follows the schema's **AS A / WHEN / THEN** form, including at least one
failure case.

Example requirement (from `specs/places/spec.md`):

```markdown
### Requirement: Container value
A container's value SHALL be the sum of quantity times unit price of every item
below it at every depth, unless the container has a manual override. An
override MUST replace that container's value and count toward every ancestor's
total...

#### Scenario: Override counts upward
- **AS A** user whose "Office" holds a 3,500 laptop, "Box A" worth 220, and "Box B" with unpriced items overridden to 500
- **WHEN** I view "Office"
- **THEN** its value is 4,220, labelled automatic
- **AND** "Box B" shows 500, labelled manual
```

### 9.5 Stage 4: tasks

`tasks.md` holds 145 tasks at first (151 after Phase 5) in 15 groups: one per
release from 0.1 to 1.0, a database-migrations group, and quality gates. Every
task states how it is verified and names the spec scenarios it tests. Each
release group writes its own docs and changelog entry.

Three choices were surfaced for confirmation: an 80% coverage threshold for
non-UI code; Android Lint plus Kotlin warnings as errors instead of new linters;
and documentation language following decision #17 rather than the schema's
Portuguese default. **Author:** "yes to all… Make sure everything is committed
and pushed to github".

```bash
openspec validate trecos-v1 --strict
openspec status --change trecos-v1 --json   # isPlanningComplete: true
git push
```

---

## 10. Phase 5: secrets, signing and distribution

In a parallel session in the `openspec-casadei` folder, the author first added
secret protection to the schema tool (three levels, selected with
`--secrets medium|high`). Then they asked: "can you see if you can apply and
merge on my project /home/ubuntu/cubby? It should have --secrets high as it's a
professional app".

Results in this repository:

- `f9cae03`: `.gitignore` secret rules, a gitleaks pre-commit hook, a Secret scan
  workflow, and `openspec/secrets-policy.md`.
- `3c3858c`: two new decisions from the author. "We will build on github and
  release an .apk that will be sideloaded to begin with", and "I want to use a
  secret manager, but I want to be mindful of costs". This became design D22:
  the release key encrypted with SOPS and age, copies in a private repository
  and in Drive, an offline backup of the age key, CI reading GitHub secrets, and
  the same key later enrolled in Play App Signing. The only cost is the US$25
  Play registration.
- `f74a1a9`: "yes it will be public". The repository goes public before the
  first release, which moves the release secrets into a protected GitHub
  Environment.

**Lesson:** an already-planned change can absorb new cross-cutting constraints.
The schema's secrets rules made the agent update the proposal, the design, the
tasks and the policy together.

---

## 11. Phase 6: CI housekeeping

| Author | Agent |
|---|---|
| "actions/checkout@v4 runs on Node.js 20, which GitHub has deprecated" | Checked the latest release (`gh api repos/actions/checkout/releases`: v7.0.1, runs on node24), read the breaking change (it affects only `pull_request_target` and `workflow_run` on forks, which aren't used here), bumped to `@v7`, pushed, and watched the run until it passed with the warning gone |
| "pin it to ubuntu-24.04" | Pinned `runs-on`, pushed, and confirmed a run with zero annotations; recorded the convention for the future CI workflow |

---

## 12. Command reference

### OpenSpec slash commands (Claude Code)

| Command | Purpose in this project |
|---|---|
| `/opsx:explore <topic>` | Thinking mode: read, research and ask, writing nothing but memory. Used twice |
| `/opsx:propose` | Create a change with all artifacts in one step (not used: the author preferred staged capture from explore) |
| `/opsx:apply` | Implement tasks. The next step |
| `/opsx:update`, `/opsx:sync`, `/opsx:archive` | Revise artifacts, sync spec deltas, archive a finished change |

### OpenSpec CLI

```bash
openspec list --json                    # changes in flight + project root
openspec list --specs                   # durable capabilities
openspec schemas --json                 # available schemas (casadei, spec-driven)
openspec new change "trecos-v1"         # scaffold a change (never by hand)
openspec status --change trecos-v1 --json          # artifact states, paths
openspec instructions <artifact> --change trecos-v1 --json   # template + rules
openspec validate trecos-v1 --strict    # after every stage
```

### Git and GitHub

```bash
git switch -c project-setup
git add <paths> && git commit -m "docs(openspec): ..."   # human-authored, no AI trailer
git push -u origin project-setup
gh run list --workflow secret-scan.yml --branch project-setup --limit 1
gh run watch <id> --exit-status
gh api repos/actions/checkout/releases --jq '.[0].tag_name'
```

### Research commands used during naming

```bash
# App Store search API
curl -s "https://itunes.apple.com/search?term=<name>&entity=software&limit=25"
# Domain registration lookup (RDAP); 404 = unregistered. Validated against known domains first
curl -s -o /dev/null -w "%{http_code}" https://rdap.org/domain/<name>.app
curl -s -o /dev/null -w "%{http_code}" https://rdap.registro.br/domain/<name>.com.br
```

Google Play search pages were fetched and parsed for app titles. The parser was
validated on "Cubby" and on names known to be taken before it was trusted.

---

## 13. Lessons learned

1. **Explore before you write.** No artifact was written until all 19 questions
   were settled, at the author's request. As a result, the proposal and specs
   were written once, with the whole picture, and needed only small corrections.
2. **One question at a time, with a recommendation.** Each question said why it
   mattered, what it unblocked, the options with trade-offs, and a preferred
   option. The author could answer "yes" in seconds, or push back with something
   better.
3. **The human's pushback made the product better.** The biggest improvements
   came from the author: houses as a first-class entity, the "choose what to
   keep" delete flow, multiple categories instead of connector fields, the
   Search tab, container colours, truncation as a rule, and the bottom bar
   design. The agent's job was to make those choices cheap to express.
4. **Record decisions outside the conversation.** A memory file, updated after
   every answer, bridged the overnight pause. The final state of that file was a
   complete decision log that fed the artifacts.
5. **Silence isn't agreement.** Every detail the agent inferred was labelled
   "proposed, not confirmed", and a final sweep, plus a list per spec batch, got
   explicit answers. This is how the schema's "do not invent requirements" rule
   works in practice.
6. **Ground claims in evidence.** Name availability was checked in the stores,
   on the web and in registries. The Play parser was validated against known
   results. A version bump was preceded by reading the release notes.
7. **Stage the writing, and validate each stage.** Proposal, design, four spec
   batches, then tasks, each reviewed, validated with `--strict`, committed and
   pushed. A failure in the middle would have lost minutes, not hours.
8. **Ask when the input contradicts itself.** "Remove every day… default every
   day" got a clarifying question, not a guess.
9. **Let the schema carry the rules.** Dependency approval, data-protection
   retention statements, migration rollback and quality gates all happened
   because the schema asked for them, not because anyone remembered.

---

## 14. Friction and mistakes

| What happened | Effect | Fix or lesson |
|---|---|---|
| The first overview skipped about 12 draft points | The author asked "did you read it all?" | The agent listed the gaps and queued them as question 18 |
| The first recommendation assumed F-Droid mattered | A Google-free build was proposed | Asking about the license first resolved it in one answer |
| A long research turn ended without a visible reply | "bring me the last message regarding the list of possible names" | Research turns should end with a summary, even an interim one |
| Terminal output was cut off (in the tool session) | "write to a temp file so that I can read it" | Very long answers are better as files |
| The memory path is tied to the folder name | Renaming `~/cubby` would orphan the memory | The local folder rename was postponed |
| A generated workflow used a deprecated action version | A CI warning | Fixed here; the fix also belongs in the tool's template |
| Late requests re-read the full 316 k context | 3.8 M tokens for two one-line CI fixes | Start a fresh session for unrelated small tasks |

---

## 15. Metrics worth adding next time

Captured in this document: time, tokens, context size, cost, API time, words,
tool calls, artifact sizes and commits. Worth adding:

| Metric | Why it is useful | How to collect it |
|---|---|---|
| **Recommendation acceptance rate** | Shows how much the human steered the plan. Here, about 14 of 36 answers changed or extended the recommendation | Tag each answer as accepted, modified or replaced |
| **Decisions per hour** | The throughput of the explore phase (about 36 decisions in 3.6 active hours) | Count the decision log entries |
| **Inferred-detail confirmation rate** | How much the agent filled in, and how often it was right (47 listed details, 1 corrected) | The per-batch confirmation lists |
| **Rework after review** | The quality of first drafts: edits to artifacts after the author's review | `git log -p` on artifact files after each review commit |
| **Time to first question** | How fast exploration becomes productive | Timestamps of the first bold question |
| **Cache hit rate** | Cost efficiency (98.5% here) | Cache reads / all input tokens |
| **Tokens per artifact word** | Comparable across projects | Processed tokens / final artifact words |
| **Requirements traceability** | Plan quality: every scenario mapped to a task | Count scenarios referenced in `tasks.md` |
| **Clarifications from contradictions** | How ambiguous the input was | Count the agent's clarifying questions |
| **Implementation versus plan** | Estimate accuracy, once `/opsx:apply` starts | Actual time per task group against planned releases |

---

## 16. Appendix: commit history

| Commit | Time (UTC) | Message |
|---|---|---|
| `f7fd6bd` | 26 Sep 23:19 | chore: set up OpenSpec with the casadei schema |
| `e527e40` | 28 Sep 00:57 | docs(openspec): add trecos-v1 change with proposal |
| `5d94f79` | 28 Sep 01:00 | docs(openspec): add trecos-v1 design |
| `b7543ee` | 28 Sep 01:03 | docs(openspec): record approval of AboutLibraries and androidx.print |
| `57f98b7` | 28 Sep 01:05 | docs(openspec): add specs batch 1 (app-shell, places, items, categories-and-tags) |
| `3ab327f` | 28 Sep 01:09 | docs(openspec): collapse long paths to '... > last two levels' |
| `b3b38ed` | 28 Sep 01:10 | docs(openspec): add specs organize, trash, photos |
| `f4e481c` | 28 Sep 01:25 | docs(openspec): add search spec |
| `39d8983` | 28 Sep 01:26 | docs(openspec): add specs batch 3 (qr-codes, custom-fields, settings, app-lock) |
| `9899295` | 28 Sep 01:31 | docs(openspec): add specs batch 4 (encryption, backup, sync, help-and-support) |
| `9979daf` | 28 Sep 01:37 | docs(openspec): add trecos-v1 tasks grouped by release 0.1-1.0 |
| `f9cae03` | 28 Sep 01:48 | chore(openspec): update casadei schema and add secret protection |
| `3c3858c` | 28 Sep 02:05 | docs(openspec): plan release signing and sideloaded distribution for trecos-v1 |
| `f74a1a9` | 28 Sep 02:10 | docs(openspec): make the trecos repository public before the first release |
| `06d586b` | 28 Sep 02:13 | ci: bump actions/checkout to v7 (Node.js 24) |
| `acab31f` | 28 Sep 02:14 | ci: pin secret scan runner to ubuntu-24.04 |
