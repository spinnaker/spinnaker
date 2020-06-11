/*
 * Copyright 2020 Netflix, Inc.
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

package com.netflix.spinnaker.clouddriver.aws.agent;

import static com.netflix.spinnaker.cats.agent.AgentDataType.Authority.AUTHORITATIVE;
import static com.netflix.spinnaker.cats.agent.AgentDataType.Authority.INFORMATIVE;
import static com.netflix.spinnaker.clouddriver.core.provider.agent.Namespace.APPLICATIONS;
import static com.netflix.spinnaker.clouddriver.core.provider.agent.Namespace.CLUSTERS;
import static com.netflix.spinnaker.clouddriver.core.provider.agent.Namespace.SERVER_GROUPS;

import com.netflix.spinnaker.cats.agent.AgentDataType;
import com.netflix.spinnaker.cats.agent.CacheResult;
import com.netflix.spinnaker.cats.agent.CachingAgent;
import com.netflix.spinnaker.cats.agent.DefaultCacheResult;
import com.netflix.spinnaker.cats.cache.CacheData;
import com.netflix.spinnaker.cats.cache.DefaultCacheData;
import com.netflix.spinnaker.cats.provider.ProviderCache;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * An Authoritative caching agent for clusters where cluster existence is derived from existing
 * server groups in cache.
 */
public abstract class CatsClusterCachingAgent implements CachingAgent {
  private static final Collection<AgentDataType> dataTypes =
      Collections.unmodifiableCollection(
          Arrays.asList(
              AUTHORITATIVE.forType(CLUSTERS.ns),
              INFORMATIVE.forType(APPLICATIONS.ns),
              INFORMATIVE.forType(SERVER_GROUPS.ns)));

  private final String serverGroupsGlob;
  private final Function<String, Map<String, String>> keyParser;
  private final Function<Map<String, String>, String> clusterKeyBuilder;
  private final Function<String, String> applicationKeyBuilder;
  private final String clusterNameAttribute;
  private final String clusterApplicationAttribute;

  /**
   * @param serverGroupsGlob The search glob to look up all server groups for the provider (e.g.
   *     Keys.getServerGroupKey("*", "*", "*", "*"))
   * @param keyParser A parser for the server group and cluster keys for the provider
   * @param clusterKeyBuilder Given a parsed server group key as a map, supplies the associated
   *     cluster key
   * @param applicationKeyBuilder Given a parsed application name, supplies the associated
   *     application key
   *     <p>Uses 'cluster' as the clusterNameAttribute
   */
  public CatsClusterCachingAgent(
      String serverGroupsGlob,
      Function<String, Map<String, String>> keyParser,
      Function<Map<String, String>, String> clusterKeyBuilder,
      Function<String, String> applicationKeyBuilder) {
    this(
        serverGroupsGlob,
        keyParser,
        clusterKeyBuilder,
        applicationKeyBuilder,
        "cluster",
        "application");
  }

  /**
   * @param serverGroupsGlob The search glob to look up all server groups for the provider (e.g.
   *     Keys.getServerGroupKey("*", "*", "*", "*"))
   * @param keyParser A parser for the server group and cluster keys for the provider
   * @param clusterKeyBuilder Given a parsed server group key as a map, supplies the associated
   *     cluster key
   * @param applicationKeyBuilder Given a parsed application name, supplies the associated
   *     application key
   * @param clusterNameAttribute The attribute name in the parsed cluster key that represents the
   *     cluster's name
   * @param clusterApplicationAttribute The attribute name in the parsed cluster key that represents
   *     the cluster's application
   */
  public CatsClusterCachingAgent(
      String serverGroupsGlob,
      Function<String, Map<String, String>> keyParser,
      Function<Map<String, String>, String> clusterKeyBuilder,
      Function<String, String> applicationKeyBuilder,
      String clusterNameAttribute,
      String clusterApplicationAttribute) {
    this.serverGroupsGlob = serverGroupsGlob;
    this.keyParser = keyParser;
    this.clusterKeyBuilder = clusterKeyBuilder;
    this.applicationKeyBuilder = applicationKeyBuilder;
    this.clusterNameAttribute = clusterNameAttribute;
    this.clusterApplicationAttribute = clusterApplicationAttribute;
  }

