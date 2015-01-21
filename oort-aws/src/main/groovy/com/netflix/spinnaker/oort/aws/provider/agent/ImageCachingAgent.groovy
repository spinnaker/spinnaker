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

package com.netflix.spinnaker.oort.aws.provider.agent

import com.amazonaws.services.ec2.model.Image
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.netflix.amazoncomponents.security.AmazonClientProvider
import com.netflix.spinnaker.amos.aws.NetflixAmazonCredentials
import com.netflix.spinnaker.cats.agent.AgentDataType
import com.netflix.spinnaker.cats.agent.CacheResult
import com.netflix.spinnaker.cats.agent.CachingAgent
import com.netflix.spinnaker.cats.agent.DefaultCacheResult
import com.netflix.spinnaker.cats.cache.CacheData
import com.netflix.spinnaker.cats.cache.DefaultCacheData
import com.netflix.spinnaker.cats.provider.ProviderCache
import com.netflix.spinnaker.oort.aws.data.Keys
import com.netflix.spinnaker.oort.aws.provider.AwsProvider
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import static com.netflix.spinnaker.cats.agent.AgentDataType.Authority.AUTHORITATIVE
import static com.netflix.spinnaker.cats.agent.AgentDataType.Authority.INFORMATIVE
import static com.netflix.spinnaker.oort.aws.data.Keys.Namespace.IMAGES
import static com.netflix.spinnaker.oort.aws.data.Keys.Namespace.NAMED_IMAGES


class ImageCachingAgent implements CachingAgent {
  private static final TypeReference<Map<String, Object>> ATTRIBUTES = new TypeReference<Map<String, Object>>() {}

  private static final Logger log = LoggerFactory.getLogger(ClusterCachingAgent)

  final Set<AgentDataType> types = Collections.unmodifiableSet([
    AUTHORITATIVE.forType(IMAGES.ns),
    INFORMATIVE.forType(NAMED_IMAGES.ns)
  ] as Set)

  final AmazonClientProvider amazonClientProvider
  final NetflixAmazonCredentials account
  final String region
  final ObjectMapper objectMapper

  ImageCachingAgent(AmazonClientProvider amazonClientProvider, NetflixAmazonCredentials account, String region, ObjectMapper objectMapper) {
    this.amazonClientProvider = amazonClientProvider
    this.account = account
    this.region = region
    this.objectMapper = objectMapper.enable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
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
  CacheResult loadData(ProviderCache providerCache) {
    def amazonEC2 = amazonClientProvider.getAmazonEC2(account, region)

    List<Image> images = amazonEC2.describeImages().images
    long start = EddaSupport.parseLastModified(amazonClientProvider.lastResponseHeaders?.get("last-modified")?.get(0))

    Collection<CacheData> imageCacheData = new ArrayList<>(images.size())
    Collection<CacheData> namedImageCacheData = new ArrayList<>(images.size())

    for (Image image : images) {
      Map<String, Object> attributes = objectMapper.convertValue(image, ATTRIBUTES)
      def imageId = Keys.getImageKey(image.imageId, account.name, region)
      def namedImageId = Keys.getNamedImageKey(account.name, image.name)
      imageCacheData.add(new DefaultCacheData(imageId, attributes, [(NAMED_IMAGES.ns):[namedImageId]]))
      namedImageCacheData.add(new DefaultCacheData(namedImageId, [name: image.name], [(IMAGES.ns):[imageId]]))
    }

    long drift = new Date().time - start
    log.info("${agentType}/drift - $drift milliseconds")
    new DefaultCacheResult((IMAGES.ns): imageCacheData, (NAMED_IMAGES.ns): namedImageCacheData)
  }

}
