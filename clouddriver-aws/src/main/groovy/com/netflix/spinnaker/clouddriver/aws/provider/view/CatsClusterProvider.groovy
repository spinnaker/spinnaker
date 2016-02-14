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

package com.netflix.spinnaker.clouddriver.aws.provider.view

import com.netflix.frigga.ami.AppVersion
import com.netflix.spinnaker.cats.cache.Cache
import com.netflix.spinnaker.cats.cache.CacheData
import com.netflix.spinnaker.cats.cache.CacheFilter
import com.netflix.spinnaker.cats.cache.RelationshipCacheFilter
import com.netflix.spinnaker.clouddriver.model.ClusterProvider
import com.netflix.spinnaker.clouddriver.aws.data.Keys
import com.netflix.spinnaker.clouddriver.aws.model.*
import com.netflix.spinnaker.clouddriver.aws.provider.AwsProvider
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

import static com.netflix.spinnaker.clouddriver.aws.data.Keys.Namespace.*

@Component
class CatsClusterProvider implements ClusterProvider<AmazonCluster> {

  private final Cache cacheView
  private final AwsProvider awsProvider

  @Value('${default.build.host:http://builds.netflix.com/}')
  String defaultBuildHost

  @Autowired
  CatsClusterProvider(Cache cacheView, AwsProvider awsProvider) {
    this.cacheView = cacheView
    this.awsProvider = awsProvider
  }

  @Override
  Map<String, Set<AmazonCluster>> getClusters() {
    Collection<CacheData> clusterData = cacheView.getAll(CLUSTERS.ns)
    Collection<AmazonCluster> clusters = translateClusters(clusterData, false)
    mapResponse(clusters)
  }

  @Override
  Map<String, Set<AmazonCluster>> getClusterSummaries(String applicationName) {
    getClusters0(applicationName, false)
  }

  @Override
  Map<String, Set<AmazonCluster>> getClusterDetails(String applicationName) {
    getClusters0(applicationName, true)
  }

  @Override
  AmazonServerGroup getServerGroup(String account, String region, String name) {
    String serverGroupKey = Keys.getServerGroupKey(name, account, region)
    CacheData serverGroupData = cacheView.get(SERVER_GROUPS.ns, serverGroupKey)
    if (serverGroupData == null) {
      return null
    }

    def asg = serverGroupData.attributes["asg"]

    String launchConfigKey = Keys.getLaunchConfigKey(serverGroupData?.attributes['launchConfigName'] as String, account, region )
    CacheData launchConfigs = cacheView.get(LAUNCH_CONFIGS.ns, launchConfigKey)

    String imageId = launchConfigs?.attributes?.get('imageId')
    CacheData imageConfigs = imageId ? cacheView.get(IMAGES.ns, Keys.getImageKey(imageId, account, region)) : null

    def serverGroup = new AmazonServerGroup(name: name, region: region)
    serverGroup.accountName = account
    serverGroup.zones = serverGroupData.attributes["zones"]
    serverGroup.launchConfig = launchConfigs ? launchConfigs.attributes : null
    serverGroup.image = imageConfigs ? imageConfigs.attributes : null
    serverGroup.asg = asg
    serverGroup.scalingPolicies = serverGroupData.attributes["scalingPolicies"]
    serverGroup.scheduledActions = serverGroupData.attributes["scheduledActions"]
    Set<String> asgInstances = getAsgInstanceKeys(asg, account, region)
    Closure<Boolean> instanceFilter = { rel ->
      return (asgInstances == null || asgInstances.contains(rel))
    }
    serverGroup.instances = translateInstances(resolveRelationshipData(serverGroupData, INSTANCES.ns, instanceFilter)).values()

    serverGroup
  }

  private static Map<String, Set<AmazonCluster>> mapResponse(Collection<AmazonCluster> clusters) {
    clusters.groupBy { it.accountName }.collectEntries { k, v -> [k, new HashSet(v)] }
  }

  private static Set<String> getAsgInstanceKeys(Map asg, String account, String region) {
    asg?.instances?.inject(new HashSet<String>()) { Set instances, Map instance ->
      instances.add(Keys.getInstanceKey(instance.instanceId, account, region))
      return instances
    } ?: []
  }

  private static Map<String, AmazonLoadBalancer> translateLoadBalancers(Collection<CacheData> loadBalancerData) {
    loadBalancerData.collectEntries { loadBalancerEntry ->
      Map<String, String> lbKey = Keys.parse(loadBalancerEntry.id)
      [(loadBalancerEntry.id) : new AmazonLoadBalancer(name: lbKey.loadBalancer, account: lbKey.account, region: lbKey.region)]
    }
  }

