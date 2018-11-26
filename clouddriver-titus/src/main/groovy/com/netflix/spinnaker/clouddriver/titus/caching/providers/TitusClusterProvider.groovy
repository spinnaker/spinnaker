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

package com.netflix.spinnaker.clouddriver.titus.caching.providers

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.cats.cache.Cache
import com.netflix.spinnaker.cats.cache.CacheData
import com.netflix.spinnaker.cats.cache.CacheFilter
import com.netflix.spinnaker.cats.cache.RelationshipCacheFilter
import com.netflix.spinnaker.clouddriver.core.provider.agent.ExternalHealthProvider
import com.netflix.spinnaker.clouddriver.model.ClusterProvider
import com.netflix.spinnaker.clouddriver.model.ServerGroupProvider
import com.netflix.spinnaker.clouddriver.titus.TitusCloudProvider
import com.netflix.spinnaker.clouddriver.titus.caching.Keys
import com.netflix.spinnaker.clouddriver.titus.caching.TitusCachingProvider
import com.netflix.spinnaker.clouddriver.titus.caching.utils.AwsLookupUtil
import com.netflix.spinnaker.clouddriver.titus.caching.utils.CachingSchema
import com.netflix.spinnaker.clouddriver.titus.caching.utils.CachingSchemaUtil
import com.netflix.spinnaker.clouddriver.titus.client.model.Job
import com.netflix.spinnaker.clouddriver.titus.client.model.Task
import com.netflix.spinnaker.clouddriver.titus.model.TitusCluster
import com.netflix.spinnaker.clouddriver.titus.model.TitusInstance
import com.netflix.spinnaker.clouddriver.titus.model.TitusServerGroup
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

import static com.netflix.spinnaker.clouddriver.core.provider.agent.Namespace.HEALTH
import static com.netflix.spinnaker.clouddriver.titus.caching.Keys.Namespace.*

@Component
class TitusClusterProvider implements ClusterProvider<TitusCluster>, ServerGroupProvider {

  private final TitusCloudProvider titusCloudProvider
  private final Cache cacheView
  private final TitusCachingProvider titusCachingProvider
  private final ObjectMapper objectMapper
  private final Logger log = LoggerFactory.getLogger(getClass())

  @Autowired
  private final AwsLookupUtil awsLookupUtil

  @Autowired
  private final CachingSchemaUtil cachingSchemaUtil

  @Autowired
  TitusClusterProvider(TitusCloudProvider titusCloudProvider,
                       TitusCachingProvider titusCachingProvider,
                       Cache cacheView,
                       ObjectMapper objectMapper) {
    this.titusCloudProvider = titusCloudProvider
    this.cacheView = cacheView
    this.titusCachingProvider = titusCachingProvider
    this.objectMapper = objectMapper
  }

  @Autowired(required = false)
  List<ExternalHealthProvider> externalHealthProviders

  /**
   *
   * @return
   */
  @Override
  Map<String, Set<TitusCluster>> getClusters() {
    Collection<CacheData> clusterData = cacheView.getAll(CLUSTERS.ns)
    Collection<TitusCluster> clustersList = translateClusters(clusterData, false)
    Map<String, Set<TitusCluster>> clusters = clustersList.groupBy {
      it.accountName
    }.collectEntries { k, v -> [k, new HashSet(v)] }
    clusters
  }

  /**
   *
   * @param applicationName
   * @return
   */
  @Override
  Map<String, Set<TitusCluster>> getClusterSummaries(String applicationName) {
    Map<String, Set<TitusCluster>> clusters = getClustersInternal(applicationName, false)
    clusters
  }

  /**
   *
   * @param applicationName
   * @return
   */
  @Override
  Map<String, Set<TitusCluster>> getClusterDetails(String applicationName) {
    Map<String, Set<TitusCluster>> clusters = getClustersInternal(applicationName, true)
    clusters
  }

  /**
   *
   * @param applicationName
   * @param account
   * @return clusters with the instances
   */
  @Override
  Set<TitusCluster> getClusters(String applicationName, String account) {
    return getClusters(applicationName, account, true)
  }

  /**
   *
   * @param applicationName
   * @param account
   * @return
   */
  @Override
  Set<TitusCluster> getClusters(String applicationName, String account, boolean includeDetails) {
    CacheData application = cacheView.get(APPLICATIONS.ns, Keys.getApplicationKey(applicationName),
      RelationshipCacheFilter.include(CLUSTERS.ns))
    if (application == null) {
      return [] as Set
    }
    Collection<String> clusterKeys = application.relationships[CLUSTERS.ns].findAll {
      Keys.parse(it).account == account
    }
    Collection<CacheData> clusters = cacheView.getAll(CLUSTERS.ns, clusterKeys)
    translateClusters(clusters, includeDetails) as Set<TitusCluster>
  }

