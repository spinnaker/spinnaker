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


package com.netflix.spinnaker.clouddriver.aws.provider.agent

import com.amazonaws.services.ec2.model.DescribeReservedInstancesRequest
import com.amazonaws.services.ec2.model.ReservedInstances
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
import com.netflix.spinnaker.clouddriver.aws.data.Keys
import com.netflix.spinnaker.clouddriver.aws.provider.AwsProvider
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider
import com.netflix.spinnaker.clouddriver.aws.security.NetflixAmazonCredentials
import com.netflix.spinnaker.clouddriver.cache.CustomScheduledAgent
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import java.util.concurrent.TimeUnit

import static com.netflix.spinnaker.cats.agent.AgentDataType.Authority.AUTHORITATIVE
import static com.netflix.spinnaker.clouddriver.core.provider.agent.Namespace.RESERVED_INSTANCES

class ReservedInstancesCachingAgent implements CachingAgent, CustomScheduledAgent, AccountAware {
  private static final TypeReference<Map<String, Object>> ATTRIBUTES = new TypeReference<Map<String, Object>>() {}
  private static final long DEFAULT_POLL_INTERVAL_MILLIS = TimeUnit.MINUTES.toMillis(10)
  private static final long DEFAULT_TIMEOUT_MILLIS = TimeUnit.MINUTES.toMillis(5)

  final Logger log = LoggerFactory.getLogger(getClass())

  final Set<AgentDataType> types = Collections.unmodifiableSet([
    AUTHORITATIVE.forType(RESERVED_INSTANCES.ns)
  ] as Set)

  final AmazonClientProvider amazonClientProvider
  final NetflixAmazonCredentials account
  final String region
  final ObjectMapper objectMapper
  final Registry registry

  ReservedInstancesCachingAgent(AmazonClientProvider amazonClientProvider,
                                NetflixAmazonCredentials account,
                                String region,
                                ObjectMapper objectMapper,
                                Registry registry) {
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
    return "${account.name}/${region}/${ReservedInstancesCachingAgent.simpleName}"
  }

  @Override
  String getAccountName() {
    return account.name
  }

  @Override
  Collection<AgentDataType> getProvidedDataTypes() {
    return types
  }

  @Override
  CacheResult loadData(ProviderCache providerCache) {
    log.info("Describing items in ${agentType}")

    def amazonEC2 = amazonClientProvider.getAmazonEC2(account, region)

    def result = amazonEC2.describeReservedInstances(new DescribeReservedInstancesRequest())
    List<ReservedInstances> allReservedInstances = result.reservedInstances

    Collection<CacheData> reservedInstancesData = allReservedInstances
      .findAll { it.state.equalsIgnoreCase("active") }
      .collect { ReservedInstances reservedInstances ->
      Map<String, Object> attributes = objectMapper.convertValue(reservedInstances, ATTRIBUTES);
      new DefaultCacheData(Keys.getReservedInstancesKey(reservedInstances.reservedInstancesId, account.name, region), attributes, [:])
    }

    log.info("Caching ${reservedInstancesData.size()} items in ${agentType}")
    new DefaultCacheResult((RESERVED_INSTANCES.ns): reservedInstancesData)
  }

  @Override
  long getPollIntervalMillis() {
    return DEFAULT_POLL_INTERVAL_MILLIS
  }

  @Override
  long getTimeoutMillis() {
    return DEFAULT_TIMEOUT_MILLIS
  }
}
