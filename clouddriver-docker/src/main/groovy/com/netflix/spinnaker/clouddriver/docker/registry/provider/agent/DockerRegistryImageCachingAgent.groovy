/*
 * Copyright 2016 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.docker.registry.provider.agent

import com.netflix.spinnaker.cats.agent.*
import com.netflix.spinnaker.cats.provider.ProviderCache
import com.netflix.spinnaker.clouddriver.docker.registry.DockerRegistryCloudProvider
import com.netflix.spinnaker.clouddriver.docker.registry.api.v2.client.DockerRegistryTags
import com.netflix.spinnaker.clouddriver.docker.registry.cache.DefaultCacheDataBuilder
import com.netflix.spinnaker.clouddriver.docker.registry.cache.Keys
import com.netflix.spinnaker.clouddriver.docker.registry.provider.DockerRegistryProvider
import com.netflix.spinnaker.clouddriver.docker.registry.provider.DockerRegistryProviderUtils
import com.netflix.spinnaker.clouddriver.docker.registry.security.DockerRegistryCredentials
import groovy.util.logging.Slf4j
import retrofit.RetrofitError

import static java.util.Collections.unmodifiableSet

@Slf4j
class DockerRegistryImageCachingAgent implements CachingAgent, AccountAware {
  static final Set<AgentDataType> types = unmodifiableSet([
    AgentDataType.Authority.AUTHORITATIVE.forType(Keys.Namespace.TAGGED_IMAGE.ns),
    AgentDataType.Authority.AUTHORITATIVE.forType(Keys.Namespace.IMAGE_ID.ns)
  ] as Set)

  private DockerRegistryCredentials credentials
  private DockerRegistryCloudProvider dockerRegistryCloudProvider
  private String accountName
  private final int index
  private final int threadCount
  private String registry

  DockerRegistryImageCachingAgent(DockerRegistryCloudProvider dockerRegistryCloudProvider,
                                  String accountName,
                                  DockerRegistryCredentials credentials,
                                  int index,
                                  int threadCount,
                                  String registry) {
    this.dockerRegistryCloudProvider = dockerRegistryCloudProvider
    this.accountName = accountName
    this.credentials = credentials
    this.index = index
    this.threadCount = threadCount
    this.registry = registry
  }

  @Override
  Collection<AgentDataType> getProvidedDataTypes() {
    return types
  }

  @Override
  CacheResult loadData(ProviderCache providerCache) {
    Map<String, Set<String>> tags = loadTags()

    buildCacheResult(tags)
  }

  @Override
  String getAgentType() {
    "${accountName}/${DockerRegistryImageCachingAgent.simpleName}[${index + 1}/$threadCount]"
  }

  @Override
  String getProviderName() {
    DockerRegistryProvider.PROVIDER_NAME
  }

  private Map<String, Set<String>> loadTags() {
    credentials.repositories.findAll { it ->
      threadCount == 1 || (it.hashCode() % threadCount).abs() == index
    }.collectEntries {
      if(credentials.skip?.contains(it)) {
          return [:]
      }
      DockerRegistryTags tags
      try {
        tags = credentials.client.getTags(it)
      } catch (e) {
        log.error("Could not load tags for ${it}")
      }
      tags ? [(tags.name): tags.tags ?: []] : [:]
    }
  }

  @Override
  String getAccountName() {
    return accountName
  }

  private CacheResult buildCacheResult(Map<String, Set<String>> tagMap) {
    log.info("Describing items in ${agentType}")

    Map<String, DefaultCacheDataBuilder> cachedTags = DefaultCacheDataBuilder.defaultCacheDataBuilderMap()
    Map<String, DefaultCacheDataBuilder> cachedIds = DefaultCacheDataBuilder.defaultCacheDataBuilderMap()

    tagMap.forEach { repository, tags ->
      tags.forEach { tag ->
        def tagKey = Keys.getTaggedImageKey(accountName, repository, tag)
        def imageIdKey = Keys.getImageIdKey(DockerRegistryProviderUtils.imageId(registry, repository, tag))
        def digest = null

        if (credentials.trackDigests) {
          try {
            digest = credentials.client.getDigest(repository, tag)
          } catch (Exception e) {
            if (e instanceof RetrofitError && ((RetrofitError) e).response?.status == 404) {
              // Indicates inconsistency in registry, or deletion between call for all tags and manifest retrieval.
              // In either case, we need to trust that this tag no longer exists.
              log.warn("Image manifest for $tagKey no longer available; tag will not be cached: $e.message")
              return
            } else {
              // Could be intermittent error, not caching the tag here will cause spurious tag creation/deletion events
              // when the error is resolved.
              log.warn("Error retrieving manifest for $tagKey; digest will not be cached: $e.message")
            }
          }
        }

        cachedTags[tagKey].with {
          attributes.name = "${repository}:${tag}".toString()
          attributes.account = accountName
          attributes.digest = digest
        }

        cachedIds[imageIdKey].with {
          attributes.tagKey = tagKey
        }
      }

      null
    }

    log.info("Caching ${cachedTags.size()} tagged images in ${agentType}")
    log.info("Caching ${cachedIds.size()} image ids in ${agentType}")

    new DefaultCacheResult([
      (Keys.Namespace.TAGGED_IMAGE.ns): cachedTags.values().collect({ builder -> builder.build() }),
      (Keys.Namespace.IMAGE_ID.ns): cachedIds.values().collect({ builder -> builder.build() }),
    ])
  }
}
