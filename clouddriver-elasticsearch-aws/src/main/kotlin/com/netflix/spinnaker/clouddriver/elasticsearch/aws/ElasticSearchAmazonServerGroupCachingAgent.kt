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

import com.amazonaws.services.autoscaling.AmazonAutoScaling
import com.amazonaws.services.autoscaling.model.AutoScalingGroup
import com.amazonaws.services.autoscaling.model.DescribeAutoScalingGroupsRequest
import com.amazonaws.services.autoscaling.model.DescribeLaunchConfigurationsRequest
import com.amazonaws.services.autoscaling.model.LaunchConfiguration
import com.google.common.collect.Lists
import com.netflix.frigga.Names
import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.cats.agent.RunnableAgent
import com.netflix.spinnaker.clouddriver.aws.provider.AwsProvider
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider
import com.netflix.spinnaker.clouddriver.aws.security.NetflixAmazonCredentials
import com.netflix.spinnaker.clouddriver.cache.CustomScheduledAgent
import com.netflix.spinnaker.clouddriver.elasticsearch.ElasticSearchClient
import com.netflix.spinnaker.clouddriver.elasticsearch.model.AccountModel
import com.netflix.spinnaker.clouddriver.elasticsearch.model.BlockDeviceModel
import com.netflix.spinnaker.clouddriver.elasticsearch.model.InstanceTypeModel
import com.netflix.spinnaker.clouddriver.elasticsearch.model.LocationModel
import com.netflix.spinnaker.clouddriver.elasticsearch.model.ModelType
import com.netflix.spinnaker.clouddriver.elasticsearch.model.Moniker
import com.netflix.spinnaker.clouddriver.elasticsearch.model.ServerGroupModel
import com.netflix.spinnaker.kork.core.RetrySupport
import org.slf4j.LoggerFactory
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class ElasticSearchAmazonServerGroupCachingAgent(
  val retrySupport: RetrySupport,
  val registry: Registry,
  val amazonClientProvider: AmazonClientProvider,
  val accounts: Collection<NetflixAmazonCredentials>,
  val elasticSearchClient: ElasticSearchClient
) : RunnableAgent, CustomScheduledAgent {

  private val log = LoggerFactory.getLogger(ElasticSearchAmazonServerGroupCachingAgent::class.java)

  override fun getPollIntervalMillis(): Long {
    return TimeUnit.SECONDS.toMillis(180)
  }

  override fun getTimeoutMillis(): Long {
    return TimeUnit.MINUTES.toMillis(5)
  }

  override fun getAgentType(): String {
    return ElasticSearchAmazonServerGroupCachingAgent::class.java.simpleName
  }

  override fun getProviderName(): String {
    return AwsProvider.PROVIDER_NAME
  }

  override fun run() {
    val prefix = "aws_server_groups"
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
    val serverGroupCount = AtomicInteger(0)

    for (credentials in accounts) {
      for (region in credentials.regions) {
        val serverGroupModels = fetchServerGroupModels(credentials, region.name)

        serverGroupCount.addAndGet(serverGroupModels.size)

        for (partition in Lists.partition<ServerGroupModel>(serverGroupModels, 1000)) {
          retrySupport.retry(
            { elasticSearchClient.store(index, ModelType.ServerGroup, partition) },
            10,
            2000,
            false
          )
        }
      }
    }

    log.info("Total # of server groups: $serverGroupCount")

    elasticSearchClient.createAlias(index, prefix)
    for (previousIndex in previousIndexes) {
      elasticSearchClient.deleteIndex(previousIndex)
    }
  }

  private fun fetchServerGroupModels(credentials: NetflixAmazonCredentials, region: String): List<ServerGroupModel> {
    val amazonAutoScaling = amazonClientProvider.getAutoScaling(credentials, region)

    log.debug("Describing All Autoscaling Groups in ${credentials.name}:$region")
    val autoScalingGroups = fetchAllAutoScalingGroups(amazonAutoScaling)

    log.debug("Describing All Launch Configurations in ${credentials.name}:$region")
    val launchConfigurationsByName = fetchAllLaunchConfigurations(amazonAutoScaling)
      .map { it.launchConfigurationName.toLowerCase() to it }
      .toMap()

    return autoScalingGroups.map { asg ->
      val launchConfiguration = launchConfigurationsByName.getOrDefault(
        asg.launchConfigurationName?.toLowerCase(),
        LaunchConfiguration()
      )

      val blockDeviceType = when {
        launchConfiguration.blockDeviceMappings.isEmpty() -> "none"
        launchConfiguration.blockDeviceMappings[0].ebs != null -> "ebs"
        else -> "ephemeral"
      }

      val instanceTypes = listOfNotNull(launchConfiguration.instanceType).map { InstanceTypeModel(it) }

      ServerGroupModel(
        "${credentials.accountId}:$region:${asg.autoScalingGroupName}".toLowerCase(),
        asg.autoScalingGroupName,
        Names.parseName(asg.autoScalingGroupName).toMoniker(),
        LocationModel("region", region),
        AccountModel(credentials.accountId, credentials.name),
        instanceTypes,
        BlockDeviceModel(blockDeviceType)
      )
    }
  }

  private fun fetchAllAutoScalingGroups(amazonAutoScaling: AmazonAutoScaling): List<AutoScalingGroup> {
    val autoScalingGroups = mutableListOf<AutoScalingGroup>()

    var request = DescribeAutoScalingGroupsRequest()
    while (true) {
      val response = amazonAutoScaling.describeAutoScalingGroups(request)

      autoScalingGroups.addAll(response.autoScalingGroups)
      if (response.nextToken != null) {
        request = request.withNextToken(response.nextToken)
      } else {
        break
      }
    }

    return autoScalingGroups
  }

  private fun fetchAllLaunchConfigurations(amazonAutoScaling: AmazonAutoScaling): List<LaunchConfiguration> {
    val launchConfigurations = mutableListOf<LaunchConfiguration>()

    var request = DescribeLaunchConfigurationsRequest()
    while (true) {
      val response = amazonAutoScaling.describeLaunchConfigurations(request)

      launchConfigurations.addAll(response.launchConfigurations)
      if (response.nextToken != null) {
        request = request.withNextToken(response.nextToken)
      } else {
        break
      }
    }

    return launchConfigurations
  }

  fun Names.toMoniker() = Moniker(this.app, this.stack, this.detail, this.cluster)
}
