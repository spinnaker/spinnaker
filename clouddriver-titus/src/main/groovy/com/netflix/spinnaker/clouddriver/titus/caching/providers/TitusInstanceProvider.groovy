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
import com.netflix.spinnaker.clouddriver.titus.TitusCloudProvider
import com.netflix.spinnaker.clouddriver.titus.caching.Keys
import com.netflix.spinnaker.clouddriver.titus.client.model.Job
import com.netflix.spinnaker.clouddriver.titus.model.TitusInstance
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

import static com.netflix.spinnaker.clouddriver.titus.caching.Keys.Namespace.HEALTH
import static com.netflix.spinnaker.clouddriver.titus.caching.Keys.Namespace.INSTANCES

@Component
class TitusInstanceProvider implements InstanceProvider<TitusInstance> {

  private final Cache cacheView
  private final ObjectMapper objectMapper
  private final TitusCloudProvider titusCloudProvider

  @Autowired
  TitusInstanceProvider(Cache cacheView, TitusCloudProvider titusCloudProvider, ObjectMapper objectMapper) {
    this.cacheView = cacheView
    this.titusCloudProvider = titusCloudProvider
    this.objectMapper = objectMapper
  }

  @Override
  TitusInstance getInstance(String account, String region, String id) {
    CacheData instanceEntry = cacheView.get(INSTANCES.ns, Keys.getInstanceKey(id, account, region))
    if (!instanceEntry) {
      return null
    }
    Job.TaskSummary task = objectMapper.convertValue(instanceEntry.attributes.task, Job.TaskSummary)
    Job job = objectMapper.convertValue(instanceEntry.attributes.job, Job)
    TitusInstance instance = new TitusInstance(job, task)
    if (instanceEntry.relationships[HEALTH.ns]) {
      instance.health = instance.health ?: []
      instance.health.addAll(cacheView.getAll(HEALTH.ns, instanceEntry.relationships[HEALTH.ns])*.attributes)
    }
    instance
  }

  @Override
  String getPlatform() {
    titusCloudProvider.id
  }

  @Override
  String getConsoleOutput(String account, String region, String id) {
    return null // TODO - TBD
  }
}
