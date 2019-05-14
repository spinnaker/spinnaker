/*
 * Copyright 2018 Netflix, Inc.
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

package com.netflix.spinnaker.clouddriver.elasticsearch.model;

import static com.netflix.spinnaker.clouddriver.tags.EntityTagger.ENTITY_TYPE_SERVER_GROUP;

import com.google.common.collect.ImmutableMap;
import com.netflix.spinnaker.clouddriver.core.services.Front50Service;
import com.netflix.spinnaker.clouddriver.model.EntityTags;
import com.netflix.spinnaker.clouddriver.model.ServerGroupProvider;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class ElasticSearchEntityTagsReconciler {
  private final Logger log = LoggerFactory.getLogger(getClass());

  private final Front50Service front50Service;
  private final Map<String, ServerGroupProvider> serverGroupProviderByCloudProvider =
      new HashMap<>();

  @Autowired
  public ElasticSearchEntityTagsReconciler(
      Front50Service front50Service,
      Optional<Collection<ServerGroupProvider>> serverGroupProviders) {
    this.front50Service = front50Service;

    for (ServerGroupProvider serverGroupProvider : serverGroupProviders.orElse(new ArrayList<>())) {
      serverGroupProviderByCloudProvider.put(
          serverGroupProvider.getCloudProviderId(), serverGroupProvider);
    }
  }

  /**
   * Remove any orphaned entity tags from elastic search in a specific account/region and cloud
   * provider.
   */
  Map reconcile(
      ElasticSearchEntityTagsProvider entityTagsProvider,
      String cloudProvider,
      String account,
      String region,
      boolean dryRun) {
    Collection<EntityTags> allEntityTags = front50Service.getAllEntityTags(false);

    List<EntityTags> allServerGroupEntityTags =
        filter(allEntityTags, cloudProvider, account, region);

    List<EntityTags> existingServerGroupEntityTags =
        filter(Collections.singletonList(cloudProvider), allServerGroupEntityTags);

    log.debug(
        "Found {} server group entity tags (valid: {}, invalid: {}, dryRun: {})",
        allServerGroupEntityTags.size(),
        existingServerGroupEntityTags.size(),
        allServerGroupEntityTags.size() - existingServerGroupEntityTags.size(),
        dryRun);

    List<EntityTags> orphanedServerGroupEntityTags = new ArrayList<>(allServerGroupEntityTags);
    orphanedServerGroupEntityTags.removeAll(existingServerGroupEntityTags);

    if (!dryRun) {
      entityTagsProvider.bulkDelete(orphanedServerGroupEntityTags);

      log.info("Removed {} orphaned entity tags", orphanedServerGroupEntityTags.size());
    }

    return ImmutableMap.builder()
        .put("dryRun", dryRun)
        .put("orphanCount", orphanedServerGroupEntityTags.size())
        .build();
  }

  /**
   * Filter out any orphaned entity tags that reference a non-existent server group.
   *
   * <p>This is invoked as part of the re-indexing process where historical data is imported from
   * Front50.
   */
  public List<EntityTags> filter(Collection<EntityTags> entityTags) {
    return filter(serverGroupProviderByCloudProvider.keySet(), entityTags);
  }

  private List<EntityTags> filter(
      Collection<String> cloudProviders, Collection<EntityTags> entityTags) {
    Set<String> serverGroupIdentifiers =
        serverGroupProviderByCloudProvider.values().stream()
            .filter(p -> cloudProviders.contains(p.getCloudProviderId()))
            .flatMap(p -> p.getServerGroupIdentifiers(null, null).stream())
            .map(String::toLowerCase)
            .collect(Collectors.toSet());

    Set<String> missingServerGroupEntityTags =
        entityTags.stream()
            .filter(e -> e.getEntityRef() != null)

            // if cloud provider is unknown, entity tags should _not_ be filtered out
            .filter(e -> cloudProviders.contains(e.getEntityRef().getCloudProvider()))

            // not all entity types are filterable
            .filter(
                e -> ENTITY_TYPE_SERVER_GROUP.equalsIgnoreCase(e.getEntityRef().getEntityType()))

            // find any entity tags that reference a non-existent server group
            .filter(
                e -> !serverGroupIdentifiers.contains(buildServerGroupIdentifier(e.getEntityRef())))
            .map(EntityTags::getId)
            .collect(Collectors.toSet());

    return entityTags.stream()
        .filter(e -> !missingServerGroupEntityTags.contains(e.getId()))
        .collect(Collectors.toList());
  }

  private List<EntityTags> filter(
      Collection<EntityTags> entityTags, String cloudProvider, String account, String region) {
    return entityTags.stream()
        .filter(e -> e.getEntityRef() != null)
        .filter(e -> ENTITY_TYPE_SERVER_GROUP.equalsIgnoreCase(e.getEntityRef().getEntityType()))
        .filter(e -> cloudProvider.equalsIgnoreCase(e.getEntityRef().getCloudProvider()))
        .filter(e -> account == null || account.equalsIgnoreCase(e.getEntityRef().getAccount()))
        .filter(e -> region == null || region.equalsIgnoreCase(e.getEntityRef().getRegion()))

        // tag must be > 14 days old (temporary safe guard)
        .filter(e -> e.getLastModified() < System.currentTimeMillis() - TimeUnit.DAYS.toMillis(14))
        .collect(Collectors.toList());
  }

  private String buildServerGroupIdentifier(EntityTags.EntityRef entityRef) {
    ServerGroupProvider serverGroupProvider =
        serverGroupProviderByCloudProvider.get(entityRef.getCloudProvider());
    return serverGroupProvider
        .buildServerGroupIdentifier(
            entityRef.getAccount(), entityRef.getRegion(), entityRef.getEntityId())
        .toLowerCase();
  }
}
