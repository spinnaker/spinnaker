# Entity Tags: Move to Clouddriver SQL, Retire Elasticsearch and Front50 Path

## Goal

Entity tags are metadata about clouddriver-owned entities (server groups,
load balancers, clusters, applications). They belong in the service that
owns those entities. This plan:

1. Adds a SQL-backed entity-tag store to Clouddriver (`clouddriver-sql`).
2. Points Clouddriver's existing `EntityTagsProvider` interface at the
   new store instead of Elasticsearch.
3. Collapses the atomic-operation write path (no more dual-write).
4. Deletes `clouddriver-elasticsearch` in its entirety.
5. Retires the entity-tag surface in Front50 (controller, DAO,
   `ENTITY_TAGS` object type, Retrofit endpoints on
   `Front50Service`).

## Why Clouddriver (not Front50, not kork)

- **Locality:** every entity a tag can point to is a clouddriver concept.
  The alerting agents that write tags (`LaunchFailureNotificationAgent`,
  `EntityTagger`) already run in clouddriver. The reconciler needs
  cloud-provider state (which server groups still exist) — also
  clouddriver.
- **Write path collapses:** atomic op → local SQL. Today it's
  atomic op → HTTP to Front50 → HTTP to ES, with a `verifyIndex`
  polling loop to paper over ES refresh lag. All of that goes away.
- **Front50 is a document store for user-declared config** (apps,
  pipelines, projects, permissions, notifications). Entity tags are
  system-generated metadata about live infra — they never fit cleanly.
- **kork isn't a runtime service.** A kork `Entity`/`EntityTags` SPI
  makes sense the day a second service (e.g., igor for CI job tags)
  wants entity tagging. Today there's exactly one caller. Defer the
  abstraction until there's a real second consumer; the model classes
  are trivially extractable when that day comes.

## Target architecture

```
Deck / Gate  ---->  Clouddriver
   GET /tags?...       EntityTagsController        (unchanged URL)
                             |
                             v
                       SqlEntityTagsProvider       (new)
                             |
                             v
                       entity_tags + entity_tag_index  (new tables in clouddriver-sql)
                             ^
                             |
Atomic ops    -------->  BulkUpsert/Delete           (simplified: single local write)
(Orca / agents)          atomic operations (moved out of -elasticsearch module)
```

## Schema (in `clouddriver-sql`)

New Liquibase changelog `clouddriver-sql/.../db/changelog/YYYYMMDD-entity-tags.yml`:

```sql
CREATE TABLE entity_tags (
  id                varchar(255)  NOT NULL PRIMARY KEY,
  body              longtext      NOT NULL,           -- canonical JSON EntityTags
  cloud_provider    varchar(64)   NULL,
  application       varchar(255)  NULL,
  account           varchar(255)  NULL,
  region            varchar(64)   NULL,
  entity_type       varchar(64)   NULL,
  entity_id         varchar(255)  NULL,
  last_modified_at  bigint        NOT NULL,
  last_modified_by  varchar(255)  NOT NULL,
  created_at        bigint        NOT NULL
);
CREATE INDEX entity_tags_app_idx      ON entity_tags(application);
CREATE INDEX entity_tags_acct_reg_idx ON entity_tags(cloud_provider, account, region);
CREATE INDEX entity_tags_entity_idx   ON entity_tags(entity_type, entity_id);

CREATE TABLE entity_tag_index (
  entity_tags_id  varchar(255)  NOT NULL,
  namespace       varchar(255)  NOT NULL DEFAULT 'default',
  name            varchar(255)  NOT NULL,
  category        varchar(64)   NULL,
  PRIMARY KEY (entity_tags_id, namespace, name)
);
CREATE INDEX entity_tag_index_ns_name ON entity_tag_index(namespace, name);
```

- `body` remains the source of truth for the tag document; projection
  columns exist purely for indexed filtering.
- On every upsert we rewrite the `entity_tag_index` rows for that
  `entity_tags_id` inside the same transaction.
