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

import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.cats.agent.*
import com.netflix.spinnaker.cats.provider.ProviderCache
import com.netflix.spinnaker.clouddriver.cache.OnDemandAgent
import com.netflix.spinnaker.clouddriver.cache.OnDemandMetricsSupport
import com.netflix.spinnaker.clouddriver.docker.registry.DockerRegistryCloudProvider
import com.netflix.spinnaker.clouddriver.docker.registry.api.v2.client.DockerRegistryTags
import com.netflix.spinnaker.clouddriver.docker.registry.cache.DefaultCacheDataBuilder
import com.netflix.spinnaker.clouddriver.docker.registry.cache.Keys
import com.netflix.spinnaker.clouddriver.docker.registry.provider.DockerRegistryProvider
import com.netflix.spinnaker.clouddriver.docker.registry.security.DockerRegistryCredentials
import groovy.util.logging.Slf4j

import static java.util.Collections.unmodifiableSet

@Slf4j
class DockerRegistryImageCachingAgent implements CachingAgent, AccountAware {
  static final Set<AgentDataType> types = unmodifiableSet([
    AgentDataType.Authority.AUTHORITATIVE.forType(Keys.Namespace.TAGGED_IMAGE.ns)
  ] as Set)

  private DockerRegistryCredentials credentials
  private DockerRegistryCloudProvider dockerRegistryCloudProvider
  private String accountName

  DockerRegistryImageCachingAgent(DockerRegistryCloudProvider dockerRegistryCloudProvider,
                                  String accountName,
                                  DockerRegistryCredentials credentials) {
    this.dockerRegistryCloudProvider = dockerRegistryCloudProvider
    this.accountName = accountName
    this.credentials = credentials
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
    "${accountName}/${DockerRegistryImageCachingAgent.simpleName}"
  }

  @Override
  String getProviderName() {
    DockerRegistryProvider.PROVIDER_NAME
  }

  private Map<String, Set<String>> loadTags() {
    credentials.repositories.collectEntries {
      DockerRegistryTags tags = credentials.client.getTags(it)
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

    tagMap.forEach { repository, tags ->
      tags.forEach { tag ->
        def tagKey = Keys.getTaggedImageKey(accountName, repository, tag)
        cachedTags[tagKey].with {
          attributes.name = "${repository}:${tag}".toString()
          attributes.account = accountName
        }
      }

      null
    }

    log.info("Caching ${cachedTags.size()} tagged images in ${agentType}")

    new DefaultCacheResult([
      (Keys.Namespace.TAGGED_IMAGE.ns): cachedTags.values().collect({ builder -> builder.build() }),
    ])
  }
}
