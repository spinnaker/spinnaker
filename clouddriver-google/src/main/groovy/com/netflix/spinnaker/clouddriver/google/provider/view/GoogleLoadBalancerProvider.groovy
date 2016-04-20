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
import com.netflix.spinnaker.clouddriver.google.model.GoogleLoadBalancer
import com.netflix.spinnaker.clouddriver.google.model.GoogleServerGroup
import com.netflix.spinnaker.clouddriver.google.model.health.GoogleLoadBalancerHealth
import com.netflix.spinnaker.clouddriver.model.LoadBalancerInstance
import com.netflix.spinnaker.clouddriver.model.LoadBalancerProvider
import com.netflix.spinnaker.clouddriver.model.LoadBalancerServerGroup
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component

import static com.netflix.spinnaker.clouddriver.google.cache.Keys.Namespace.*

@Component
class GoogleLoadBalancerProvider implements LoadBalancerProvider<GoogleLoadBalancer.View> {

  @Autowired
  Cache cacheView
  @Autowired
  ObjectMapper objectMapper
  @Autowired
  GoogleSecurityGroupProvider googleSecurityGroupProvider
  @Autowired
  GoogleInstanceProvider googleInstanceProvider

  @Override
  Set<GoogleLoadBalancer.View> getApplicationLoadBalancers(String application) {
    def pattern = Keys.getLoadBalancerKey("*", "*", "${application}*")
    def identifiers = cacheView.filterIdentifiers(LOAD_BALANCERS.ns, pattern)

    cacheView.getAll(LOAD_BALANCERS.ns,
                     identifiers,
                     RelationshipCacheFilter.include(SERVER_GROUPS.ns)).collect { CacheData loadBalancerCacheData ->
      loadBalancersFromCacheData(loadBalancerCacheData)
    } as Set
  }

  GoogleLoadBalancer.View loadBalancersFromCacheData(CacheData loadBalancerCacheData) {
    GoogleLoadBalancer loadBalancer = objectMapper.convertValue(loadBalancerCacheData.attributes, GoogleLoadBalancer)
    GoogleLoadBalancer.View loadBalancerView = loadBalancer?.view

    def serverGroupKeys = loadBalancerCacheData.relationships[SERVER_GROUPS.ns]
    if (!serverGroupKeys) {
      return loadBalancerView
    }
    cacheView.getAll(SERVER_GROUPS.ns,
                     serverGroupKeys,
                     RelationshipCacheFilter.include(INSTANCES.ns))?.each { CacheData serverGroupCacheData ->
      GoogleServerGroup serverGroup = objectMapper.convertValue(serverGroupCacheData.attributes, GoogleServerGroup)

      def loadBalancerServerGroup = new LoadBalancerServerGroup(
          name: serverGroup.name,
          isDisabled: serverGroup.disabled,
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
