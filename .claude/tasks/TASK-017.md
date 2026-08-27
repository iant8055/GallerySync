# TASK-017 — A Help screen, for the rules that are not obvious

Milestone: v0.3 — space management
Requested by: Ian, 27 Aug 2026
Origin: cutting the "Still watched. Anything added here is archived too." line from the archived
album card, and needing somewhere for it to go

## Why

This app enforces several rules that a user cannot infer from the interface, and at present each one
is explained — if at all — in a sentence attached to the control that happens to raise it. That works
while the sentence is on screen and not at all afterwards. Ian cut one such line on 27 Aug because it
was too long for an album card, which is the right call for the card and leaves the explanation
homeless.

There is no Help or About anywhere in the app today. Settings holds Appearance, Account, Folders,
Destination, Cloud files, Automatic sync, Default mode, Optimise and Deletion, and none of them
explains *why* the app behaves as it does.

## What belongs in it

The test is: a rule the user is subject to, which the interface cannot state at the moment it
applies.

| Rule | Currently explained | Why it is not obvious |
|---|---|---|
| **Archive is a standing instruction** — files added to an Archive album later are covered by the choice already made | in the confirmation dialog, once, at the moment of consent | the album card can only show a mode badge; the consequence outlives the dialog |
| **A local removal cannot be promised as recoverable** | in the archive prompt | it is the app's most important caveat and appears only where a removal is being authorised |
| **The verified cloud copy is the guarantee** — not the trash | same place | this is what makes the rest safe, and it is stated only in passing |
| **Videos are never optimised** | one line in Settings under Optimise | a user who optimises an album of clips and sees no change has no way to know it is deliberate |
| **What the four modes actually do** | the dropdown labels, and nothing else | Backup / Sync / Archive / Off is not self-describing, and one of the four removes files |
| **Optimising is reversible through Restore** | nowhere | the full-size original stays in OneDrive; the user is never told they can get it back |

## Scope

- A Help entry in Settings, and a screen behind it.
- Prose, not controls. Nothing on this screen changes any state.
- Written to be read once and findable again, not as onboarding.

## Not in scope

- Onboarding or a tour. TASK-014's wizard is where first-run explanation belongs, and duplicating it
  here would leave two texts to keep in step.
- Per-control help icons. The design principle keeps this app small; a (?) beside every setting is
  the opposite of that, and MILESTONES already records what may sit behind a (?) and what may not.

## Acceptance

- Reachable from Settings
- Covers every rule in the table above
- States the trash caveat in the same terms the archive prompt uses — the wording has been argued
  over and must not drift into a second, softer version
- Says plainly that the full-size original stays in OneDrive and can be brought back
- Verified on hardware in both themes, per CLAUDE.md
