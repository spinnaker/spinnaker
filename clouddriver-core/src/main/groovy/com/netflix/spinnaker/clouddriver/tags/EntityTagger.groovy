/*
 * Copyright 2017 Netflix, Inc.
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

package com.netflix.spinnaker.clouddriver.tags

import com.netflix.spinnaker.clouddriver.model.EntityTags;

/**
 * Provides a mechanism for attaching arbitrary metadata to resources within cloud providers.
 */
interface EntityTagger {
  public static final String ENTITY_TYPE_SERVER_GROUP = "servergroup"
  public static final String ENTITY_TYPE_CLUSTER = "cluster"

  void alert(String cloudProvider,
             String accountId,
             String region,
             String category,
             String entityType,
             String entityId,
             String key,
             String value,
             Long timestamp)

  void notice(String cloudProvider,
              String accountId,
              String region,
              String category,
              String entityType,
              String entityId,
              String key,
              String value,
              Long timestamp)

  void tag(String cloudProvider,
           String accountId,
           String region,
           String namespace,
           String entityType,
           String entityId,
           String tagName,
           Object value,
           Long timestamp)

  Collection<EntityTags> taggedEntities(String cloudProvider,
                                        String accountId,
                                        String entityType,
                                        String tagName,
                                        int maxResults)

  void deleteAll(String cloudProvider,
                 String accountId,
                 String region,
                 String entityType,
                 String entityId)

  void delete(String cloudProvider,
              String accountId,
              String region,
              String entityType,
              String entityId,
              String tagName)
}