  /**
   *
   * @param application
   * @param account
   * @param name
   * @return
   */
  @Override
  TitusCluster getCluster(String application, String account, String name, boolean includeDetails) {
    String clusterKey = (cachingSchemaUtil.getCachingSchemaForAccount(account) == CachingSchema.V1
      ? Keys.getClusterKey(name, application, account)
      : Keys.getClusterV2Key(name, application, account))
    CacheData cluster = cacheView.get(CLUSTERS.ns, clusterKey)
    TitusCluster titusCluster = cluster ? translateClusters([cluster], includeDetails)[0] : null
    titusCluster
  }

  @Override
  TitusCluster getCluster(String application, String account, String name) {
    return getCluster(application, account, name, true)
  }

  /**
   *
   * @param account
   * @param region
   * @param name
   * @return
   */
  @Override
  TitusServerGroup getServerGroup(String account, String region, String name, boolean includeDetails) {
    String serverGroupKey = (cachingSchemaUtil.getCachingSchemaForAccount(account) == CachingSchema.V1
      ? Keys.getServerGroupKey(name, account, region)
      : Keys.getServerGroupV2Key(name, account, region))
    CacheData serverGroupData = cacheView.get(SERVER_GROUPS.ns, serverGroupKey)
    if (serverGroupData == null) {
      return null
    }
    String json = objectMapper.writeValueAsString(serverGroupData.attributes.job)
    Job job = objectMapper.readValue(json, Job)

    TitusServerGroup serverGroup = new TitusServerGroup(job, serverGroupData.attributes.account, serverGroupData.attributes.region)
    serverGroup.placement.account = account
    serverGroup.placement.region = region
    serverGroup.scalingPolicies = serverGroupData.attributes.scalingPolicies
    serverGroup.targetGroups = serverGroupData.attributes.targetGroups
    if (includeDetails) {
      serverGroup.instances = translateInstances(resolveRelationshipData(serverGroupData, INSTANCES.ns), Collections.singletonList(serverGroupData)).values()
      if (serverGroup.targetGroups) {
        awsLookupUtil.lookupTargetGroupHealth(job, serverGroup.instances)
      }
    }
    serverGroup.accountId = awsLookupUtil.awsAccountId(account, region)
    serverGroup.awsAccount = awsLookupUtil.lookupAccount(account, region)?.awsAccount
    serverGroup
  }

  @Override
  TitusServerGroup getServerGroup(String account, String region, String name) {
    return getServerGroup(account, region, name, true);
  }

  @Override
  String getCloudProviderId() {
    return titusCloudProvider.id
  }

  @Override
  boolean supportsMinimalClusters() {
    return true
  }

  @Override
  Collection<String> getServerGroupIdentifiers(String account, String region) {
    account = Optional.ofNullable(account).orElse("*")
    region = Optional.ofNullable(region).orElse("*")

    return cacheView.filterIdentifiers(SERVER_GROUPS.ns, Keys.getServerGroupKey("*", "*", account, region))
  }

  @Override
  String buildServerGroupIdentifier(String account, String region, String serverGroupName) {
    return Keys.getServerGroupKey(serverGroupName, account, region)
  }

  // Private methods
  private Map<String, Set<TitusCluster>> getClustersInternal(String applicationName, boolean includeDetails) {
    CacheData application = cacheView.get(APPLICATIONS.ns, Keys.getApplicationKey(applicationName))
    if (application == null) return null
    Collection<TitusCluster> clusters = translateClusters(resolveRelationshipData(application, CLUSTERS.ns), includeDetails)
    clusters.groupBy { it.accountName }.collectEntries { k, v -> [k, new HashSet(v)] }
  }

  /**
   * Translate clusters
   */
  private Collection<TitusCluster> translateClusters(Collection<CacheData> clusterData, boolean includeDetails) {
    def relationshipFilter = includeDetails ? RelationshipCacheFilter.include(INSTANCES.ns) : RelationshipCacheFilter.none()
    Collection<CacheData> allServerGroups = resolveRelationshipDataForCollection(clusterData, SERVER_GROUPS.ns, relationshipFilter)
    Map<String, TitusServerGroup> serverGroups = translateServerGroups(allServerGroups)

    Collection<TitusCluster> clusters = clusterData.collect { CacheData clusterDataEntry ->
      Map<String, String> clusterKey = Keys.parse(clusterDataEntry.id)
      TitusCluster cluster = new TitusCluster()
      cluster.accountName = clusterKey.account
      cluster.name = clusterKey.cluster
      cluster.serverGroups = clusterDataEntry.relationships[SERVER_GROUPS.ns]?.findResults { serverGroups.get(it) }
      cluster
    }
    return clusters
  }

