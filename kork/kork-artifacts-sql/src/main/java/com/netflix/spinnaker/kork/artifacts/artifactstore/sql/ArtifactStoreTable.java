/*
 * Copyright 2026 Apple Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.spinnaker.kork.artifacts.artifactstore.sql;

import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.table;

import org.jooq.Field;
import org.jooq.Table;

/**
 * Hand-written jOOQ field/table definitions for the {@code artifact_store} table, matching the
 * convention used by front50-sql's TableDefinitions rather than generated jOOQ metadata.
 */
final class ArtifactStoreTable {
  static final Table<?> TABLE = table("artifact_store");
  static final Field<String> APPLICATION = field("application", String.class);
  static final Field<String> HASH = field("hash", String.class);
  static final Field<String> BODY = field("body", String.class);
  static final Field<Long> CREATED_AT = field("created_at", Long.class);

  private ArtifactStoreTable() {}
}
