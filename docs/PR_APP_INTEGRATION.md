# PR: App integration (`feature/app-integration` → `main`)

> Draft, kept current while the branch is open. Paste into the pull request
> when the work is complete.

## Summary

Makes the backend serve the Rentlo Flutter app: authentication, the data the
app's screens need, and fixes found while integrating. Existing behaviour on
`main` is preserved unless a change below says otherwise.

## Changes

| Area | Change | Migration | Tests |
|---|---|---|---|
| Docs | Branch log, PR draft, branch commit log generator | — | — |

## Breaking changes / migration notes

None yet.

## How to test

1. Reset the database (README → "Resetting the database") and start the app.
2. Run the Postman collections in `docs/api-tests/` in README order, plus any
   collections added on this branch.
3. Run `mvn test`.

## Commits

See `docs/BRANCH_COMMITS.md` (regenerate with `scripts/update_branch_log.ps1`).