  /**
   * Translate server groups
   */
  private Map<String, TitusServerGroup> translateServerGroups(Collection<CacheData> serverGroupData) {
    Collection<CacheData> allInstances = resolveRelationshipDataForCollection(serverGroupData, INSTANCES.ns, RelationshipCacheFilter.include(SERVER_GROUPS.ns))

    Map<String, TitusInstance> instances = translateInstances(allInstances, serverGroupData)

    Map<String, TitusServerGroup> serverGroups = serverGroupData.collectEntries { serverGroupEntry ->
      String json = objectMapper.writeValueAsString(serverGroupEntry.attributes.job)
      Job job = objectMapper.readValue(json, Job)

      TitusServerGroup serverGroup = new TitusServerGroup(job, serverGroupEntry.attributes.account, serverGroupEntry.attributes.region)
      serverGroup.instances = serverGroupEntry.relationships[INSTANCES.ns]?.findResults { instances.get(it) } as Set

      if (!serverGroup.instances && serverGroupEntry.attributes.tasks) {
        // has no direct instance relationships but we can partially populate instances based on attributes.tasks
        serverGroup.instances = serverGroupEntry.attributes.tasks.collect {
          new TitusInstance(it as Map)
        }
      }
      serverGroup.instances = serverGroup.instances ?: []
      serverGroup.targetGroups = serverGroupEntry.attributes.targetGroups
      if (serverGroup.targetGroups) {
        awsLookupUtil.lookupTargetGroupHealth(job, serverGroup.instances)
      }
      serverGroup.awsAccount = awsLookupUtil.lookupAccount(serverGroupEntry.attributes.account, serverGroupEntry.attributes.region)?.awsAccount
      [(serverGroupEntry.id): serverGroup]
    }
    return serverGroups
  }

  /**
   * Translate instances
   */
  private Map<String, TitusInstance> translateInstances(Collection<CacheData> instanceData, Collection<CacheData> serverGroupData) {
    Map<String, Job> jobData = serverGroupData.collectEntries { cacheData ->
      Job job = objectMapper.convertValue(cacheData.getAttributes().job, Job)
      [job.id, job]
    }
    Map<String, TitusInstance> instances = instanceData.collectEntries { instanceEntry ->
      Task task = objectMapper.convertValue(instanceEntry.attributes.task, Task)

      Job job
      if (instanceEntry.attributes.job != null) {
        if (instanceEntry.relationships[SERVER_GROUPS.ns]
            && !instanceEntry.relationships[SERVER_GROUPS.ns].empty) {
          // job needs to be loaded because it was cached separately
          job = jobData.get(instanceEntry.attributes.jobId)
        } else {
          job = objectMapper.convertValue(instanceEntry.attributes.job, Job)
        }

        TitusInstance instance = new TitusInstance(job, task)
        instance.health = instanceEntry.attributes[HEALTH.ns]
        [(instanceEntry.id): instance]
      } else {
        log.error("Job id is null for instance {}. Are there two jobs with the same server group name?", instanceEntry.id)
        [:]
      }
    }.findAll { it.key != null }

    Map<String, String> healthKeysToInstance = [:]
    instanceData.each { instanceEntry ->
      externalHealthProviders.each { externalHealthProvider ->
        externalHealthProvider.agents.each { externalHealthAgent ->
          def key = Keys.getInstanceHealthKey(instanceEntry.attributes.task.instanceId, externalHealthAgent.healthId)
          healthKeysToInstance.put(key, instanceEntry.id)
        }
      }
    }
    Collection<CacheData> healths = cacheView.getAll(HEALTH.ns, healthKeysToInstance.keySet(), RelationshipCacheFilter.none())
    healths.each { healthEntry ->
      def instanceId = healthKeysToInstance.get(healthEntry.id)
      healthEntry.attributes.remove('lastUpdatedTimestamp')
      instances[instanceId].health << healthEntry.attributes
    }
    return instances
  }

  // Resolving cache data relationships

  private Collection<CacheData> resolveRelationshipDataForCollection(Collection<CacheData> sources, String relationship, CacheFilter cacheFilter = null) {
    Collection<String> relationships = sources.findResults { it.relationships[relationship] ?: [] }.flatten()
    relationships ? cacheView.getAll(relationship, relationships, cacheFilter) : []
  }

  private Collection<CacheData> resolveRelationshipData(CacheData source, String relationship) {
    resolveRelationshipData(source, relationship) { true }
  }

  private Collection<CacheData> resolveRelationshipData(CacheData source, String relationship, Closure<Boolean> relFilter) {
    Collection<String> filteredRelationships = source.relationships[relationship]?.findAll(relFilter)
    filteredRelationships ? cacheView.getAll(relationship, filteredRelationships) : []
  }
}
