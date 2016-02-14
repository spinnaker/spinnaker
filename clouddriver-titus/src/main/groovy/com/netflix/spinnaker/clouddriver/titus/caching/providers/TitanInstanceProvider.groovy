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
import com.netflix.spinnaker.clouddriver.model.InstanceProvider
import com.netflix.spinnaker.clouddriver.titus.TitanCloudProvider
import com.netflix.spinnaker.clouddriver.titus.caching.Keys
import com.netflix.spinnaker.clouddriver.titus.model.TitanInstance
import com.netflix.titanclient.model.Task
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

import static com.netflix.spinnaker.clouddriver.titus.caching.Keys.Namespace.HEALTH
import static com.netflix.spinnaker.clouddriver.titus.caching.Keys.Namespace.INSTANCES

@Component
class TitanInstanceProvider implements InstanceProvider<TitanInstance> {

  private final Cache cacheView
  private final ObjectMapper objectMapper
  private final TitanCloudProvider titanCloudProvider

  @Autowired
  TitanInstanceProvider(Cache cacheView, TitanCloudProvider titanCloudProvider, ObjectMapper objectMapper) {
    this.cacheView = cacheView
    this.titanCloudProvider = titanCloudProvider
    this.objectMapper = objectMapper
  }

  @Override
  TitanInstance getInstance(String account, String region, String id) {
    CacheData instanceEntry = cacheView.get(INSTANCES.ns, Keys.getInstanceKey(id, account, region))
    if (!instanceEntry) {
      return null
    }
    String json = objectMapper.writeValueAsString(instanceEntry.attributes.task)
    Task task = objectMapper.readValue(json, Task)
    TitanInstance instance = new TitanInstance(task)
    instance.health = instanceEntry.attributes[HEALTH.ns]
    if (instanceEntry.relationships[HEALTH.ns]) {
      instance.health = instance.health ?: []
      instance.health.addAll(cacheView.getAll(HEALTH.ns, instanceEntry.relationships[HEALTH.ns])*.attributes)
    }
    instance
  }

  @Override
  String getPlatform() {
    titanCloudProvider.id
  }

  @Override
  String getConsoleOutput(String account, String region, String id) {
    return null // TODO - TBD
  }
}
