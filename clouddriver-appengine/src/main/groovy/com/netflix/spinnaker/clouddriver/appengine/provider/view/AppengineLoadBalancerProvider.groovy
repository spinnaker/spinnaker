/*
 * Copyright 2016 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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

package com.netflix.spinnaker.clouddriver.appengine.provider.view

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.cats.cache.Cache
import com.netflix.spinnaker.cats.cache.CacheData
import com.netflix.spinnaker.clouddriver.appengine.AppengineCloudProvider
import com.netflix.spinnaker.clouddriver.appengine.AppengineConfiguration
import com.netflix.spinnaker.clouddriver.appengine.cache.Keys
import com.netflix.spinnaker.clouddriver.appengine.cache.Keys.Namespace
import com.netflix.spinnaker.clouddriver.appengine.model.AppengineInstance
import com.netflix.spinnaker.clouddriver.appengine.model.AppengineLoadBalancer
import com.netflix.spinnaker.clouddriver.appengine.model.AppengineServerGroup
import com.netflix.spinnaker.clouddriver.model.LoadBalancerProvider
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Component
class AppengineLoadBalancerProvider implements LoadBalancerProvider<AppengineLoadBalancer> {

  final String cloudProvider = AppengineCloudProvider.ID

  @Override
  List<LoadBalancerProvider.Item> list() {
    // TODO(danielpeach): Implement.
    throw new UnsupportedOperationException("Appengine Not Yet Ready.")
  }

  @Override
  LoadBalancerProvider.Item get(String name) {
    throw new UnsupportedOperationException("Appengine Not Yet Ready.")
  }

  @Override
  List<LoadBalancerProvider.Details> byAccountAndRegionAndName(String account, String region, String name) {
    throw new UnsupportedOperationException("Appengine Not Yet Ready.")
  }

  @Autowired
  Cache cacheView

  @Autowired
  ObjectMapper objectMapper

  @Override
  Set<AppengineLoadBalancer> getApplicationLoadBalancers(String applicationName) {
    String applicationKey = Keys.getApplicationKey(applicationName)
    CacheData application = cacheView.get(Namespace.APPLICATIONS.ns, applicationKey)

    def applicationLoadBalancers = AppengineProviderUtils.resolveRelationshipData(cacheView,
                                                                                  application,
                                                                                  Namespace.LOAD_BALANCERS.ns)
    translateLoadBalancers(applicationLoadBalancers)
  }

  Set<AppengineLoadBalancer> translateLoadBalancers(Collection<CacheData> cacheData) {
    cacheData.collect { loadBalancerData ->

      Set<AppengineServerGroup> serverGroups = AppengineProviderUtils
        .resolveRelationshipData(cacheView, loadBalancerData, Namespace.SERVER_GROUPS.ns)
        .collect {
          Set<AppengineInstance> instances = AppengineProviderUtils
            .resolveRelationshipData(cacheView, it, Namespace.INSTANCES.ns)
            .findResults { AppengineProviderUtils.instanceFromCacheData(objectMapper, it) }
          AppengineProviderUtils.serverGroupFromCacheData(objectMapper, it, instances)
        }

      AppengineProviderUtils.loadBalancerFromCacheData(objectMapper, loadBalancerData, serverGroups)
    }
  }

  AppengineLoadBalancer getLoadBalancer(String account, String loadBalancerName) {
    String loadBalancerKey = Keys.getLoadBalancerKey(account, loadBalancerName)
    CacheData loadBalancerData = cacheView.get(Namespace.LOAD_BALANCERS.ns, loadBalancerKey)
    Set<AppengineLoadBalancer> loadBalancerSet = translateLoadBalancers([loadBalancerData] - null)

    loadBalancerSet ? loadBalancerSet.first() : null
  }
}
