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

package com.netflix.spinnaker.clouddriver.aws.provider.agent

import com.amazonaws.services.autoscaling.model.DescribeLaunchConfigurationsRequest
import com.amazonaws.services.autoscaling.model.LaunchConfiguration
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.cats.agent.AccountAware
import com.netflix.spinnaker.cats.agent.AgentDataType
import com.netflix.spinnaker.cats.agent.CacheResult
import com.netflix.spinnaker.cats.agent.CachingAgent
import com.netflix.spinnaker.cats.agent.DefaultCacheResult
import com.netflix.spinnaker.cats.cache.CacheData
import com.netflix.spinnaker.cats.cache.DefaultCacheData
import com.netflix.spinnaker.cats.provider.ProviderCache
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider
import com.netflix.spinnaker.clouddriver.aws.security.NetflixAmazonCredentials
import com.netflix.spinnaker.clouddriver.aws.data.Keys
import com.netflix.spinnaker.clouddriver.aws.provider.AwsProvider
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import static com.netflix.spinnaker.cats.agent.AgentDataType.Authority.AUTHORITATIVE
import static com.netflix.spinnaker.clouddriver.core.provider.agent.Namespace.IMAGES
import static com.netflix.spinnaker.clouddriver.core.provider.agent.Namespace.LAUNCH_CONFIGS

class LaunchConfigCachingAgent implements CachingAgent, AccountAware, DriftMetric {
  final Logger log = LoggerFactory.getLogger(getClass())
  private static final TypeReference<Map<String, Object>> ATTRIBUTES = new TypeReference<Map<String, Object>>() {}

  final Set<AgentDataType> types = Collections.unmodifiableSet([
    AUTHORITATIVE.forType(LAUNCH_CONFIGS.ns)
  ] as Set)

  final AmazonClientProvider amazonClientProvider
  final NetflixAmazonCredentials account
  final String region
  final ObjectMapper objectMapper
  final Registry registry

  LaunchConfigCachingAgent(AmazonClientProvider amazonClientProvider, NetflixAmazonCredentials account, String region, ObjectMapper objectMapper, Registry registry) {
    this.amazonClientProvider = amazonClientProvider
    this.account = account
    this.region = region
    this.objectMapper = objectMapper.enable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
    this.registry = registry
  }

  @Override
  String getProviderName() {
    return AwsProvider.PROVIDER_NAME
  }

  @Override
  String getAgentType() {
    return "${account.name}/${region}/${LaunchConfigCachingAgent.simpleName}"
  }

  @Override
  String getAccountName() {
    account.name
  }

  @Override
  Collection<AgentDataType> getProvidedDataTypes() {
    return types
  }

  @Override
  CacheResult loadData(ProviderCache providerCache) {
    log.info("Describing items in ${agentType}")
    def autoScaling = amazonClientProvider.getAutoScaling(account, region)

    Long start = null
    List<LaunchConfiguration> launchConfigs = []
    def request = new DescribeLaunchConfigurationsRequest()
    while (true) {
      def resp = autoScaling.describeLaunchConfigurations(request)
      if (account.eddaEnabled) {
        start = amazonClientProvider.lastModified ?: 0
      }
      launchConfigs.addAll(resp.launchConfigurations)
      if (resp.nextToken) {
        request.withNextToken(resp.nextToken)
      } else {
        break
      }
    }

    Collection<CacheData> launchConfigData = launchConfigs.collect { LaunchConfiguration lc ->
      String key = Keys.getLaunchConfigKey(lc.launchConfigurationName, account.name, region)
      String application = Keys.parse(key).get("application")
      Map<String, Object> attributes = objectMapper.convertValue(lc, ATTRIBUTES);

      if (application != null) {
        attributes.put("application", application)
      }

      Map<String, Collection<String>> relationships = [(IMAGES.ns):[Keys.getImageKey(lc.imageId, account.name, region)]]
      new DefaultCacheData(key, attributes, relationships)
    }

    recordDrift(start)
    log.info("Caching ${launchConfigData.size()} items in ${agentType}")
    new DefaultCacheResult((LAUNCH_CONFIGS.ns): launchConfigData)
  }
}
