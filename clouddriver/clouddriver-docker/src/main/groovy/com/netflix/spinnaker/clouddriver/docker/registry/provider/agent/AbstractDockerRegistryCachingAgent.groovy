/*
 * Copyright 2025 Harness, Inc
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

import com.netflix.spinnaker.cats.agent.AccountAware
import com.netflix.spinnaker.cats.agent.AgentDataType
import com.netflix.spinnaker.cats.agent.AgentIntervalAware
import com.netflix.spinnaker.cats.agent.CacheResult
import com.netflix.spinnaker.cats.agent.CachingAgent
import com.netflix.spinnaker.cats.agent.DefaultCacheResult
import com.netflix.spinnaker.cats.provider.ProviderCache
import com.netflix.spinnaker.clouddriver.docker.registry.DockerRegistryCloudProvider
import com.netflix.spinnaker.clouddriver.docker.registry.cache.DefaultCacheDataBuilder
import com.netflix.spinnaker.clouddriver.docker.registry.cache.Keys
import com.netflix.spinnaker.clouddriver.docker.registry.provider.DockerRegistryProvider
import com.netflix.spinnaker.clouddriver.docker.registry.provider.DockerRegistryProviderUtils
import com.netflix.spinnaker.clouddriver.docker.registry.security.DockerRegistryCredentials
import com.netflix.spinnaker.kork.docker.model.DockerRegistryTags
import com.netflix.spinnaker.kork.retrofit.exceptions.SpinnakerHttpException
import groovy.util.logging.Slf4j

import java.util.concurrent.ConcurrentMap
import java.util.concurrent.TimeUnit

@Slf4j
abstract class AbstractDockerRegistryCachingAgent implements CachingAgent, AccountAware, AgentIntervalAware {
  protected DockerRegistryCredentials credentials
  protected DockerRegistryCloudProvider dockerRegistryCloudProvider
  protected String accountName
  protected final int index
  protected final int threadCount
  protected final long interval
  protected String registry

  AbstractDockerRegistryCachingAgent(DockerRegistryCloudProvider dockerRegistryCloudProvider,
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
  abstract Collection<AgentDataType> getProvidedDataTypes()

  @Override
  CacheResult loadData(ProviderCache providerCache) {
    Map<String, Set<String>> tags = loadRepositoryTags()
    buildCacheResult(tags)
  }

  @Override
  String getAgentType() {
    "${accountName}/${this.class.simpleName}[${index + 1}/$threadCount]"
  }

  @Override
  String getProviderName() {
    DockerRegistryProvider.PROVIDER_NAME
  }

  /**
   * Get the list of repositories to process
   * @return List of repositories
   */
  protected abstract List<String> getRepositories()

  /**
   * Load tags for repositories
   * @return Map of repository to set of tags
   */
  protected Map<String, Set<String>> loadRepositoryTags() {
    getRepositories().findAll { it ->
      threadCount == 1 || (it.hashCode() % threadCount).abs() == index
    }.collectEntries { repository ->
      if (credentials.skip?.contains(repository)) {
        return [:]
      }
      DockerRegistryTags tags = null
      try {
        tags = credentials.client.getTags(repository)
      } catch (Exception e) {
        if (e instanceof SpinnakerHttpException && ((SpinnakerHttpException)e).getResponseCode() == 404) {
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
          log.warn("Docker registry $accountName responded with an image name that does not match the repository name. Defaulting to repository='$repository' over name='$name'")
          name = repository
        }
        [(name): imageTags]
      } else {
        return [:]
      }
    }
  }

  /**
   * Get the key for a tagged image
   * @param repository Repository name
   * @param tag Tag name
   * @return Key for the tagged image
   */
  protected abstract String getTaggedImageKey(String account, String repository, String tag)

  /**
   * Get the namespace for tagged images
   * @return Namespace for tagged images
   */
  protected abstract Keys.Namespace getTaggedImageNamespace()

  @Override
  String getAccountName() {
    return accountName
  }

  protected CacheResult buildCacheResult(Map<String, Set<String>> tagMap) {
    log.info("Describing items in ${agentType}")

    ConcurrentMap<String, DefaultCacheDataBuilder> cachedTags = DefaultCacheDataBuilder.defaultCacheDataBuilderMap()
    ConcurrentMap<String, DefaultCacheDataBuilder> cachedIds = DefaultCacheDataBuilder.defaultCacheDataBuilderMap()

    tagMap.forEach { repository, tags ->
      tags.parallelStream().forEach { tag ->
        if (!tag) {
          log.warn("Empty tag encountered for $accountName/$repository, not caching")
          return
        }
        def tagKey = getTaggedImageKey(accountName, repository, tag)
        def imageIdKey = Keys.getImageIdKey(DockerRegistryProviderUtils.imageId(registry, repository, tag))
        def digest = null
        def digestContent = null
        def creationDate = null

        if (credentials.trackDigests) {
          try {
            digest = credentials.client.getDigest(repository, tag)
          } catch (Exception e) {
            if(e instanceof SpinnakerHttpException && ((SpinnakerHttpException)e).getResponseCode() == 404)
            {
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

        if (credentials.inspectDigests) {
          try {
            digest = credentials.client.getConfigDigest(repository, tag)
            digestContent = credentials.client.getDigestContent(repository, digest)
          } catch (Exception e) {
            log.warn("Error retrieving config digest for $tagKey; digest and tag will not be cached: $e.message")
          }
        }

        if (credentials.sortTagsByDate) {
          try {
            creationDate = credentials.client.getCreationDate(repository, tag)
          } catch (Exception e) {
            log.warn("Unable to fetch tag creation date, reason: {} (tag: {}, repository: {})", e.message, tag, repository)
          }
        }

        def tagData = new DefaultCacheDataBuilder()
        tagData.setId(tagKey)
        tagData.attributes.put("name", "${repository}:${tag}".toString())
        tagData.attributes.put("account", accountName)
        tagData.attributes.put("digest", digest)
        tagData.attributes.put("date", creationDate)
        if (digestContent?.config != null) {
          tagData.attributes.put("labels", digestContent.config.Labels)
        }
        cachedTags.put(tagKey, tagData)

        def idData = new DefaultCacheDataBuilder()
        idData.setId(imageIdKey)
        idData.attributes.put("tagKey", tagKey)
        idData.attributes.put("account", accountName)
        cachedIds.put(imageIdKey, idData)
      }

      null
    }

    log.info("Caching ${cachedTags.size()} tagged images in ${agentType}")
    log.info("Caching ${cachedIds.size()} image ids in ${agentType}")

    new DefaultCacheResult([
      (getTaggedImageNamespace().ns): cachedTags.values().collect({ builder -> builder.build() }),
      (Keys.Namespace.IMAGE_ID.ns): cachedIds.values().collect({ builder -> builder.build() }),
    ])
  }

  @Override
  Long getAgentInterval() {
    return interval
  }
}
