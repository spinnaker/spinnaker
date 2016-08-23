/*
 * Copyright 2016 Netflix, Inc.
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
import com.netflix.spinnaker.clouddriver.core.provider.agent.ExternalHealthProvider
import com.netflix.spinnaker.clouddriver.model.InstanceProvider
import com.netflix.spinnaker.clouddriver.titus.TitusCloudProvider
import com.netflix.spinnaker.clouddriver.titus.caching.Keys
import com.netflix.spinnaker.clouddriver.titus.caching.utils.AwsLookupUtil
import com.netflix.spinnaker.clouddriver.titus.client.model.Job
import com.netflix.spinnaker.clouddriver.titus.model.TitusInstance
import com.netflix.spinnaker.clouddriver.titus.model.TitusSecurityGroup
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

import static com.netflix.spinnaker.clouddriver.core.provider.agent.Namespace.HEALTH
import static com.netflix.spinnaker.clouddriver.core.provider.agent.Namespace.INSTANCES

@Component
class TitusInstanceProvider implements InstanceProvider<TitusInstance> {

  private final Cache cacheView
  private final ObjectMapper objectMapper
  private final TitusCloudProvider titusCloudProvider

  @Autowired(required = false)
  List<ExternalHealthProvider> externalHealthProviders

  @Autowired
  AwsLookupUtil awsLookupUtil

  @Autowired
  TitusInstanceProvider(Cache cacheView, TitusCloudProvider titusCloudProvider, ObjectMapper objectMapper) {
    this.cacheView = cacheView
    this.titusCloudProvider = titusCloudProvider
    this.objectMapper = objectMapper
  }

  @Override
  TitusInstance getInstance(String account, String region, String id) {
    CacheData instanceEntry = cacheView.get(INSTANCES.ns, Keys.getInstanceKey(id))
    if (!instanceEntry) {
      return null
    }
    Job.TaskSummary task = objectMapper.convertValue(instanceEntry.attributes.task, Job.TaskSummary)
    Job job = objectMapper.convertValue(instanceEntry.attributes.job, Job)
    TitusInstance instance = new TitusInstance(job, task)
    instance.health = instance.health ?: []
    if (instanceEntry.attributes[HEALTH.ns]) {
      instance.health.addAll(instanceEntry.attributes[HEALTH.ns])
    }
    instance.securityGroups = awsLookupUtil.lookupSecurityGroupNames(account, region, job.securityGroups as LinkedHashSet)
    externalHealthProviders.each { externalHealthProvider ->
      def healthKeys = []
      externalHealthProvider.agents.each { externalHealthAgent ->
        healthKeys << Keys.getInstanceHealthKey(instance.name, externalHealthAgent.healthId)
      }
      healthKeys.unique().each { key ->
        def externalHealth = cacheView.getAll(HEALTH.ns, key)
        if (externalHealth) {
          def health = externalHealth*.attributes
          health.each { it.remove('lastUpdatedTimestamp') }
          instance.health.addAll(health)
        }
      }
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
