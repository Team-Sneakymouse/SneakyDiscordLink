# Agent instructions

Follow `../EmptyPlugin/AGENTS.md` for the shared rules.

## Repo-specific rules

- PocketBase calls (`PocketbaseRecords`) stay off the main thread. Keep the `runTaskAsynchronously` wrappers in `LinkManager` and send player feedback via `scheduler.runTask`.
- `LinkManager.inFlight` is a plain `mutableSetOf` touched from async tasks; use a concurrent set, and remove the uuid on every return path (use `finally`).
- Collection and field names (`lom2_accounts`, `owner`, `main`, `link_token`, `discord_id`) are defined in PocketBase and used by the Discord bot. Don't rename them in `Records.kt` without changing both.
- Link tokens are secrets: never log or broadcast them, and keep one active token per account (check for an existing token before issuing).
- Don't build new features on PocketBase; SneakyDB is the intended replacement.
