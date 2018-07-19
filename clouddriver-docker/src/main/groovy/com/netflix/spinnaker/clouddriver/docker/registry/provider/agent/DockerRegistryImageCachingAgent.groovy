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

import java.util.concurrent.TimeUnit

import static java.util.Collections.unmodifiableSet

@Slf4j
class DockerRegistryImageCachingAgent implements CachingAgent, AccountAware, AgentIntervalAware {
  static final Set<AgentDataType> types = unmodifiableSet([
    AgentDataType.Authority.INFORMATIVE.forType(Keys.Namespace.TAGGED_IMAGE.ns),
    AgentDataType.Authority.INFORMATIVE.forType(Keys.Namespace.IMAGE_ID.ns)
  ] as Set)

  private DockerRegistryCredentials credentials
  private DockerRegistryCloudProvider dockerRegistryCloudProvider
  private String accountName
  private final int index
  private final int threadCount
  private final long interval
  private String registry

  DockerRegistryImageCachingAgent(DockerRegistryCloudProvider dockerRegistryCloudProvider,
                                  String accountName,
                                  DockerRegistryCredentials credentials,
                                  int index,
                                  int threadCount,
                                  Long intervalSecs,
                                  String registry) {
    this.dockerRegistryCloudProvider = dockerRegistryCloudProvider
    this.accountName = accountName
    this.credentials = credentials
    this.index = index
    this.threadCount = threadCount
    this.interval = TimeUnit.SECONDS.toMillis(intervalSecs)
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
    }.collectEntries { repository ->
      if(credentials.skip?.contains(repository)) {
          return [:]
      }
      DockerRegistryTags tags = null
      try {
        tags = credentials.client.getTags(repository)
      } catch (Exception e) {
        if (e instanceof RetrofitError && e.response?.status == 404) {
          log.warn("Could not load tags for ${repository} in ${credentials.client.address}, reason: ${e.message}")
        } else {
          log.error("Could not load tags for ${repository} in ${credentials.client.address}", e)
        }

        return [:]
      }

      def name = tags?.name
      def imageTags = tags?.tags
      if (name && imageTags) {
        if (name != repository) {
          // TODO(lwander) remove this warning if this doesn't cause problems
          log.warn("Docker registry $accountName responded with an image name that does not match the repository name. Defaulting to repository='$repository' over name='$name'")
          name = repository
        }
        [(name): imageTags]
      } else {
        return [:]
      }
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
        if (!tag) {
          log.warn("Empty tag encountered for $accountName/$repository, not caching")
          return
        }
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
              // It is safe to not cache the tag here because igor now persists all the tags it has seen.
              log.warn("Error retrieving manifest for $tagKey; digest and tag will not be cached: $e.message")
              return
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
          attributes.account = accountName
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

  @Override
  Long getAgentInterval() {
    return interval
  }
}
