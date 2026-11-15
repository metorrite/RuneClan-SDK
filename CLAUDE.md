# RuneClan SDK

The public Java SDK for the RuneClans hub API (Java 17+, Jackson only). This repository is **public**: never commit secrets, API keys,
real Discord ids (build fake ones at runtime, e.g. `"1".repeat(18)`), internal plans or lessons files.

Branch from `dev` and open PRs into `dev`; `master` is the release branch (a version is tagged from master only).

## Checks
- `mvn verify` (look at the `Tests run:` line), `python3 tools/gen_model.py --check` and `python3 tools/gen_docs.py --check`.
  In the cloud, add `-Dmaven.artifact.threads=1` when Maven reports "Could not find artifact" for plugins it is downloading.
- Anything with a lifetime (pacing, retries, handshake retry) gets fake-clock tests (`FakeTicker`).

## Rules
- `spec/openapi.json` holds only the hub's **public** operations. Refresh it with
  `python3 tools/sync_spec.py <RuneClan-API>/src/main/resources/openapi.json --source <hub commit>`, then `python3 tools/gen_model.py`.
  Never hand-edit `spec/`, `model/` or `Routes.java`.
- `docs/index.html` (the API reference, also packed into the jar for `com.runeclans.sdk.Docs`) is made by `python3 tools/gen_docs.py`
  from the sources, the spec, `examples/` and the words in `tools/docs_content.py`. Never hand-edit it; rerun the tool after any change
  to a public method, the spec or an example. Every public method needs an example in `tools/docs_content.py` (`DocsTest`, `--check`).
- `examples/*.java` are standalone programs; `ExamplesTest` compiles each and runs its `run(...)` against a fake hub. A new example needs
  a test there. No real ids or keys in examples or docs: variables or runtime-built fakes only.
- First-party and plugin routes never go in this SDK.
- `RuneClanClient.VERSION` must equal the pom version (`SpecTest`).
- POST and DELETE are never retried after a lost connection or 5xx unless they carry an `Idempotency-Key`.
- Never log, print or put the key in an exception message or `toString()`.

## Never
Never commit secrets, never touch `master`, never force-push.
