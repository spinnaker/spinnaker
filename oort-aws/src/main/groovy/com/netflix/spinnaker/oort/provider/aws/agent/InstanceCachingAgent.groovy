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

package com.netflix.spinnaker.oort.provider.aws.agent

import com.amazonaws.services.ec2.model.DescribeInstancesRequest
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.amazoncomponents.security.AmazonClientProvider
import com.netflix.spinnaker.amos.aws.NetflixAmazonCredentials
import com.netflix.spinnaker.cats.agent.AgentDataType
import com.netflix.spinnaker.cats.agent.CacheResult
import com.netflix.spinnaker.cats.agent.CachingAgent
import com.netflix.spinnaker.cats.agent.DefaultCacheResult
import com.netflix.spinnaker.cats.cache.CacheData
import com.netflix.spinnaker.oort.model.Instance
import com.netflix.spinnaker.oort.model.ServerGroup
import com.netflix.spinnaker.oort.provider.aws.AwsProvider
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import static com.netflix.spinnaker.cats.agent.AgentDataType.Authority.AUTHORITATIVE
import static com.netflix.spinnaker.cats.agent.AgentDataType.Authority.INFORMATIVE

class InstanceCachingAgent implements CachingAgent {

  private static final TypeReference<Map<String, Object>> ATTRIBUTES = new TypeReference<Map<String, Object>>() {}

  private static final Logger log = LoggerFactory.getLogger(ClusterCachingAgent)

  final Set<AgentDataType> types = Collections.unmodifiableSet([
    AUTHORITATIVE.forType(Instance.DATA_TYPE),
    INFORMATIVE.forType(ServerGroup.DATA_TYPE),
    INFORMATIVE.forType(AwsProvider.IMAGE_TYPE)
  ] as Set)

  final AmazonClientProvider amazonClientProvider
  final NetflixAmazonCredentials account
  final String region
  final ObjectMapper objectMapper
  final AwsProvider.Identifiers identifiers

  InstanceCachingAgent(AmazonClientProvider amazonClientProvider, NetflixAmazonCredentials account, String region, ObjectMapper objectMapper) {
    this.amazonClientProvider = amazonClientProvider
    this.account = account
    this.region = region
    this.objectMapper = objectMapper
    identifiers = new AwsProvider.Identifiers(account.name, region)
  }

  @Override
  String getProviderName() {
    AwsProvider.PROVIDER_NAME
  }

  @Override
  String getAgentType() {
    "${account.name}/${region}/${InstanceCachingAgent.simpleName}"
  }

  @Override
  Collection<AgentDataType> getProvidedDataTypes() {
    types
  }

  static class MutableCacheData implements CacheData {
    final String id
    final Map<String, Object> attributes = [:]
    final Map<String, Collection<String>> relationships = [:].withDefault { [] as Set }
    public MutableCacheData(String id) {
      this.id = id
    }
  }

  @Override
  CacheResult loadData() {

    def amazonEC2 = amazonClientProvider.getAmazonEC2(account, region)
    def request = new DescribeInstancesRequest()
    List<com.amazonaws.services.ec2.model.Instance> awsInstances = []
    while (true) {
      def resp = amazonEC2.describeInstances(request)
      awsInstances.addAll(resp.reservations.collectMany { it.instances })
      if (resp.nextToken) {
        request.withNextToken(request.nextToken)
      } else {
        break
      }
    }


    Closure<Map<String, CacheData>> cache = {
      [:].withDefault { String id -> new MutableCacheData(id) }
    }

    Map<String, CacheData> serverGroups = cache()
    Map<String, CacheData> instances = cache()
    Map<String, CacheData> images = cache()

    for (com.amazonaws.services.ec2.model.Instance instance : awsInstances) {
      def data = new InstanceData(instance, identifiers)

      cacheImage(data, images)
      cacheServerGroup(data, serverGroups)
      cacheInstance(data, instances)
    }

    new DefaultCacheResult(
      (ServerGroup.DATA_TYPE): serverGroups.values(),
      (Instance.DATA_TYPE): instances.values(),
      (AwsProvider.IMAGE_TYPE): images.values())
  }

  private void cacheImage(InstanceData data, Map<String, CacheData> images) {
    images[data.imageId].with {
      relationships[Instance.DATA_TYPE].add(data.instanceId)
    }
  }

  private void cacheServerGroup(InstanceData data, Map<String, CacheData> serverGroups) {
    if (data.serverGroup) {
      serverGroups[data.serverGroup].with {
        relationships[Instance.DATA_TYPE].add(data.instanceId)
      }
    }
  }

  private void cacheInstance(InstanceData data, Map<String, CacheData> instances) {
    instances[data.instanceId].with {
      attributes.putAll(objectMapper.convertValue(data.instance, ATTRIBUTES))
      relationships[AwsProvider.IMAGE_TYPE].add(data.imageId)
      if (data.serverGroup) {
        relationships[ServerGroup.DATA_TYPE].add(data.serverGroup)
      } else {
        relationships[ServerGroup.DATA_TYPE].clear()
      }
    }

  }

  private static class InstanceData {
    static final String ASG_TAG_NAME = "aws:autoscaling:groupName"

    final com.amazonaws.services.ec2.model.Instance instance
    final String instanceId
    final String serverGroup
    final String imageId

    public InstanceData(com.amazonaws.services.ec2.model.Instance instance, AwsProvider.Identifiers identifiers) {
      this.instance = instance
      this.instanceId = identifiers.instanceId(instance.instanceId)
      String sgTag = instance.tags?.find { it.key == ASG_TAG_NAME }?.value
      this.serverGroup = sgTag ? identifiers.serverGroupId(sgTag) : null
      this.imageId = identifiers.imageId(instance.imageId)
    }

  }
}
