# Retrospective: the server work, 28 to 29 October 2026

The owner asked for the companion's retrospective (`b4b-companion-game`, branch `retrospective`, `docs/research/retrospective.md`) to be read, anything learned adapted here, and a retrospective of this work too. Blameless: contributing causes, not fault. Every lesson ends in a mechanism (a test, a script, a checklist line), because a lesson that lives only in a note gets lost.

## What had to be redone

**Bold**: the owner found it before any check of mine did.

| # | What went wrong | Redo | Contributing cause |
| --- | --- | --- | --- |
| 1 | **Queue choices could not be marked** (twice) | Rewrote the item times | I typed a time ahead of the real clock; the page treats an answer older than `askedAt` as an earlier round. |
| 2 | **The live server had no essential datapack** | Installed it, extended the allowed list | I tested on a server I had built myself and never compared the live server's list with what the test server had. |
| 3 | The questline did not start in tests | Three fixes (save on start, parse the jar's config myself, quote `"on"`) | I tested a clean start, not how the server runs: no `config.yml` on disk, killed rather than stopped, YAML 1.1 reading `on` as true. |
| 4 | Stale statistics skipped steps and inflated counts | Counting own progress; reset between tests | The test world carried state from earlier runs. |
| 5 | **Questline wording: no boss bar at first, steps not split, "ink sac" odd, "Place a torch under it" vague** | Rewrites, quest dropped | I wrote player-facing text without reading it as a new player would. |
| 6 | Reveal title was empty | Fixed by the own-parsed config | Same cause as 3: the default came from a path that returns nothing. |
| 7 | Dynamic spawn landed back in the same cell | Skip the current cell | Only found by running a turnaround on a bot, which I did. |
| 8 | **Instructions too hard for the owner** (SQ6, SQ13, SQ15) and **"what machine will run it? always specify"** | Steps with copy buttons, MobaXterm wording, machine named | I wrote steps for myself, not for the person pasting them. |
| 9 | **"The owner queue seems to have been left unupdated"** | Caught the queue up | I did the work and updated the queue later. |
| 10 | Large Python patches in a Bash heredoc failed repeatedly | Patch files written with the editor, then run | A fragile tool used again after it had failed (the companion lists the same pattern, three incidents). |
| 11 | A PowerShell kill-by-pattern stopped my own shell | Kill by process id | The pattern matched the command that was running it. |
| 12 | SQ24 asked about DNS before I had looked up who runs it | SQ25 | The name servers are public; one lookup showed Dynadot. I asked the owner what I could read myself. |
| 13 | Ko-fi service: a two-item or quantity-2 order would have made one request | Fixed before it shipped | Found in this retrospective by reading Ko-fi's field list, not by a test. |

Seven of thirteen were found by the owner first (1, 2, 5, 8, 9, and the two queue repeats). That is the number to bring down.

## The patterns

1. **Testing in ideal conditions, not production's.** Clean config, clean world, a server I stopped politely (3, 4, 6), a test server built by me (2). The companion names the same pattern ("test like you fly").
2. **Writing for myself.** Steps, quest text and queue items read well to the person who wrote them (5, 8).
3. **Asking what I could look up.** DNS (12).
4. **Fragile tools used again after they failed.** Heredocs (10), pattern kills (11).
5. **Doing, then reporting.** The queue lagging the work (9), a time typed instead of read (1).

## What proven practice says (researched 29 October)

- **Webhook receivers: verify, store, then acknowledge; make handling idempotent.** Providers deliver at least once, so duplicates are normal; a unique transaction id makes a replay harmless ([Hookdeck: webhooks at scale](https://hookdeck.com/blog/webhooks-at-scale); [Hookdeck: webhook security](https://hookdeck.com/webhooks/guides/webhook-security-vulnerabilities-guide); [360Learning: processing best practices](https://360learning.readme.io/docs/webhooks-processing-best-practices)). The Ko-fi service already stores before answering and ignores a repeated transaction id.
- **Ko-fi's token is a plain-text shared secret, not a signature.** A request with an unset or empty token must be refused, not accepted ([third-party summary of Ko-fi's docs](https://community.nodebb.org/topic/17448/ko-fi-webhook-plugin-request); [fail-closed fix](https://github.com/whoshotu/wassup-chat/pull/2)). The service refuses when its token file is empty, and the dashboard is served only over HTTPS. The payload's field list comes from third-party pages, so the first real payload is stored (without the email) to check it.
- **Ko-fi shop orders carry `shop_items[]` with `direct_link_code`, `variation_name` and `quantity`** ([API catalogue](https://github.com/api-evangelist/ko-fi); [Ko-fi webhook summary](https://apis.io/apis/ko-fi/ko-fi-webhook/)). My first version used only the first item and ignored quantity (13). Fixed and tested. No source gave the Commission payload's shape, so an unclassifiable order goes to the Flagged list with the owner deciding.
- **Bukkit config defaults:** `saveDefaultConfig` copies the jar's file once and never adds new keys to an existing file; `getConfig()` reads a missing key from the jar's defaults only through the defaults tree ([Paper: plugin configuration](https://docs.papermc.io/paper/dev/plugin-configurations/); [Spigot thread](https://www.spigotmc.org/threads/whats-the-difference-between-savedefaultconfig-copydefaults.301865/)). This is what produced 3 and 6. The standing check: run every new setting with no `config.yml` on disk and with an old one that lacks the new keys.
- **Blameless postmortems end in action items that are followed through; a pre-mortem before shipping; checklists for steps that get skipped** (the companion's research: [Google SRE workbook](https://sre.google/workbook/postmortem-culture/), Klein's pre-mortem, Gawande). Adapted below.

## What changes (each one a mechanism)

| Pattern | Mechanism | Where |
| --- | --- | --- |
| Ideal conditions | A standard test pass on the test server: no `config.yml`, a stale `config.yml`, state reset, server killed mid-step, a fresh account and a returning one | `docs/ship-checklist.md`, `tools/test-server/README.md` |
| Ideal conditions | Before any live install, compare the live list with the test server's list (plugins and datapacks) | ship checklist |
| Writing for myself | Quest text and steps are read aloud as a first-time player or as someone pasting into MobaXterm; every step names the machine | ship checklist |
| Looking things up | Anything public (DNS, a version, a port) is looked up before it becomes a question | ship checklist |
| Fragile tools | Files with quotes or backslashes are written with the editor tools, never a heredoc; kill processes by id | ship checklist |
| Doing then reporting | The queue is updated in the same turn as the work; every time is read from the clock in the same call that writes it | ship checklist, memory |
| Owner finds it first | Each owner-found problem gets a regression test or a checklist line, and the count below is kept | this file |

## The count to bring down

| Stretch | Problems the owner found before my checks |
| --- | --- |
| 28-29 Oct (server work) | 7 (queue times twice, datapack, questline wording, steps hard to follow, queue lagging, torch quest) |

Next review: when the Ko-fi requests are live, with the same table.
