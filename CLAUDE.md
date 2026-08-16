# GallerySync — Agent Rules

## Project
Cross-source photo/media sync app.
Stack: TypeScript, Electron, SQLite (better-sqlite3), React (renderer), Vitest (tests).

## Hard Rules — all agents must follow
- Never delete user media files without an explicit UI confirmation prompt
- All file operations are reversible until a sync commit is confirmed
- Test files live in src/__tests__/ mirroring the src/ structure
- Commit after every completed feature: "feat(scope): description"
- Tag milestones: v0.1.0, v0.2.0, etc. after each major feature group
- No console.log in production code — use Logger service (src/services/logger.ts)
- SQLite WAL mode always on; never hold transactions across async gaps

## Escalate to Ian — Lead Agent only, when:
- A new sync source requires registering an OAuth app or API credentials
- A database schema change requires migrating existing user data
- A dependency has a license conflict with the distribution plan
- Feature scope has two architecturally distinct paths with long-term implications
- A security issue is found in existing code
- The debug loop has cycled 3+ times without resolving a test failure

## Autonomous — no escalation needed for:
- Utility functions, helpers, service methods
- Writing or updating tests
- Bug fixes with clear root cause
- UI layout and styling changes
- Refactoring within a single module

## File Structure
src/
  main/        <- Electron main process
  renderer/    <- React UI
  services/    <- Sync engine, file watchers, DB access
  __tests__/   <- Vitest test files (mirror src/ structure)
.claude/
  agents/      <- Agent prompt files (version-controlled)
  tasks/       <- Active task specs (TASK-NNN.md, FIX-NNN.md)
  MILESTONES.md
