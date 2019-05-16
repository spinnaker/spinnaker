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

import com.amazonaws.services.ec2.model.DescribeImagesRequest
import com.amazonaws.services.ec2.model.Filter
import com.amazonaws.services.ec2.model.Image
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
import com.netflix.spinnaker.clouddriver.cache.CustomScheduledAgent
import com.netflix.spinnaker.kork.dynamicconfig.DynamicConfigService
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import static com.netflix.spinnaker.cats.agent.AgentDataType.Authority.AUTHORITATIVE
import static com.netflix.spinnaker.clouddriver.core.provider.agent.Namespace.IMAGES
import static com.netflix.spinnaker.clouddriver.core.provider.agent.Namespace.NAMED_IMAGES

import java.util.concurrent.TimeUnit

class ImageCachingAgent implements CachingAgent, AccountAware, DriftMetric, CustomScheduledAgent {
  final Logger log = LoggerFactory.getLogger(getClass())
  private static final TypeReference<Map<String, Object>> ATTRIBUTES = new TypeReference<Map<String, Object>>() {}

  final Set<AgentDataType> types = Collections.unmodifiableSet([
    AUTHORITATIVE.forType(IMAGES.ns),
    AUTHORITATIVE.forType(NAMED_IMAGES.ns)
  ] as Set)

  final AmazonClientProvider amazonClientProvider
  final NetflixAmazonCredentials account
  final String region
  final ObjectMapper objectMapper
  final Registry registry
  final boolean includePublicImages
  final long pollIntervalMillis
  final DynamicConfigService dynamicConfigService

  ImageCachingAgent(AmazonClientProvider amazonClientProvider, NetflixAmazonCredentials account, String region, ObjectMapper objectMapper, Registry registry, boolean includePublicImages, DynamicConfigService dynamicConfigService) {
    this.amazonClientProvider = amazonClientProvider
    this.account = account
    this.region = region
    this.objectMapper = objectMapper.enable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
    this.registry = registry
    this.includePublicImages = includePublicImages
    this.dynamicConfigService = dynamicConfigService
    if (includePublicImages) {
      this.pollIntervalMillis = TimeUnit.MINUTES.toMillis(60)
    } else {
      this.pollIntervalMillis = -1
    }
  }

  @Override
  long getPollIntervalMillis() {
    return pollIntervalMillis
  }

  @Override
  long getTimeoutMillis() {
    return -1
  }

  @Override
  String getProviderName() {
    return AwsProvider.PROVIDER_NAME
  }

  @Override
  String getAgentType() {
    def scope = "public"
    if (!includePublicImages) {
      scope = "private"
    }
    return "${account.name}/${region}/${ImageCachingAgent.simpleName}/${scope}"
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
    if (includePublicImages && !dynamicConfigService.isEnabled("aws.defaults.public-images", true)) {
      log.info("short-circuiting with empty result set for public images in ${agentType}")
      return new DefaultCacheResult((IMAGES.ns): [], (NAMED_IMAGES.ns): [])
    }
    log.info("Describing items in ${agentType}")
    //we read public images directly from AWS instead of having edda cache them:
    def amazonEC2 = amazonClientProvider.getAmazonEC2(account, region, includePublicImages)
    def request = new DescribeImagesRequest()
    if (includePublicImages) {
      request.withFilters(new Filter('is-public', ['true']))
    } else {
      request.withFilters(new Filter('is-public', ['false']))
    }

    List<Image> images = amazonEC2.describeImages(request).images
    Long start = null
    if (account.eddaEnabled) {
      start = amazonClientProvider.lastModified ?: 0
      // Edda does not respect filter parameters. Filter here manually instead.
      if (includePublicImages) {
        images = images.findAll { it.isPublic() }
      } else {
        images = images.findAll { !it.isPublic() }
      }
    }

    Collection<CacheData> imageCacheData = new ArrayList<>(images.size())
    Collection<CacheData> namedImageCacheData = new ArrayList<>(images.size())

    for (Image image : images) {
      Map<String, Object> attributes = objectMapper.convertValue(image, ATTRIBUTES)
      def imageId = Keys.getImageKey(image.imageId, account.name, region)
      def namedImageId = Keys.getNamedImageKey(account.name, image.name)
      imageCacheData.add(new DefaultCacheData(imageId, attributes, [(NAMED_IMAGES.ns): [namedImageId]]))
      namedImageCacheData.add(new DefaultCacheData(namedImageId, [
        name              : image.name,
        virtualizationType: image.virtualizationType,
        creationDate      : image.creationDate
      ], [(IMAGES.ns): [imageId]]))
    }

    recordDrift(start)
    log.info("Caching ${imageCacheData.size()} items in ${agentType}")
    new DefaultCacheResult((IMAGES.ns): imageCacheData, (NAMED_IMAGES.ns): namedImageCacheData)
  }

}