- No `is_deleted` column: entity tags aren't soft-deleted today (Front50
  hard-deletes via `deleteEntityTags`). Preserve current semantics.
- MySQL + Postgres both supported; no JSON functions required.

## Code layout in Clouddriver

- **Model** (`clouddriver-core`): keep `EntityTags`, `EntityTag`,
  `EntityRef`, `EntityTagMetadata`, `EntityTagsProvider` where they are.
- **SQL provider** (`clouddriver-sql`, new package
  `com.netflix.spinnaker.clouddriver.sql.entitytags`):
  - `SqlEntityTagsProvider implements EntityTagsProvider` — full CRUD +
    filtered query. Replaces `ElasticSearchEntityTagsProvider`.
  - `SqlEntityTagsReconciler` — orphan cleanup (same logic as
    `ElasticSearchEntityTagsReconciler`, but reads from local SQL
    instead of Front50/ES).
- **Atomic operations** (`clouddriver-web` or a new
  `clouddriver-entitytags` module — see PR3 decision below):
  - Move `UpsertEntityTagsAtomicOperation`,
    `BulkUpsertEntityTagsAtomicOperation`,
    `DeleteEntityTagsAtomicOperation`, `BulkDeleteEntityTagsAtomicOperation`,
    their converters/descriptions/validators, `EntityRefIdBuilder`, and
    the `EntityTagger` / `DefaultEntityTagger` API out of
    `clouddriver-elasticsearch` and into a non-ES module.
  - Simplified body: get existing tags from `EntityTagsProvider`, merge,
    write via `EntityTagsProvider.bulkIndex(...)`. Single write path.
  - Remove `verifyIndex` polling entirely.
- **Alerting integrations** (AWS `LaunchFailureNotificationAgent` and
  its wiring) — move out of `clouddriver-elasticsearch` into
  `clouddriver-aws` (where they belong). They currently only depend on
  `EntityTagger`, which is portable.
- **Controllers** (`clouddriver-web`): `EntityTagsController` unchanged
  in URL and params. `EntityTagsAdminController`'s `/reindex` and
  `/delta` endpoints go away (no index to rebuild). `/reconcile` stays.

## Read/write endpoints

**Read** — no change to Deck/Gate wire:
- `GET /tags?cloudProvider=&application=&entityType=&entityId=&idPrefix=&account=&region=&namespace=&tag:X=Y&maxResults=`
- `GET /tags/{id}`

**Write** — same atomic operation names, same Orca stage contracts.
Internally: one SQL upsert instead of Front50 + ES.

**Removed:** `/admin/tags/reindex`, `/admin/tags/delta`. Documented as
part of PR3.

## Data migration (one-shot)

Existing installs have tags in Front50. Ship a
`Front50ToClouddriverEntityTagsMigrator` bean in `clouddriver-sql`,
gated by property `entity-tags.front50-migration.enabled: true`:

1. On startup, checks if `entity_tags` is empty.
2. If empty and flag is set, pages `front50Service.getAllEntityTags(true)`
   and bulk-inserts into `entity_tags` + `entity_tag_index`.
3. Logs count; leaves Front50 data intact so the migration is
   re-runnable if the SQL side is dropped.
4. Migrator class is deleted in the release *after* PR3 ships.

Non-goal: no dual-read window. Once operators flip the migration flag
and confirm counts, the Front50 tag endpoints stop being called.

## PR sequence

### PR1 — Clouddriver SQL entity tag store
- Liquibase changelog (schema above), wired into
  `clouddriver-sql/.../db/changelog-master.yml`.
- `SqlEntityTagsProvider` implementing the existing `EntityTagsProvider`
  interface. Uses jOOQ, follows patterns from `SqlTaskRepository`.
- `SqlEntityTagsReconciler` (mirrors ES reconciler behavior).
- Bean config guarded by property `entity-tags.provider: sql`
  (default remains `elasticsearch` in this PR so the switch is opt-in).
