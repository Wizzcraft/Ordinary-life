# bugs — the studio's bug list (qa-lead keeps it; append only)

How to read an entry:
`### BUG-nnn  [severity]  where — one-line title`, then Found / Repro / Why it matters / Fix / Owner.
Severity: **blocker** (a release cannot ship with it; the owner is told directly) · **major**
(a player will hit it and be hurt: money, progress, fairness, a broken screen) · **minor**
(visible but harmless, or rare) · **polish** (the difference between good and game of the year).
Status changes are appended as `> BUG-nnn: fixed in <branch> <commit>, verified by qa-lead` (or
`not reproduced`, `by design: <who ruled>`). Nothing is closed without a re-test.

Every bug here was reproduced before it was written down. The reproduction is a command, a test
in `tools/test/`, or a short program under `scratchpad/qa-lead/repro/`.

Counts are kept at the bottom of `room/papers/qa-lead.md`.
