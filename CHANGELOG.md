# Changelog

## 0.2.0 (unreleased)

First release from this repository (0.1.0 lived inside the hub and was never published).

- New package `com.runeclans.sdk`, artifact `com.runeclans:runeclan-sdk`. Entry point `RuneClan.connect()`.
- The API key comes from the builder, `RUNECLAN_API_KEY`, or `runeclan.properties` (`RUNECLAN_CONFIG` names another file). The key is
  never logged; a key file inside a git folder that no `.gitignore` names gets a warning.
- Start-up handshake (`GET /v1/me`): scopes, tier, rate limit, SDK versions and notices. A wrong key fails at connect; a call that needs a
  scope the key lacks fails before it is sent.
- Every call sends `X-RuneClan-Client: runeclan-sdk-java/0.2.0`.
- Pacing: a local token bucket set from the handshake and corrected by `RateLimit-Limit` / `RateLimit-Remaining` on every answer.
- 429: waits `Retry-After` (up to `maxRetryWait`, default 60 s) and sends again (any call after the hub's own `rate_limited`; only
  repeatable calls after another 429). A longer wait is thrown and holds back no other call. Lost connections and 502/503/504: GET and PUT are sent
  again with backoff; POST and DELETE only when they carry an `Idempotency-Key` (`RouteCall.idempotent()`).
- `ApiAction` for every call: `complete()`, `submit()` (CompletableFuture), `queue(...)`.
- Data types generated from the hub's public OpenAPI operations (`spec/openapi.json`, `tools/gen_model.py`), and `Routes` lists all of
  them for `client.route(...)`.
- `FakeTransport` and a `Ticker` seam for tests.
- `gimGroups()`: RS3 Group Ironman groups (`GET /v1/gim-groups`, `/v1/gim-groups/{id}`): name, size, mode, group total XP and level, and a totals history. From Jagex's undocumented group leaderboard, read daily by the hub; no members.
- `communities()`: `list()`, `bySlug(slug)` and `get(communityId)`, read with any key as `clans()` (needs the hub's public community
  routes). The spec now also carries the hub's `Idempotency-Key` header on keyed writes.
