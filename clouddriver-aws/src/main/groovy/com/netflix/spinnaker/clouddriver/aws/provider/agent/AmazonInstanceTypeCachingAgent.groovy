/*
 * Copyright 2015 Netflix, Inc.
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

import com.amazonaws.services.ec2.model.DescribeReservedInstancesOfferingsRequest
import com.amazonaws.services.ec2.model.ReservedInstancesOffering
import com.netflix.spinnaker.cats.agent.AccountAware
import com.netflix.spinnaker.cats.agent.AgentDataType
import com.netflix.spinnaker.cats.agent.CacheResult
import com.netflix.spinnaker.cats.agent.CachingAgent
import com.netflix.spinnaker.cats.agent.DefaultCacheResult
import com.netflix.spinnaker.cats.cache.CacheData
import com.netflix.spinnaker.cats.cache.DefaultCacheData
import com.netflix.spinnaker.cats.provider.ProviderCache
import com.netflix.spinnaker.clouddriver.aws.AmazonCloudProvider
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider
import com.netflix.spinnaker.clouddriver.aws.security.NetflixAmazonCredentials
import com.netflix.spinnaker.clouddriver.cache.CustomScheduledAgent
import com.netflix.spinnaker.clouddriver.aws.cache.Keys
import com.netflix.spinnaker.clouddriver.aws.provider.AwsInfrastructureProvider

import java.util.concurrent.TimeUnit

import static com.netflix.spinnaker.cats.agent.AgentDataType.Authority.AUTHORITATIVE
import static com.netflix.spinnaker.clouddriver.aws.cache.Keys.Namespace.INSTANCE_TYPES

import groovy.util.logging.Slf4j

@Slf4j
class AmazonInstanceTypeCachingAgent implements CachingAgent, CustomScheduledAgent, AccountAware {

  public static final long DEFAULT_POLL_INTERVAL_MILLIS = TimeUnit.HOURS.toMillis(2)
  public static final long DEFAULT_TIMEOUT_MILLIS = TimeUnit.MINUTES.toMillis(15)

  final AmazonCloudProvider amazonCloudProvider
  final AmazonClientProvider amazonClientProvider
  final NetflixAmazonCredentials account
  final String region

  final long pollIntervalMillis
  final long timeoutMillis

  static final Set<AgentDataType> types = Collections.unmodifiableSet([
    AUTHORITATIVE.forType(INSTANCE_TYPES.ns)
  ] as Set)

  AmazonInstanceTypeCachingAgent(AmazonCloudProvider amazonCloudProvider,
                                 AmazonClientProvider amazonClientProvider,
                                 NetflixAmazonCredentials account,
                                 String region) {
    this(amazonCloudProvider, amazonClientProvider, account, region, DEFAULT_POLL_INTERVAL_MILLIS, DEFAULT_TIMEOUT_MILLIS)
  }

  AmazonInstanceTypeCachingAgent(AmazonCloudProvider amazonCloudProvider,
                                 AmazonClientProvider amazonClientProvider,
                                 NetflixAmazonCredentials account,
                                 String region,
                                 long pollIntervalMillis,
                                 long timeoutMillis) {
    this.amazonCloudProvider = amazonCloudProvider
    this.amazonClientProvider = amazonClientProvider
    this.account = account
    this.region = region
    this.pollIntervalMillis = pollIntervalMillis
    this.timeoutMillis = timeoutMillis
  }

  @Override
  String getProviderName() {
    AwsInfrastructureProvider.PROVIDER_NAME
  }

  @Override
  String getAgentType() {
    "${account.name}/${region}/${AmazonInstanceTypeCachingAgent.simpleName}"
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
    def ec2 = amazonClientProvider.getAmazonEC2(account, region)
    List<CacheData> data = []
    def request = new DescribeReservedInstancesOfferingsRequest()
    while (true) {
      def offerings = ec2.describeReservedInstancesOfferings(request)
      Set<String> allIdentifiers = []
      data.addAll(offerings.reservedInstancesOfferings.findResults { ReservedInstancesOffering offering ->
        String key = Keys.getInstanceTypeKey(amazonCloudProvider, offering.instanceType, region, account.name)
        if (allIdentifiers.add(key)) {
          new DefaultCacheData(Keys.getInstanceTypeKey(amazonCloudProvider, offering.instanceType, region, account.name), [
            account           : account.name,
            region            : region,
            name              : offering.instanceType,
            availabilityZone  : null,
            productDescription: null,
            durationSeconds   : null
          ],
            [:])
        } else {
          null
        }
      })

      if (offerings.nextToken) {
        request.setNextToken(offerings.nextToken)
      } else {
        break
      }
    }
    log.info("Caching ${data.size()} items in ${agentType}")
    new DefaultCacheResult([(INSTANCE_TYPES.ns): data])
  }
}
