/*
 * Copyright 2018 Netflix, Inc.
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

package com.netflix.spinnaker.clouddriver.elasticsearch.aws

import com.amazonaws.services.ec2.AmazonEC2
import com.amazonaws.services.ec2.model.DescribeInstancesRequest
import com.amazonaws.services.ec2.model.Instance
import com.google.common.collect.Lists
import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.cats.agent.RunnableAgent
import com.netflix.spinnaker.clouddriver.aws.provider.AwsProvider
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider
import com.netflix.spinnaker.clouddriver.aws.security.NetflixAmazonCredentials
import com.netflix.spinnaker.clouddriver.cache.CustomScheduledAgent
import com.netflix.spinnaker.clouddriver.elasticsearch.ElasticSearchClient
import com.netflix.spinnaker.clouddriver.elasticsearch.model.AccountModel
import com.netflix.spinnaker.clouddriver.elasticsearch.model.InstanceModel
import com.netflix.spinnaker.clouddriver.elasticsearch.model.InstanceTypeModel
import com.netflix.spinnaker.clouddriver.elasticsearch.model.LocationModel
import com.netflix.spinnaker.clouddriver.elasticsearch.model.ModelType
import com.netflix.spinnaker.clouddriver.elasticsearch.model.TagModel
import com.netflix.spinnaker.kork.core.RetrySupport
import org.slf4j.LoggerFactory
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class ElasticSearchAmazonInstanceCachingAgent(
  val retrySupport: RetrySupport,
  val registry: Registry,
  val amazonClientProvider: AmazonClientProvider,
  val accounts: Collection<NetflixAmazonCredentials>,
  val elasticSearchClient: ElasticSearchClient
) : RunnableAgent, CustomScheduledAgent {

  private val log = LoggerFactory.getLogger(ElasticSearchAmazonInstanceCachingAgent::class.java)

  override fun getPollIntervalMillis(): Long {
    return TimeUnit.MINUTES.toMillis(10)
  }

  override fun getTimeoutMillis(): Long {
    return TimeUnit.MINUTES.toMillis(20)
  }

  override fun getAgentType(): String {
    return ElasticSearchAmazonInstanceCachingAgent::class.java.simpleName
  }

  override fun getProviderName(): String {
    return AwsProvider.PROVIDER_NAME
  }

  override fun run() {
    val prefix = "aws_instances"
    var previousIndexes = elasticSearchClient.getPreviousIndexes(prefix)
    if (previousIndexes.size > 2) {
      log.warn("Found multiple previous indexes: {}", previousIndexes.joinToString(", "))

      // TODO-AJ revisit this safe guard ... at least emit a metric that can be alerted upon when this goes production
      for (previousIndex in previousIndexes) {
        elasticSearchClient.deleteIndex(previousIndex)
      }
      previousIndexes = emptySet()
    }

    val index = elasticSearchClient.createIndex(prefix)

    val instanceCount = AtomicInteger(0)

    for (credentials in accounts) {
      for (region in credentials.regions) {
        val instanceModels = fetchInstanceModels(credentials, region.name)

        instanceCount.addAndGet(instanceModels.size)

        for (partition in Lists.partition<InstanceModel>(instanceModels, 1000)) {
          retrySupport.retry(
            { elasticSearchClient.store(index, ModelType.Intance, partition) },
            10,
            2000,
            false
          )
        }
      }
    }

    log.info("Total # of instances: $instanceCount")

    elasticSearchClient.createAlias(index, prefix)
    for (previousIndex in previousIndexes) {
      elasticSearchClient.deleteIndex(previousIndex)
    }
  }

  private fun fetchInstanceModels(credentials: NetflixAmazonCredentials, region: String): List<InstanceModel> {
    val amazonEC2 = amazonClientProvider.getAmazonEC2(credentials, region)

    log.debug("Describing All Instances in ${credentials.name}:$region")

    return fetchAllInstances(amazonEC2).map { instance ->
      InstanceModel(
        "${credentials.accountId}:$region:${instance.instanceId}".toLowerCase(),
        instance.instanceId,
        InstanceTypeModel(instance.instanceType),
        LocationModel("availabilityZone", instance.placement.availabilityZone),
        AccountModel(credentials.accountId, credentials.name),
        listOfNotNull(instance.publicIpAddress, instance.privateIpAddress),
        instance.launchTime,
        instance.tags.map { TagModel(it.key, it.value) }
      )
    }
  }

  private fun fetchAllInstances(amazonEC2: AmazonEC2): List<Instance> {
    val instances = mutableListOf<Instance>()

    var request = DescribeInstancesRequest()
    while (true) {
      val response = amazonEC2.describeInstances(request)

      instances.addAll(response.reservations.flatMap { it.instances })
      if (response.nextToken != null) {
        request = request.withNextToken(response.nextToken)
      } else {
        break
      }
    }

    return instances
  }
}