  private Collection<AmazonCluster> translateClusters(Collection<CacheData> clusterData, boolean includeDetails) {

    Map<String, AmazonLoadBalancer> loadBalancers
    Map<String, AmazonServerGroup> serverGroups

    if (includeDetails) {
      Collection<CacheData> allLoadBalancers = resolveRelationshipDataForCollection(clusterData, LOAD_BALANCERS.ns)
      Collection<CacheData> allServerGroups = resolveRelationshipDataForCollection(clusterData, SERVER_GROUPS.ns, RelationshipCacheFilter.include(INSTANCES.ns, LAUNCH_CONFIGS.ns))

      loadBalancers = translateLoadBalancers(allLoadBalancers)
      serverGroups = translateServerGroups(allServerGroups)
    }

    Collection<AmazonCluster> clusters = clusterData.collect { CacheData clusterDataEntry ->
      Map<String, String> clusterKey = Keys.parse(clusterDataEntry.id)

      def cluster = new AmazonCluster()
      cluster.accountName = clusterKey.account
      cluster.name = clusterKey.cluster
      if (includeDetails) {
        cluster.loadBalancers = clusterDataEntry.relationships[LOAD_BALANCERS.ns]?.findResults { loadBalancers.get(it) }
        cluster.serverGroups = clusterDataEntry.relationships[SERVER_GROUPS.ns]?.findResults { serverGroups.get(it) }
      } else {
        cluster.loadBalancers = clusterDataEntry.relationships[LOAD_BALANCERS.ns]?.collect { loadBalancerKey ->
          Map parts = Keys.parse(loadBalancerKey)
          new AmazonLoadBalancer(name: parts.loadBalancer, account: parts.account, region: parts.region)
        }
        cluster.serverGroups = clusterDataEntry.relationships[SERVER_GROUPS.ns]?.collect { serverGroupKey ->
          Map parts = Keys.parse(serverGroupKey)
          new AmazonServerGroup(name: parts.serverGroup, region: parts.region)
        }
      }
      cluster
    }

    clusters
  }

  private Map<String, Set<AmazonCluster>> getClusters0(String applicationName, boolean includeDetails) {
    CacheData application = cacheView.get(APPLICATIONS.ns, Keys.getApplicationKey(applicationName))
    if (application == null) {
      return null
    }
    Collection<AmazonCluster> clusters = translateClusters(resolveRelationshipData(application, CLUSTERS.ns), includeDetails)
    mapResponse(clusters)
  }

  private Map<String, AmazonServerGroup> translateServerGroups(Collection<CacheData> serverGroupData) {
    Collection<CacheData> allInstances = resolveRelationshipDataForCollection(serverGroupData, INSTANCES.ns, RelationshipCacheFilter.none())

    Map<String, AmazonInstance> instances = translateInstances(allInstances)

    Map<String, AmazonServerGroup> serverGroups = serverGroupData.collectEntries { serverGroupEntry ->
      Map<String, String> serverGroupKey = Keys.parse(serverGroupEntry.id)

      def serverGroup = new AmazonServerGroup(serverGroupEntry.attributes)
      def asg = serverGroupEntry.attributes.asg

      Set<String> asgInstanceSet = getAsgInstanceKeys(asg, serverGroupKey.account, serverGroupKey.region)
      serverGroup.instances = serverGroupEntry.relationships[INSTANCES.ns]?.findResults {
        if (asgInstanceSet != null && !asgInstanceSet.contains(it)) {
          return null
        }
        instances.get(it)
      } ?: []
      [(serverGroupEntry.id) : serverGroup]
    }

    Map<String, String> launchConfigurations = serverGroupData.findAll { it.relationships[LAUNCH_CONFIGS.ns] }.collectEntries {
      [(it.relationships[LAUNCH_CONFIGS.ns].first()) : it.id]
    }
    Collection<CacheData> launchConfigs = cacheView.getAll(LAUNCH_CONFIGS.ns, launchConfigurations.keySet())
    Map<String, Collection<String>> allImages = [:]
    launchConfigs.each { launchConfig ->
      def serverGroupId = launchConfigurations[launchConfig.id]
      def imageId = launchConfig.relationships[IMAGES.ns]?.first()
      serverGroups[serverGroupId].launchConfig = launchConfig.attributes
      if (imageId) {
        if (!allImages.containsKey(imageId)) {
          allImages.put(imageId, [])
        }
        allImages[imageId] << serverGroupId
      }
    }
    Collection<CacheData> images = cacheView.getAll(IMAGES.ns, allImages.keySet())
    images.each { image ->
      def serverGroupIds = allImages[image.id]
      Map buildInfo = null

      String appVersionTag = image.attributes.tags?.find { it.key == "appversion" }?.value
      if (appVersionTag) {
        def appVersion = AppVersion.parseName(appVersionTag)
        if (appVersion) {
          buildInfo = [package_name: appVersion.packageName, version: appVersion.version, commit: appVersion.commit] as Map<Object, Object>
          if (appVersion.buildJobName) {
            buildInfo.jenkins = [name: appVersion.buildJobName, number: appVersion.buildNumber]
          }
          def buildHost = image.attributes.tags.find { it.key == "build_host" }?.value ?: defaultBuildHost
          if (buildHost && buildInfo.containsKey("jenkins")) {
            ((Map) buildInfo.jenkins).host = buildHost
          }
        }
      }

      serverGroupIds.each { serverGroupId ->
        def serverGroup = serverGroups[serverGroupId]
        serverGroup.image = image.attributes
        serverGroup.buildInfo = buildInfo
      }
    }

    serverGroups
  }

