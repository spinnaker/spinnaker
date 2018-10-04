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
import com.netflix.frigga.Names
import com.netflix.spinnaker.cats.cache.Cache
import com.netflix.spinnaker.cats.cache.CacheData
import com.netflix.spinnaker.clouddriver.core.provider.agent.ExternalHealthProvider
import com.netflix.spinnaker.clouddriver.model.InstanceProvider
import com.netflix.spinnaker.clouddriver.titus.TitusCloudProvider
import com.netflix.spinnaker.clouddriver.titus.caching.Keys
import com.netflix.spinnaker.clouddriver.titus.caching.utils.AwsLookupUtil
import com.netflix.spinnaker.clouddriver.titus.caching.utils.CachingSchema
import com.netflix.spinnaker.clouddriver.titus.caching.utils.CachingSchemaUtil
import com.netflix.spinnaker.clouddriver.titus.client.model.Job
import com.netflix.spinnaker.clouddriver.titus.client.model.Task
import com.netflix.spinnaker.clouddriver.titus.model.TitusInstance
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

import static com.netflix.spinnaker.clouddriver.core.provider.agent.Namespace.HEALTH
import static com.netflix.spinnaker.clouddriver.core.provider.agent.Namespace.INSTANCES
import static com.netflix.spinnaker.clouddriver.titus.caching.Keys.Namespace.SERVER_GROUPS

@Component
class TitusInstanceProvider implements InstanceProvider<TitusInstance, String> {
  final String cloudProvider = TitusCloudProvider.ID
  private final Cache cacheView
  private final ObjectMapper objectMapper
  private final TitusCloudProvider titusCloudProvider
  private final CachingSchemaUtil cachingSchemaUtil
  private final AwsLookupUtil awsLookupUtil

  private final Logger log = LoggerFactory.getLogger(getClass())

  @Autowired(required = false)
  List<ExternalHealthProvider> externalHealthProviders

  @Autowired
  TitusInstanceProvider(
    Cache cacheView,
    TitusCloudProvider titusCloudProvider,
    ObjectMapper objectMapper,
    CachingSchemaUtil cachingSchemaUtil,
    AwsLookupUtil awsLookupUtil
  ) {
    this.cacheView = cacheView
    this.titusCloudProvider = titusCloudProvider
    this.objectMapper = objectMapper
    this.cachingSchemaUtil = cachingSchemaUtil
    this.awsLookupUtil = awsLookupUtil
  }

  @Override
  TitusInstance getInstance(String account, String region, String id) {

    String awsAccount = awsLookupUtil.awsAccountId(account, region)

    if (!awsAccount) {
      return null
    }

    if (account == null || account == "") {
      return null
    }

    String stack = awsLookupUtil.stack(account)
    if (!stack) {
      stack = 'mainvpc'
    }

    CachingSchema cachingSchema = cachingSchemaUtil.getCachingSchemaForAccount(account)

    String instanceKey = ( cachingSchema == CachingSchema.V1
      ? Keys.getInstanceKey(id, awsAccount, stack, region)
      : Keys.getInstanceV2Key(id, account, region))

    CacheData instanceEntry = cacheView.get(INSTANCES.ns, instanceKey)
    if (!instanceEntry) {
      return null
    }
    Task task = objectMapper.convertValue(instanceEntry.attributes.task, Task)
    Job job
    if (instanceEntry.attributes.job == null) {
      // Instance is cached separately from job, so we must also load the job cache entry
      if (instanceEntry.relationships[SERVER_GROUPS.ns] && !instanceEntry.relationships[SERVER_GROUPS.ns].empty) {
        job = loadJob(instanceEntry)
      } else {
        log.error( "Task {} in {}:{} does not have a job", task.id, account, region)
      }
    } else {
      // Instance is cached at the same time as job, V1 schema
      job = objectMapper.convertValue(instanceEntry.attributes.job, Job)
    }

    TitusInstance instance = new TitusInstance(job, task)
    instance.accountId = awsAccount

    instance.health = instance.health ?: []
    if (instanceEntry.attributes[HEALTH.ns]) {
      instance.health.addAll(instanceEntry.attributes[HEALTH.ns])
    }
    if (instanceEntry.relationships[SERVER_GROUPS.ns] && !instanceEntry.relationships[SERVER_GROUPS.ns].empty) {
      instance.serverGroup = (cachingSchema == CachingSchema.V1
        ? instanceEntry.relationships[SERVER_GROUPS.ns].iterator().next()
        : Keys.parse(instanceEntry.relationships[SERVER_GROUPS.ns].iterator().next()).serverGroup)
      instance.cluster =  Names.parseName(instance.serverGroup)?.cluster
    }
    externalHealthProviders.each { externalHealthProvider ->
      def healthKeys = []
      externalHealthProvider.agents.each { externalHealthAgent ->
        healthKeys << Keys.getInstanceHealthKey(instance.instanceId, externalHealthAgent.healthId)
      }
      healthKeys.unique().each { key ->
        def externalHealth = cacheView.getAll(HEALTH.ns, key)
        if (externalHealth) {
          def health = externalHealth*.attributes
          health.each {
            it.remove('lastUpdatedTimestamp')
          }
          instance.health.addAll(health)
        }
      }
    }
    awsLookupUtil.lookupTargetGroupHealth(job, [instance].toSet())
    return instance
  }

  private Job loadJob(CacheData instanceEntry) {
    Collection<CacheData> data = resolveRelationshipData(instanceEntry, SERVER_GROUPS.ns)
    return objectMapper.convertValue(data?.first()?.attributes.job, Job)
  }

  private Collection<CacheData> resolveRelationshipData(CacheData source, String relationship) {
    resolveRelationshipData(source, relationship) { true }
  }

  private Collection<CacheData> resolveRelationshipData(CacheData source, String relationship, Closure<Boolean> relFilter) {
    Collection<String> filteredRelationships = source.relationships[relationship]?.findAll(relFilter)
    filteredRelationships ? cacheView.getAll(relationship, filteredRelationships) : []
  }

  @Override
  String getConsoleOutput(String account, String region, String id) {
    TitusInstance instance = this.getInstance(account, region, id)
    if (instance == null) {
      return null
    }
    String url = "http://" + instance.getHostIp() + ":8004/logs/" + instance.getInstanceId() + "?f=stdout"
    String output = new URL(url).getText()
    return output
  }
}
