/*
 * Copyright 2016 Target Inc.
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

package com.netflix.spinnaker.clouddriver.openstack.provider.view

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.cats.cache.Cache
import com.netflix.spinnaker.cats.cache.CacheData
import com.netflix.spinnaker.cats.cache.RelationshipCacheFilter
import com.netflix.spinnaker.clouddriver.model.InstanceProvider
import com.netflix.spinnaker.clouddriver.openstack.OpenstackCloudProvider
import com.netflix.spinnaker.clouddriver.openstack.cache.Keys
import com.netflix.spinnaker.clouddriver.openstack.model.OpenstackInstance
import com.netflix.spinnaker.clouddriver.openstack.model.OpenstackLoadBalancer
import com.netflix.spinnaker.clouddriver.openstack.model.OpenstackLoadBalancerHealth
import com.netflix.spinnaker.clouddriver.openstack.security.OpenstackNamedAccountCredentials
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

import static com.netflix.spinnaker.clouddriver.openstack.cache.Keys.Namespace.INSTANCES
import static com.netflix.spinnaker.clouddriver.openstack.cache.Keys.Namespace.LOAD_BALANCERS

@Component
class OpenstackInstanceProvider implements InstanceProvider<OpenstackInstance.View, String> {
  final String cloudProvider = OpenstackCloudProvider.ID
  final Cache cacheView
  final AccountCredentialsProvider accountCredentialsProvider
  final ObjectMapper objectMapper

  @Autowired
  OpenstackInstanceProvider(Cache cacheView, AccountCredentialsProvider accountCredentialsProvider, ObjectMapper objectMapper) {
    this.cacheView = cacheView
    this.accountCredentialsProvider = accountCredentialsProvider
    this.objectMapper = objectMapper
  }

  Set<OpenstackInstance.View> getInstances(Collection<String> cacheKeys) {
    cacheKeys.findResults(this.&getInstanceInternal).collect { it.view }.toSet()
  }

  @Override
  OpenstackInstance.View getInstance(String account, String region, String id) {
    getInstanceInternal(Keys.getInstanceKey(id, account, region))?.view
  }

  /**
   * Shared logic between getInstance and getInstances
   * @param cacheKey
   * @return
   */
  protected OpenstackInstance getInstanceInternal(String cacheKey) {
    OpenstackInstance result = null

    CacheData instanceEntry = cacheView.get(INSTANCES.ns, cacheKey, RelationshipCacheFilter.include(LOAD_BALANCERS.ns))
    if (instanceEntry) {
      result = objectMapper.convertValue(instanceEntry.attributes, OpenstackInstance)

      def loadBalancerKeys = instanceEntry.relationships[LOAD_BALANCERS.ns]
      if (loadBalancerKeys) {
        cacheView.getAll(LOAD_BALANCERS.ns, loadBalancerKeys).each { CacheData loadBalancerCacheData ->
          OpenstackLoadBalancer loadBalancer = objectMapper.convertValue(loadBalancerCacheData.attributes, OpenstackLoadBalancer)
          def foundHealths = loadBalancer.healths.findAll { OpenstackLoadBalancerHealth health ->
            health.instanceId == result.instanceId
          }
          if (foundHealths) {
            result.loadBalancerHealths?.addAll(foundHealths)
          }
        }
      }
    }
    result
  }

  @Override
  String getConsoleOutput(String account, String region, String id) {
    String result
    OpenstackNamedAccountCredentials namedAccountCredentials = (OpenstackNamedAccountCredentials) this.accountCredentialsProvider.getCredentials(account)
    if (!namedAccountCredentials) {
      throw new IllegalArgumentException("Invalid credentials: ${account}:${region}")
    } else {
      result = namedAccountCredentials.credentials.provider.getConsoleOutput(region, id)
    }
    result
  }
}
