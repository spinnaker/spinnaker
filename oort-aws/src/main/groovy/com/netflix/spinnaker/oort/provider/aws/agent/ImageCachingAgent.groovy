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

import com.amazonaws.services.ec2.model.DescribeImagesRequest
import com.amazonaws.services.ec2.model.Image
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.amazoncomponents.security.AmazonClientProvider
import com.netflix.spinnaker.amos.aws.NetflixAmazonCredentials
import com.netflix.spinnaker.cats.agent.AgentDataType
import com.netflix.spinnaker.cats.agent.CacheResult
import com.netflix.spinnaker.cats.agent.CachingAgent
import com.netflix.spinnaker.cats.agent.DefaultCacheResult
import com.netflix.spinnaker.cats.cache.CacheData
import com.netflix.spinnaker.cats.cache.DefaultCacheData
import com.netflix.spinnaker.oort.provider.aws.AwsProvider
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import static com.netflix.spinnaker.cats.agent.AgentDataType.Authority.AUTHORITATIVE


class ImageCachingAgent implements CachingAgent {
  private static final TypeReference<Map<String, Object>> ATTRIBUTES = new TypeReference<Map<String, Object>>() {}

  private static final Logger log = LoggerFactory.getLogger(ClusterCachingAgent)

  final Set<AgentDataType> types = Collections.unmodifiableSet([
    AUTHORITATIVE.forType(AwsProvider.IMAGE_TYPE)
  ] as Set)

  final AmazonClientProvider amazonClientProvider
  final NetflixAmazonCredentials account
  final String region
  final ObjectMapper objectMapper
  final AwsProvider.Identifiers identifiers

  ImageCachingAgent(AmazonClientProvider amazonClientProvider, NetflixAmazonCredentials account, String region, ObjectMapper objectMapper) {
    this.amazonClientProvider = amazonClientProvider
    this.account = account
    this.region = region
    this.objectMapper = objectMapper
    identifiers = new AwsProvider.Identifiers(account.name, region)
  }

  @Override
  String getProviderName() {
    return AwsProvider.PROVIDER_NAME
  }

  @Override
  String getAgentType() {
    return "${account.name}/${region}/${ImageCachingAgent.simpleName}"
  }

  @Override
  Collection<AgentDataType> getProvidedDataTypes() {
    return types
  }

  @Override
  CacheResult loadData() {
    def amazonEC2 = amazonClientProvider.getAmazonEC2(account, region)

    List<Image> images = amazonEC2.describeImages().images

    Collection<CacheData> imageCacheData = images.collect { Image image ->
      Map<String, Object> attributes = objectMapper.convertValue(image, ATTRIBUTES)
      new DefaultCacheData(identifiers.imageId(image.imageId), attributes, [:])
    }

    new DefaultCacheResult((AwsProvider.IMAGE_TYPE): imageCacheData)
  }

}
