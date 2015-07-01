/*
 * Copyright 2014 Netflix, Inc.
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

package com.netflix.spinnaker.oort.titan.caching.agents
import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.cats.agent.AgentDataType
import com.netflix.spinnaker.cats.agent.CacheResult
import com.netflix.spinnaker.cats.agent.CachingAgent
import com.netflix.spinnaker.cats.agent.DefaultCacheResult
import com.netflix.spinnaker.cats.cache.CacheData
import com.netflix.spinnaker.cats.provider.ProviderCache
import com.netflix.spinnaker.oort.aws.data.Keys
import com.netflix.spinnaker.oort.titan.TitanClientProvider
import com.netflix.spinnaker.oort.titan.caching.TitanCachingProvider
import com.netflix.spinnaker.oort.titan.credentials.NetflixTitanCredentials
import com.netflix.titanclient.TitanClient
import com.netflix.titanclient.model.Task
import com.netflix.titanclient.model.TaskState
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import static com.netflix.spinnaker.cats.agent.AgentDataType.Authority.AUTHORITATIVE
import static com.netflix.spinnaker.cats.agent.AgentDataType.Authority.INFORMATIVE
import static com.netflix.spinnaker.oort.aws.data.Keys.Namespace.IMAGES
import static com.netflix.spinnaker.oort.aws.data.Keys.Namespace.INSTANCES
import static com.netflix.spinnaker.oort.aws.data.Keys.Namespace.SERVER_GROUPS

class InstanceCachingAgent implements CachingAgent {

  private static final Logger log = LoggerFactory.getLogger(ClusterCachingAgent)

  final Set<AgentDataType> types = Collections.unmodifiableSet([
    AUTHORITATIVE.forType(INSTANCES.ns),
    INFORMATIVE.forType(SERVER_GROUPS.ns),
    INFORMATIVE.forType(IMAGES.ns)
  ] as Set)

  final TitanClientProvider titanClientProvider
  final NetflixTitanCredentials account
  final String region
  final ObjectMapper objectMapper

  InstanceCachingAgent(TitanClientProvider titanClientProvider, NetflixTitanCredentials account, String region, ObjectMapper objectMapper) {
    this.titanClientProvider = titanClientProvider
    this.account = account
    this.region = region
    this.objectMapper = objectMapper
  }

  @Override
  String getProviderName() {
    TitanCachingProvider.PROVIDER_NAME
  }

  @Override
  String getAgentType() {
    "${account.name}/${region}/${InstanceCachingAgent.simpleName}"
  }

  @Override
  Collection<AgentDataType> getProvidedDataTypes() {
    types
  }

  static class MutableCacheData implements CacheData {
    final String id
    int ttlSeconds = -1
    final Map<String, Object> attributes = [:]
    final Map<String, Collection<String>> relationships = [:].withDefault { [] as Set }
    public MutableCacheData(String id) {
      this.id = id
    }
  }

  @Override
  CacheResult loadData(ProviderCache providerCache) {
    // Initialize empty caches
    Closure<Map<String, CacheData>> cache = {
      [:].withDefault { String id -> new MutableCacheData(id) }
    }
    Map<String, CacheData> serverGroups = cache()
    Map<String, CacheData> instances = cache()
    Map<String, CacheData> images = cache()

    // Fetch ALL titan tasks
    TitanClient titanClient = titanClientProvider.getTitanClient(account, region)
    List<Task> tasks = titanClient.getAllTasks()
    for (Task task : tasks) {
      def data = new InstanceData(task, account.name, region)
      if (data.cache) {
        cacheImage(data, images)
        cacheServerGroup(data, serverGroups)
        cacheInstance(data, instances)
      }
    }

    new DefaultCacheResult(
      (SERVER_GROUPS.ns): serverGroups.values(),
      (INSTANCES.ns): instances.values(),
      (IMAGES.ns): images.values()
    )
  }

  private void cacheImage(InstanceData data, Map<String, CacheData> images) {
    images[data.imageId].with {
      relationships[INSTANCES.ns].add(data.instanceId)
    }
  }

  private void cacheServerGroup(InstanceData data, Map<String, CacheData> serverGroups) {
    if (data.serverGroup) {
      serverGroups[data.serverGroup].with {
        relationships[INSTANCES.ns].add(data.instanceId)
      }
    }
  }

  private void cacheInstance(InstanceData data, Map<String, CacheData> instances) {
    instances[data.instanceId].with {
      attributes.task = objectMapper.convertValue(data.task, Task)
      // attributes.put(HEALTH.ns, [getAmazonHealth(data.instance)]) // TODO - add health
      relationships[IMAGES.ns].add(data.imageId)
      if (data.serverGroup) {
        relationships[SERVER_GROUPS.ns].add(data.serverGroup)
      } else {
        relationships[SERVER_GROUPS.ns].clear()
      }
    }

  }

  private static class InstanceData {
    private final Task task
    private final String instanceId
    private final String serverGroup
    private final String imageId
    private final boolean cache

    public InstanceData(Task task, String account, String region) {
      this.task = task
      this.instanceId = Keys.getInstanceKey(task.id, account, region)
      this.serverGroup = task.jobId // TODO - change this to jobName
      this.imageId = task.imageName + ":" + task.imageVersion
      this.cache = !(task.state == TaskState.DEAD)
    }
  }
}
