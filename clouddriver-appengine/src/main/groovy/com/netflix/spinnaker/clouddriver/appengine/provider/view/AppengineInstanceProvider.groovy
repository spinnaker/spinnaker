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
import com.netflix.spinnaker.cats.cache.RelationshipCacheFilter
import com.netflix.spinnaker.clouddriver.appengine.AppengineCloudProvider
import com.netflix.spinnaker.clouddriver.appengine.cache.Keys
import com.netflix.spinnaker.clouddriver.appengine.model.AppengineInstance
import com.netflix.spinnaker.clouddriver.model.InstanceProvider
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

import static com.netflix.spinnaker.clouddriver.appengine.cache.Keys.Namespace.INSTANCES
import static com.netflix.spinnaker.clouddriver.appengine.cache.Keys.Namespace.LOAD_BALANCERS
import static com.netflix.spinnaker.clouddriver.appengine.cache.Keys.Namespace.SERVER_GROUPS

@Component
class AppengineInstanceProvider implements InstanceProvider<AppengineInstance, String> {
  @Autowired
  Cache cacheView

  @Autowired
  ObjectMapper objectMapper

  final String cloudProvider = AppengineCloudProvider.ID

  @Override
  AppengineInstance getInstance(String account, String region, String instanceName) {
    def instanceKey = Keys.getInstanceKey(account, instanceName)
    def instanceData = cacheView.get(
      INSTANCES.ns,
      instanceKey,
      RelationshipCacheFilter.include(LOAD_BALANCERS.ns, SERVER_GROUPS.ns))

    instanceData ? getInstanceFromCacheData(instanceData) : null
  }

  @Override
  String getConsoleOutput(String account, String region, String id) {
    null
  }

  AppengineInstance getInstanceFromCacheData(CacheData cacheData) {
    AppengineInstance instance = objectMapper.convertValue(cacheData.attributes.instance, AppengineInstance)

    def serverGroupKey = cacheData.relationships[SERVER_GROUPS.ns]?.first()
    if (serverGroupKey) {
      instance.serverGroup = Keys.parse(serverGroupKey).serverGroup
    }

    def loadBalancerKey = cacheData.relationships[LOAD_BALANCERS.ns]?.first()
    if (loadBalancerKey) {
      instance.loadBalancers = [Keys.parse(loadBalancerKey).loadBalancer]
    }

    instance
  }
}

