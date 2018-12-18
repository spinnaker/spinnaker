/*
 * Copyright 2016 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.model;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface EntityTagsProvider {
  /**
   * Fetch EntityTags by any combination of {@code cloudProvider}/{@code type}/{@code idPrefix}/{@code tags}
   */
  Collection<EntityTags> getAll(String cloudProvider,
                                String application,
                                String entityType,
                                List<String> entityIds,
                                String idPrefix,
                                String account,
                                String region,
                                String namespace,
                                Map<String, Object> tags,
                                int maxResults);

  /**
   * Fetch EntityTags by {@code id}
   */
  Optional<EntityTags> get(String id);

  /**
   * Fetch EntityTags by {@code id} AND {@code tags}, both must match
   */
  Optional<EntityTags> get(String id, Map<String, Object> tags);

  /**
   * Index an EntityTags
   */
  void index(EntityTags entityTags);

  /**
   * Index multiple EntityTags
   */
  void bulkIndex(Collection<EntityTags> multipleEntityTags);

  /**
   * Verify that EntityTags has been indexed and can be retrieved via a search
   */
  void verifyIndex(EntityTags entityTags);

  /**
   * Delete EntityTags by {@code id}
   */
  void delete(String id);

  /**
   * Remove all entity tags within a particular namespace, optionally deleting from the source of truth (front50).
   */
  Map<String, Object> deleteByNamespace(String namespace, boolean dryRun, boolean deleteFromSource);

  /**
   * Delete EntityTags
   */
  void bulkDelete(Collection<EntityTags> multipleEntityTags);

  /**
   * Reindex all EntityTags
   */
  void reindex();

  /**
   * Fetch delta (counts of EntityTags broken down by Elasticsearch and Front50)
   *
   * Can be used to identify when Elasticsearch and Front50 are out-of-sync.
   */
  Map delta();

  /**
   * Remove all entity tags referencing entities that no longer exist (in a clouddriver cache).
   */
  Map reconcile(String cloudProvider, String account, String region, boolean dryRun);
}
