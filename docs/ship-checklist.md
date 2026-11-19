# Ship checklist (from docs/research/retrospective.md)

Before a pull request that changes what players see or what the owner runs. Each line is here because skipping it cost a redo. Adapted from the companion's checklist.

## Before building
- [ ] Unfamiliar kind of work? Research proven practice first and write it up in `docs/research/`.
- [ ] Pre-mortem: three lines in the PR, "this ships and fails because...". Then check each one.
- [ ] Anything public I can look up (DNS, a version, a port, a field list)? Look it up before asking the owner.

## Before the pull request
- [ ] Every problem the owner found has a test or a line here that would have caught it.
- [ ] New setting? Run with no `config.yml` on disk and with an old `config.yml` that lacks the key.
- [ ] State-carrying feature? Run on a reset world, then again with the server killed mid-step, then a returning player.
- [ ] Player-facing text read aloud as a first-time player. Quest steps say one thing each.
- [ ] Files with quotes or backslashes written with the editor tools, not a heredoc.
- [ ] `mvn package` builds; the jar loads on the test server with the plugins the live server has.

## Before a live install
- [ ] Compare the live list (`b4b-plugin-install list`) with the test server's plugins and datapacks.
- [ ] Load test on the companion test server first (`ops/testserver/test_plugin.py`).
- [ ] Say what will restart and who is warned; say which machine each command runs on.

## Before a queue question
- [ ] Every page, PR or link to open is at the top of the context; steps repeat the address.
- [ ] Commands in a step's `command`, copyable, working as is in MobaXterm.
- [ ] Every time read from the clock in the same call that writes it (never typed, never ahead).
- [ ] The queue is updated in the same turn as the work.

## After
- [ ] Kill test processes by id, never by a command-line pattern that matches the shell itself.
- [ ] Read the live result (log line, `/b4bspawn info`, `list`) and say plainly what was and was not checked.
