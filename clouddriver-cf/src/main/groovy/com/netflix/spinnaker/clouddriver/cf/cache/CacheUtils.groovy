/*
 * Copyright 2016 Pivotal, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.cf.cache

import com.netflix.spinnaker.cats.cache.Cache
import com.netflix.spinnaker.cats.cache.CacheData
import com.netflix.spinnaker.cats.cache.RelationshipCacheFilter
import com.netflix.spinnaker.clouddriver.cf.config.CloudFoundryConstants
import com.netflix.spinnaker.clouddriver.cf.model.CloudFoundryApplicationInstance
import com.netflix.spinnaker.clouddriver.cf.model.CloudFoundryCluster
import com.netflix.spinnaker.clouddriver.cf.model.CloudFoundryLoadBalancer
import com.netflix.spinnaker.clouddriver.cf.model.CloudFoundryServerGroup
import com.netflix.spinnaker.clouddriver.cf.model.CloudFoundryService
import com.netflix.spinnaker.clouddriver.cf.provider.ProviderUtils
import com.netflix.spinnaker.clouddriver.model.LoadBalancerInstance
import com.netflix.spinnaker.clouddriver.model.LoadBalancerServerGroup
import org.cloudfoundry.client.lib.domain.CloudApplication
import org.cloudfoundry.client.lib.domain.CloudDomain
import org.cloudfoundry.client.lib.domain.CloudRoute
import org.cloudfoundry.client.lib.domain.CloudService
import org.cloudfoundry.client.lib.domain.InstanceInfo

import static com.netflix.spinnaker.clouddriver.cf.cache.Keys.Namespace.*

class CacheUtils {

  static Collection<CloudFoundryCluster> translateClusters(Cache cacheView, Collection<CacheData> clusterData, boolean includeDetails) {

    Map<String, CloudFoundryLoadBalancer> loadBalancers
    Map<String, CloudFoundryServerGroup> serverGroups

    if (includeDetails) {
      Collection<CacheData> allLoadBalancers = ProviderUtils.resolveRelationshipDataForCollection(cacheView, clusterData, LOAD_BALANCERS.ns)
      Collection<CacheData> allServerGroups = ProviderUtils.resolveRelationshipDataForCollection(cacheView, clusterData, SERVER_GROUPS.ns, RelationshipCacheFilter.include(INSTANCES.ns, LOAD_BALANCERS.ns))

      loadBalancers = translateLoadBalancers(allLoadBalancers)
      serverGroups = translateServerGroups(cacheView, allServerGroups, loadBalancers)
    }

    def clusters = clusterData.collect { CacheData clusterDataEntry ->
      Map<String, String> clusterKey = Keys.parse(clusterDataEntry.id)

      def cluster = new CloudFoundryCluster([
          name: clusterKey.cluster,
          accountName: clusterKey.account
      ])

      if (includeDetails) {
        cluster.loadBalancers = clusterDataEntry.relationships[LOAD_BALANCERS.ns]?.findResults { loadBalancers.get(it) }
        cluster.serverGroups = clusterDataEntry.relationships[SERVER_GROUPS.ns]?.findResults { serverGroups.get(it) }
      } else {
        cluster.loadBalancers = clusterDataEntry.relationships[LOAD_BALANCERS.ns]?.collect { loadBalancerKey ->
          Map parts = Keys.parse(loadBalancerKey)
          new CloudFoundryLoadBalancer(name: parts.loadBalancer, account: parts.account, region: parts.region)
        }
        cluster.serverGroups = clusterDataEntry.relationships[SERVER_GROUPS.ns]?.collect { serverGroupKey ->
          Map parts = Keys.parse(serverGroupKey)
          new CloudFoundryServerGroup(name: parts.serverGroup)
        }
      }

      cluster
    }

    clusters
  }

  static Map<String, CloudFoundryServerGroup> translateServerGroups(Cache cacheView, Collection<CacheData> serverGroupData,
                                                                    Map<String, CloudFoundryLoadBalancer> allLoadBalancers) {
    Collection<CacheData> allInstances = ProviderUtils.resolveRelationshipDataForCollection(cacheView, serverGroupData, INSTANCES.ns)
    Map<String, CloudFoundryApplicationInstance> instances = translateInstances(cacheView, allInstances)

    serverGroupData.collectEntries { serverGroupEntry ->
      def account = Keys.parse(serverGroupEntry.id).account
      def serverGroup = translateServerGroup(serverGroupEntry, instances.values(), allLoadBalancers.values().findAll {it.account == account})
      [(serverGroupEntry.id) : serverGroup]
    }
  }

  static CloudFoundryServerGroup translateServerGroup(CacheData serverGroupEntry,
                                                      Collection<CloudFoundryApplicationInstance> instances,
                                                      Collection<CloudFoundryLoadBalancer> loadBalancers) {

    def serverGroup = new CloudFoundryServerGroup(serverGroupEntry.attributes)

    serverGroup.nativeApplication = (serverGroupEntry.attributes.nativeApplication instanceof CloudApplication) ? serverGroupEntry.attributes.nativeApplication : ProviderUtils.buildNativeApplication(serverGroupEntry.attributes.nativeApplication)

    serverGroup.services = serverGroupEntry.attributes.services.collect {
      new CloudFoundryService([
          type: it.type,
          id: it.id,
          name: it.name,
          application: it.application,
          accountName: it.accountName,
          region: it.region,
          nativeService: (it.nativeService instanceof CloudService) ? it.nativeService : ProviderUtils.buildNativeService(it.nativeService)
      ])
    }

    serverGroup.buildInfo = [
        commit: serverGroup.nativeApplication.envAsMap[CloudFoundryConstants.COMMIT_HASH],
        branch: serverGroup.nativeApplication.envAsMap[CloudFoundryConstants.COMMIT_BRANCH],
        package_name: serverGroup.nativeApplication.envAsMap[CloudFoundryConstants.PACKAGE],
        jenkins: [
            fullUrl: serverGroup.nativeApplication.envAsMap[CloudFoundryConstants.JENKINS_HOST],
            name: serverGroup.nativeApplication.envAsMap[CloudFoundryConstants.JENKINS_NAME],
            number: serverGroup.nativeApplication.envAsMap[CloudFoundryConstants.JENKINS_BUILD]
        ]
    ]

    serverGroup.instances = (instances) ? instances.findAll { it.name.contains(serverGroup.name) } : [] as Set
    serverGroup.nativeLoadBalancers = serverGroup.nativeApplication.envAsMap[CloudFoundryConstants.LOAD_BALANCERS]?.split(',').collect { route ->
      loadBalancers.find { it.name == route }
    }

    serverGroup.disabled = !serverGroup.nativeApplication.uris?.findResult { uri ->
      def lbs = serverGroup.nativeLoadBalancers.collect { loadBalancer ->
        (loadBalancer?.nativeRoute?.name) ? loadBalancer.nativeRoute.name : ''
      }
      lbs.contains(uri)
    }

    serverGroup.nativeLoadBalancers.each {
      it.serverGroups.add(new LoadBalancerServerGroup(
          name      :        serverGroup.name,
          isDisabled:        serverGroup.isDisabled(),
          detachedInstances: [] as Set,
          instances :        serverGroup.instances.collect {
            new LoadBalancerInstance(
                id: it.name,
                zone: it.zone,
                health: it.health?.get(0)
            )
          },
      ))
    }

    serverGroup
  }

  static Map<String, CloudFoundryApplicationInstance> translateInstances(Cache cacheView, Collection<CacheData> instanceData) {
    instanceData?.collectEntries { instanceEntry ->
      CacheData serverGroup = ProviderUtils.resolveRelationshipData(cacheView, instanceEntry, SERVER_GROUPS.ns)[0]
      [(instanceEntry.id): translateInstance(instanceEntry, serverGroup)]
    } ?: [:]
  }

  static CloudFoundryApplicationInstance translateInstance(CacheData instanceEntry, CacheData serverGroup) {
    def instance = new CloudFoundryApplicationInstance(
        name: instanceEntry.attributes.name.toString(),
        nativeInstance: (instanceEntry.attributes.nativeInstance instanceof InstanceInfo) ? instanceEntry.attributes.nativeInstance : new InstanceInfo(instanceEntry.attributes.nativeInstance as Map<String, Object>),
        nativeApplication: (serverGroup.attributes.nativeApplication instanceof CloudApplication) ? serverGroup.attributes.nativeApplication : ProviderUtils.buildNativeApplication(serverGroup.attributes.nativeApplication as Map)
    )
    instance.consoleLink = serverGroup.attributes.consoleLink
    instance.logsLink = serverGroup.attributes.logsLink
    instance.healthState = CloudFoundryApplicationInstance.instanceStateToHealthState(instance.nativeInstance.state)
    instance.health = CloudFoundryApplicationInstance.createInstanceHealth(instance)
    instance
  }



  static Map<String, CloudFoundryLoadBalancer> translateLoadBalancers(Collection<CacheData> loadBalancerData) {
    loadBalancerData.collectEntries { loadBalancerEntry ->
      [(loadBalancerEntry.id): translateLoadBalancer(loadBalancerEntry)]
    }
  }

  static CloudFoundryLoadBalancer translateLoadBalancer(CacheData loadBalancerEntry) {
    Map<String, String> lbKey = Keys.parse(loadBalancerEntry.id)
    def route = new CloudRoute(
        ProviderUtils.mapToMeta(loadBalancerEntry.attributes.nativeRoute.meta),
        loadBalancerEntry.attributes.nativeRoute.host,
        new CloudDomain(ProviderUtils.mapToMeta(loadBalancerEntry.attributes.nativeRoute.domain.meta), loadBalancerEntry.attributes.nativeRoute.domain.name, null),
        loadBalancerEntry.attributes.nativeRoute.appsUsingRoute
    )
    new CloudFoundryLoadBalancer(name: lbKey.loadBalancer, account: lbKey.account, region: lbKey.region, nativeRoute: route)
  }


}