  private Map<String, AmazonInstance> translateInstances(Collection<CacheData> instanceData) {
    Map<String, AmazonInstance> instances = instanceData?.collectEntries { instanceEntry ->
      AmazonInstance instance = new AmazonInstance(instanceEntry.attributes)
      instance.name = instanceEntry.attributes.instanceId.toString()
      [(instanceEntry.id): instance]
    } ?: [:]
    addHealthToInstances(instanceData, instances)

    instances
  }

  private void addHealthToInstances(Collection<CacheData> instanceData, Map<String, AmazonInstance> instances) {
    Map<String, String> healthKeysToInstance = [:]
    instanceData.each { instanceEntry ->
      Map<String, String> instanceKey = Keys.parse(instanceEntry.id)
      awsProvider.healthAgents.each {
        def key = Keys.getInstanceHealthKey(instanceKey.instanceId, instanceKey.account, instanceKey.region, it.healthId)
        healthKeysToInstance.put(key, instanceEntry.id)
      }
    }

    Collection<CacheData> healths = cacheView.getAll(HEALTH.ns, healthKeysToInstance.keySet(), RelationshipCacheFilter.none())
    healths.each { healthEntry ->
      def instanceId = healthKeysToInstance.get(healthEntry.id)
      instances[instanceId].health << healthEntry.attributes
    }

    instances.values().each { instance ->
      instance.isHealthy = instance.health.any { it.state == 'Up' } && instance.health.every { it.state == 'Up' || it.state == 'Unknown' }
    }
  }

  private Collection<CacheData> resolveRelationshipDataForCollection(Collection<CacheData> sources, String relationship, CacheFilter cacheFilter = null) {
    Collection<String> relationships = sources?.findResults { it.relationships[relationship]?: [] }?.flatten() ?: []
    relationships ? cacheView.getAll(relationship, relationships, cacheFilter) : []
  }

  private Collection<CacheData> resolveRelationshipData(CacheData source, String relationship) {
    resolveRelationshipData(source, relationship) { true }
  }

  private Collection<CacheData> resolveRelationshipData(CacheData source, String relationship, Closure<Boolean> relFilter) {
    Collection<String> filteredRelationships = source.relationships[relationship]?.findAll(relFilter)
    filteredRelationships ? cacheView.getAll(relationship, filteredRelationships) : []
  }

  @Override
  Set<AmazonCluster> getClusters(String applicationName, String account) {
    CacheData application = cacheView.get(APPLICATIONS.ns, Keys.getApplicationKey(applicationName), RelationshipCacheFilter.include(CLUSTERS.ns))
    if (application == null) {
      return [] as Set
    }
    Collection<String> clusterKeys = application.relationships[CLUSTERS.ns].findAll { Keys.parse(it).account == account }
    Collection<CacheData> clusters = cacheView.getAll(CLUSTERS.ns, clusterKeys)
    translateClusters(clusters, true) as Set<AmazonCluster>
  }

  @Override
  AmazonCluster getCluster(String application, String account, String name) {
    CacheData cluster = cacheView.get(CLUSTERS.ns, Keys.getClusterKey(name, application, account))
    if (cluster == null) {
      null
    } else {
      translateClusters([cluster], true)[0]
    }
  }
}
