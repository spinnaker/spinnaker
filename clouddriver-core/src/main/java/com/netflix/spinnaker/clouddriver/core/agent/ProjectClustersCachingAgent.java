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
package com.netflix.spinnaker.clouddriver.core.agent;

import static com.netflix.spinnaker.cats.agent.AgentDataType.Authority.AUTHORITATIVE;
import static com.netflix.spinnaker.clouddriver.core.provider.agent.Namespace.PROJECT_CLUSTERS;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.netflix.spinnaker.cats.agent.AgentDataType;
import com.netflix.spinnaker.cats.agent.CacheResult;
import com.netflix.spinnaker.cats.agent.CachingAgent;
import com.netflix.spinnaker.cats.agent.DefaultCacheResult;
import com.netflix.spinnaker.cats.cache.CacheData;
import com.netflix.spinnaker.cats.provider.ProviderCache;
import com.netflix.spinnaker.clouddriver.cache.CustomScheduledAgent;
import com.netflix.spinnaker.clouddriver.config.ProjectClustersCachingAgentProperties;
import com.netflix.spinnaker.clouddriver.core.ProjectClustersService;
import com.netflix.spinnaker.clouddriver.core.provider.CoreProvider;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class ProjectClustersCachingAgent implements CachingAgent, CustomScheduledAgent {

  private static final long DEFAULT_POLL_INTERVAL_MILLIS = TimeUnit.MINUTES.toMillis(1);
  private static final long DEFAULT_TIMEOUT_MILLIS = TimeUnit.MINUTES.toMillis(30);

  private final Collection<AgentDataType> types =
      Collections.singletonList(AUTHORITATIVE.forType(PROJECT_CLUSTERS.ns));

  private final ProjectClustersService projectClustersService;
  private final ProjectClustersCachingAgentProperties properties;

  public ProjectClustersCachingAgent(
      ProjectClustersService projectClustersService,
      ProjectClustersCachingAgentProperties properties) {
    this.projectClustersService = projectClustersService;
    this.properties = properties;
  }

  @Override
  public Collection<AgentDataType> getProvidedDataTypes() {
    return types;
  }

  @Override
  public CacheResult loadData(ProviderCache providerCache) {
    return new DefaultCacheResult(
        Collections.singletonMap(
            PROJECT_CLUSTERS.ns,
            Collections.singletonList(
                new MutableCacheData(
                    "v1",
                    new HashMap<>(
                        projectClustersService.getProjectClusters(
                            properties.getNormalizedAllowList())),
                    Collections.emptyMap()))));
  }

  static class MutableCacheData implements CacheData {

    private final String id;
    private final int ttlSeconds = -1;
    private final Map<String, Object> attributes = new HashMap<>();
    private final Map<String, Collection<String>> relationships = new HashMap<>();

    public MutableCacheData(String id) {
      this.id = id;
    }

    @JsonCreator
    public MutableCacheData(
        String id, Map<String, Object> attributes, Map<String, Collection<String>> relationships) {
      this.id = id;
      this.attributes.putAll(attributes);
      this.relationships.putAll(relationships);
    }

    @Override
    public String getId() {
      return id;
    }

    @Override
    public int getTtlSeconds() {
      return ttlSeconds;
    }

    @Override
    public Map<String, Object> getAttributes() {
      return attributes;
    }

    @Override
    public Map<String, Collection<String>> getRelationships() {
      return relationships;
    }
  }

  @Override
  public long getPollIntervalMillis() {
    return DEFAULT_POLL_INTERVAL_MILLIS;
  }

  @Override
  public long getTimeoutMillis() {
    return DEFAULT_TIMEOUT_MILLIS;
  }

  @Override
  public String getAgentType() {
    return ProjectClustersCachingAgent.class.getSimpleName();
  }

  @Override
  public String getProviderName() {
    return CoreProvider.PROVIDER_NAME;
  }
}
