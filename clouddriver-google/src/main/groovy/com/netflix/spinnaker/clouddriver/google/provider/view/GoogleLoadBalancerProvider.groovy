/*
 * Copyright 2016 Google, Inc.
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

package com.netflix.spinnaker.clouddriver.google.provider.view

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.cats.cache.Cache
import com.netflix.spinnaker.cats.cache.CacheData
import com.netflix.spinnaker.cats.cache.RelationshipCacheFilter
import com.netflix.spinnaker.clouddriver.google.cache.Keys
import com.netflix.spinnaker.clouddriver.google.model.GoogleServerGroup
import com.netflix.spinnaker.clouddriver.google.model.callbacks.Utils
import com.netflix.spinnaker.clouddriver.google.model.health.GoogleLoadBalancerHealth
import com.netflix.spinnaker.clouddriver.google.model.loadbalancing.GoogleHttpLoadBalancer
import com.netflix.spinnaker.clouddriver.google.model.loadbalancing.GoogleLoadBalancer
import com.netflix.spinnaker.clouddriver.google.model.loadbalancing.GoogleLoadBalancerType
import com.netflix.spinnaker.clouddriver.google.model.loadbalancing.GoogleLoadBalancerView
import com.netflix.spinnaker.clouddriver.model.LoadBalancerInstance
import com.netflix.spinnaker.clouddriver.model.LoadBalancerProvider
import com.netflix.spinnaker.clouddriver.model.LoadBalancerServerGroup
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

import static com.netflix.spinnaker.clouddriver.google.cache.Keys.Namespace.*

@Component
class GoogleLoadBalancerProvider implements LoadBalancerProvider<GoogleLoadBalancerView> {

  @Autowired
  Cache cacheView
  @Autowired
  ObjectMapper objectMapper

  @Override
  Set<GoogleLoadBalancerView> getApplicationLoadBalancers(String application) {
    def pattern = Keys.getLoadBalancerKey("*", "*", "${application}*")
    def identifiers = cacheView.filterIdentifiers(LOAD_BALANCERS.ns, pattern)

    cacheView.getAll(LOAD_BALANCERS.ns,
                     identifiers,
                     RelationshipCacheFilter.include(SERVER_GROUPS.ns)).collect { CacheData loadBalancerCacheData ->
      loadBalancersFromCacheData(loadBalancerCacheData)
    } as Set
  }

  GoogleLoadBalancerView loadBalancersFromCacheData(CacheData loadBalancerCacheData) {
    def loadBalancer = null
    switch (loadBalancerCacheData.attributes?.type) {
      case GoogleLoadBalancerType.HTTP.toString():
        loadBalancer = objectMapper.convertValue(loadBalancerCacheData.attributes, GoogleHttpLoadBalancer)
        break
      case GoogleLoadBalancerType.NETWORK.toString():
        loadBalancer = objectMapper.convertValue(loadBalancerCacheData.attributes, GoogleLoadBalancer)
        break
      default:
        loadBalancer = null
        break
    }

    def loadBalancerView = loadBalancer?.view

    def serverGroupKeys = loadBalancerCacheData.relationships[SERVER_GROUPS.ns]
    if (!serverGroupKeys) {
      return loadBalancerView
    }
    cacheView.getAll(SERVER_GROUPS.ns,
                     serverGroupKeys,
                     RelationshipCacheFilter.include(INSTANCES.ns))?.each { CacheData serverGroupCacheData ->
      if (!serverGroupCacheData) {
        return
      }

      GoogleServerGroup serverGroup = objectMapper.convertValue(serverGroupCacheData.attributes, GoogleServerGroup)

      // We have to calculate the L7 disabled state with respect to this server group since it's not
      // set on the way to the cache.
      def isDisabledFromHttp = false
      if (loadBalancer.type == GoogleLoadBalancerType.HTTP) {
        isDisabledFromHttp = Utils.determineHttpLoadBalancerDisabledState(loadBalancer, serverGroup)
      }

      def loadBalancerServerGroup = new LoadBalancerServerGroup(
          name: serverGroup.name,
          region: serverGroup.region,
          isDisabled: serverGroup.disabled || isDisabledFromHttp,
          detachedInstances: [],
          instances: [])

      def instanceNames = serverGroupCacheData.relationships[INSTANCES.ns]?.collect {
        Keys.parse(it)?.name
      }

      loadBalancer.healths.each { GoogleLoadBalancerHealth googleLoadBalancerHealth ->
        if (!instanceNames.remove(googleLoadBalancerHealth.instanceName)) {
          return
        }

        loadBalancerServerGroup.instances << new LoadBalancerInstance(
            id: googleLoadBalancerHealth.instanceName,
            zone: googleLoadBalancerHealth.instanceZone,
            health: [
                "state"      : googleLoadBalancerHealth.lbHealthSummaries[0].state as String,
                "description": googleLoadBalancerHealth.lbHealthSummaries[0].description
            ]
        )
      }

      loadBalancerServerGroup.detachedInstances = instanceNames // Any remaining instances are considered detached.
      loadBalancerView.serverGroups << loadBalancerServerGroup
    }

    loadBalancerView
  }
}