  @Override
  public Collection<AgentDataType> getProvidedDataTypes() {
    return dataTypes;
  }

  @Override
  public CacheResult loadData(ProviderCache providerCache) {
    Collection<String> serverGroups =
        providerCache.filterIdentifiers(SERVER_GROUPS.ns, serverGroupsGlob);
    Collection<String> existing = providerCache.existingIdentifiers(SERVER_GROUPS.ns, serverGroups);
    Map<String, Set<String>> clusterData = new HashMap<>();
    for (String serverGroupKey : existing) {
      Map<String, String> serverGroupData = keyParser.apply(serverGroupKey);
      if (serverGroupData != null) {
        String clusterKey = clusterKeyBuilder.apply(serverGroupData);
        clusterData.computeIfAbsent(clusterKey, k -> new HashSet<>()).add(serverGroupKey);
      }
    }

    // cache data w/ relationships to app and cluster
    Collection<CacheData> serverGroupData = new ArrayList<>();

    // cache data w/ relationships to cluster and server groups
    Map<String, Map<String, Collection<String>>> applicationRelationships = new HashMap<>();

    Collection<CacheData> clusterCacheData =
        clusterData.entrySet().stream()
            .map(
                entry -> {
                  Map<String, String> clusterAttributes = keyParser.apply(entry.getKey());
                  String clusterApplication = clusterAttributes.get(clusterApplicationAttribute);
                  String clusterApplicationKey = applicationKeyBuilder.apply(clusterApplication);

                  for (String serverGroup : entry.getValue()) {
                    Map<String, Collection<String>> sgRels = new HashMap<>();
                    sgRels.put(APPLICATIONS.ns, Collections.singleton(clusterApplicationKey));
                    sgRels.put(CLUSTERS.ns, Collections.singleton(entry.getKey()));
                    serverGroupData.add(
                        new DefaultCacheData(serverGroup, Collections.emptyMap(), sgRels));
                  }
                  Map<String, Collection<String>> appRels =
                      applicationRelationships.computeIfAbsent(
                          clusterApplicationKey, (s) -> new HashMap<>());

                  appRels.computeIfAbsent(CLUSTERS.ns, (c) -> new HashSet<>()).add(entry.getKey());
                  appRels
                      .computeIfAbsent(SERVER_GROUPS.ns, (sg) -> new HashSet<>())
                      .addAll(entry.getValue());

                  // cluster
                  Map<String, Object> cacheAttributes = new HashMap<>();
                  cacheAttributes.put("name", clusterAttributes.get(clusterNameAttribute));
                  cacheAttributes.put(
                      "application", clusterAttributes.get(clusterApplicationAttribute));
                  Map<String, Collection<String>> relationships = new HashMap<>();
                  relationships.put(SERVER_GROUPS.ns, entry.getValue());

                  return new DefaultCacheData(entry.getKey(), cacheAttributes, relationships);
                })
            .collect(Collectors.toList());

    Collection<CacheData> applicationCacheData =
        applicationRelationships.entrySet().stream()
            .map(
                entry ->
                    new DefaultCacheData(entry.getKey(), Collections.emptyMap(), entry.getValue()))
            .collect(Collectors.toList());

    Map<String, Collection<CacheData>> cacheResult = new HashMap<>();
    cacheResult.put(CLUSTERS.ns, clusterCacheData);
    cacheResult.put(SERVER_GROUPS.ns, serverGroupData);
    cacheResult.put(APPLICATIONS.ns, applicationCacheData);
    return new DefaultCacheResult(cacheResult);
  }
}
