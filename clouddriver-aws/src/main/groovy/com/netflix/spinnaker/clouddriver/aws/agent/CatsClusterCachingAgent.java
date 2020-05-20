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
import static com.netflix.spinnaker.clouddriver.core.provider.agent.Namespace.CLUSTERS;
import static com.netflix.spinnaker.clouddriver.core.provider.agent.Namespace.SERVER_GROUPS;

import com.netflix.spinnaker.cats.agent.AgentDataType;
import com.netflix.spinnaker.cats.agent.CacheResult;
import com.netflix.spinnaker.cats.agent.CachingAgent;
import com.netflix.spinnaker.cats.agent.DefaultCacheResult;
import com.netflix.spinnaker.cats.cache.CacheData;
import com.netflix.spinnaker.cats.cache.DefaultCacheData;
import com.netflix.spinnaker.cats.provider.ProviderCache;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * An Authoritative caching agent for clusters where cluster existence is derived from existing
 * server groups in cache.
 */
public abstract class CatsClusterCachingAgent implements CachingAgent {
  private static final Collection<AgentDataType> dataTypes =
      Collections.unmodifiableCollection(Collections.singleton(AUTHORITATIVE.forType(CLUSTERS.ns)));

  private final String serverGroupsGlob;
  private final Function<String, Map<String, String>> keyParser;
  private final Function<Map<String, String>, String> clusterKeyBuilder;
  private final String clusterNameAttribute;

  /**
   * @param serverGroupsGlob The search glob to look up all server groups for the provider (e.g.
   *     Keys.getServerGroupKey("*", "*", "*", "*"))
   * @param keyParser A parser for the server group and cluster keys for the provider
   * @param clusterKeyBuilder Given a parsed server group key as a map, supplies the associated
   *     cluster key
   *     <p>Uses 'cluster' as the clusterNameAttribute
   */
  public CatsClusterCachingAgent(
      String serverGroupsGlob,
      Function<String, Map<String, String>> keyParser,
      Function<Map<String, String>, String> clusterKeyBuilder) {
    this(serverGroupsGlob, keyParser, clusterKeyBuilder, "cluster");
  }

  /**
   * @param serverGroupsGlob The search glob to look up all server groups for the provider (e.g.
   *     Keys.getServerGroupKey("*", "*", "*", "*"))
   * @param keyParser A parser for the server group and cluster keys for the provider
   * @param clusterKeyBuilder Given a parsed server group key as a map, supplies the associated
   *     cluster key
   * @param clusterNameAttribute The attribute name in the parsed cluster key that represents the
   *     cluster's name
   */
  public CatsClusterCachingAgent(
      String serverGroupsGlob,
      Function<String, Map<String, String>> keyParser,
      Function<Map<String, String>, String> clusterKeyBuilder,
      String clusterNameAttribute) {
    this.serverGroupsGlob = serverGroupsGlob;
    this.keyParser = keyParser;
    this.clusterKeyBuilder = clusterKeyBuilder;
    this.clusterNameAttribute = clusterNameAttribute;
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
    Set<String> clusterKeys =
        existing.stream()
            .map(keyParser)
            .filter(Objects::nonNull)
            .map(clusterKeyBuilder)
            .collect(Collectors.toSet());

    Collection<CacheData> clusterData =
        clusterKeys.stream()
            .map(
                s -> {
                  Map<String, String> clusterAttributes = keyParser.apply(s);
                  return new DefaultCacheData(
                      s,
                      Collections.singletonMap("name", clusterAttributes.get(clusterNameAttribute)),
                      Collections.emptyMap());
                })
            .collect(Collectors.toList());

    return new DefaultCacheResult(Collections.singletonMap(CLUSTERS.ns, clusterData));
  }
}
