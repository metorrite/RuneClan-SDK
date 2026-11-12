# RuneClan SDK

The public Java SDK for the RuneClans hub API (Java 17+, Jackson only). This repository is **public**: never commit secrets, API keys,
real Discord ids (build fake ones at runtime, e.g. `"1".repeat(18)`), internal plans or lessons files.

Branch from `dev` and open PRs into `dev`; `master` is the release branch (a version is tagged from master only).

## Checks
- `mvn verify` (look at the `Tests run:` line) and `python3 tools/gen_model.py --check`.
- Anything with a lifetime (pacing, retries, handshake retry) gets fake-clock tests (`FakeTicker`).

## Rules
- `spec/openapi.json` holds only the hub's **public** operations. Refresh it with
  `python3 tools/sync_spec.py <RuneClan-API>/src/main/resources/openapi.json --source <hub commit>`, then `python3 tools/gen_model.py`.
  Never hand-edit `spec/`, `model/` or `Routes.java`.
- First-party and plugin routes never go in this SDK.
- `RuneClanClient.VERSION` must equal the pom version (`SpecTest`).
- POST and DELETE are never retried after a lost connection or 5xx unless they carry an `Idempotency-Key`.
- Never log, print or put the key in an exception message or `toString()`.

## Never
Never commit secrets, never touch `master`, never force-push.
