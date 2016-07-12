/*
 * Copyright 2016 Target, Inc.
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

package com.netflix.spinnaker.clouddriver.openstack.provider.agent

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.cats.agent.AgentDataType
import com.netflix.spinnaker.cats.agent.CacheResult
import com.netflix.spinnaker.cats.provider.ProviderCache
import com.netflix.spinnaker.clouddriver.openstack.cache.CacheResultBuilder
import com.netflix.spinnaker.clouddriver.openstack.cache.Keys
import com.netflix.spinnaker.clouddriver.openstack.model.OpenstackImage
import com.netflix.spinnaker.clouddriver.openstack.security.OpenstackNamedAccountCredentials
import groovy.util.logging.Slf4j
import org.openstack4j.model.image.Image

import static com.netflix.spinnaker.cats.agent.AgentDataType.Authority.AUTHORITATIVE
import static com.netflix.spinnaker.clouddriver.openstack.cache.Keys.Namespace.IMAGES
import static com.netflix.spinnaker.clouddriver.openstack.provider.OpenstackInfrastructureProvider.ATTRIBUTES

@Slf4j
class OpenstackImageCachingAgent extends AbstractOpenstackCachingAgent {

  static final Set<AgentDataType> types = Collections.unmodifiableSet([
    AUTHORITATIVE.forType(IMAGES.ns)
  ] as Set)

  final ObjectMapper objectMapper

  OpenstackImageCachingAgent(
    final OpenstackNamedAccountCredentials account, final String region, final ObjectMapper objectMapper) {
    super(account, region)
    this.objectMapper = objectMapper
  }

  @Override
  String getAgentType() {
    "${account.name}/${region}/${OpenstackImageCachingAgent.simpleName}"
  }

  @Override
  Collection<AgentDataType> getProvidedDataTypes() {
    types
  }

  @Override
  CacheResult loadData(ProviderCache providerCache) {
    log.info("Describing items in ${agentType}")

    CacheResultBuilder cacheResultBuilder = new CacheResultBuilder()

    List<Image> images = this.clientProvider.listImages(region)

    images?.each { Image image ->
      cacheResultBuilder.namespace(IMAGES.ns).keep(Keys.getImageKey(image.id, accountName, region)).with {
        attributes = objectMapper.convertValue(buildImage(image), ATTRIBUTES)
      }
    }

    log.info("Caching ${cacheResultBuilder.namespace(IMAGES.ns).keepSize()} items in ${agentType}")

    cacheResultBuilder.build()
  }

  OpenstackImage buildImage(Image image) {
    OpenstackImage.builder()
      .id(image.id)
      .status(image.status?.value())
      .size(image.size)
      .location(image.location)
      .createdAt(image.createdAt?.time)
      .deletedAt(image.deletedAt?.time)
      .updatedAt(image.updatedAt?.time)
      .properties(image.properties)
      .name(image.name)
      .build()
  }
}