- Tests: testcontainers MySQL + Postgres. Cover all filter combos, tag
  merge on upsert, delete, reconcile.

### PR2 — Cutover: default to SQL, move atomic ops + alerting off `-elasticsearch`
- Flip default of `entity-tags.provider` to `sql`.
- Extract atomic ops, `EntityTagger`, `EntityRefIdBuilder`, converters,
  descriptions, validators from `clouddriver-elasticsearch` into a new
  `clouddriver-entitytags` module (or fold into `clouddriver-web`; TBD
  based on dependency graph — see "Open questions" below).
- Rewrite `BulkUpsertEntityTagsAtomicOperation` to call
  `entityTagsProvider.bulkIndex` only (no Front50 call, no verify loop).
  Same for `DeleteEntityTagsAtomicOperation`.
- Ship the `Front50ToClouddriverEntityTagsMigrator` behind the migration
  flag.
- `clouddriver-elasticsearch` module still present but no longer wired
  in — safety net for one release.
- Tests: WireMock for migrator; existing atomic-op specs updated for
  new single-write path.

### PR3 — Delete `clouddriver-elasticsearch` and Front50 tag surface
- Remove `clouddriver-elasticsearch` module, its gradle wiring,
  halconfig references, and remaining ES config classes.
- Remove `EntityTagsController`, `DefaultEntityTagsDAO`,
  `EntityTagsDAO` from Front50.
- Remove `ENTITY_TAGS` from `ObjectType` and its handling in every
  Front50 storage backend (`SqlStorageService`, `S3StorageService`,
  `GcsStorageService`, Oracle/Azure/Swift/Redis).
- Remove `Front50Service` methods: `getAllEntityTags`,
  `getAllEntityTagsById`, `getEntityTags`, `batchUpdate` (for tags path),
  `deleteEntityTags`.
- Remove `Front50ToClouddriverEntityTagsMigrator` (its job is done).
- Release notes: operator step is "run Liquibase against clouddriver-sql;
  set `entity-tags.front50-migration.enabled=true` for one boot to copy data;
  unset afterward."

## Risks & mitigations

| Risk | Mitigation |
|---|---|
| Clouddriver instances are horizontally scaled; concurrent tag writes could race | `entity_tags` has a PK; upsert uses `ON CONFLICT ... DO UPDATE`. Bulk ops chunk by 50 today; keep chunking. Merge-on-write reads current row inside the same transaction. |
| Migration miscount | Migrator logs source count from Front50 and destination count post-insert; refuses to run if destination isn't empty. |
| Loss of Front50 as "durable across clouddriver blowouts" story | `clouddriver-sql` already stores durable state (tasks, events, accounts). Same operational discipline applies. |
| `clouddriver-elasticsearch` deletion breaks external plugins | Search codebase for `com.netflix.spinnaker.clouddriver.elasticsearch` imports outside the module itself — none found. Announce in release notes. |
| `Front50Service` method removals break other callers | Grep across the monorepo before PR3. `getAllEntityTags*` / `deleteEntityTags` are only referenced from `clouddriver-elasticsearch` today. |

## Resolved decisions

1. **Module target for extracted atomic ops:** new `clouddriver-entitytags`
   module (not folded into `clouddriver-web`). Keeps operations
   independent of web startup and mirrors the pattern used by other
   provider modules.
2. **`EntityTagger` API location:** interface + `EntityRefIdBuilder` +
   `DefaultEntityTagger` live in `clouddriver-core`. Providers
   (`clouddriver-aws`, etc.) already depend on `clouddriver-core`, so
   they get the API without any new coupling. SQL implementation of
   `EntityTagsProvider` lives in `clouddriver-sql`; atomic operations
   live in `clouddriver-entitytags`.

## Non-goals

- No kork `Entity` SPI (deferred; extract when a second consumer exists).
- No new tag-value full-text search.
- No change to `EntityTag` / `EntityTags` model classes.
- No change to the Deck/Gate contract — same URLs, same params, same
  JSON.
- No dual-read window against Front50 after cutover.
