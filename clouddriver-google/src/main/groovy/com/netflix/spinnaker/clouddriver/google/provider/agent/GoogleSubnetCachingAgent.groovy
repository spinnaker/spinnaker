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

package com.netflix.spinnaker.clouddriver.google.provider.agent

import com.fasterxml.jackson.databind.ObjectMapper
import com.google.api.services.compute.model.Subnetwork
import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.cats.agent.AgentDataType
import com.netflix.spinnaker.cats.agent.CacheResult
import com.netflix.spinnaker.cats.provider.ProviderCache
import com.netflix.spinnaker.clouddriver.google.cache.CacheResultBuilder
import com.netflix.spinnaker.clouddriver.google.cache.Keys
import com.netflix.spinnaker.clouddriver.google.deploy.GCEUtil
import com.netflix.spinnaker.clouddriver.google.security.GoogleNamedAccountCredentials
import groovy.util.logging.Slf4j

import static com.netflix.spinnaker.cats.agent.AgentDataType.Authority.AUTHORITATIVE
import static com.netflix.spinnaker.clouddriver.google.cache.Keys.Namespace.SUBNETS

@Slf4j
class GoogleSubnetCachingAgent extends AbstractGoogleCachingAgent {

  final String region

  final Set<AgentDataType> providedDataTypes = [
      AUTHORITATIVE.forType(SUBNETS.ns)
  ] as Set

  String agentType = "$accountName/$region/$GoogleSubnetCachingAgent.simpleName"

  GoogleSubnetCachingAgent(String clouddriverUserAgentApplicationName,
                           GoogleNamedAccountCredentials credentials,
                           ObjectMapper objectMapper,
                           Registry registry,
                           String region) {
    super(clouddriverUserAgentApplicationName,
          credentials,
          objectMapper,
          registry)
    this.region = region
  }

  @Override
  CacheResult loadData(ProviderCache providerCache) {
    List<Subnetwork> subnetList = loadSubnets()
    buildCacheResult(providerCache, subnetList)
  }

  List<Subnetwork> loadSubnets() {
    // TODO(duftler): Batch these 2 calls.
    List<Subnetwork> subnets = timeExecute(compute.subnetworks().list(project, region),
                                           "compute.subnetworks.list",
                                           TAG_SCOPE, SCOPE_REGIONAL, TAG_REGION, region).items as List

    if (xpnHostProject) {
      List<Subnetwork> hostSubnets = timeExecute(compute.subnetworks().list(xpnHostProject, region),
                                                 "compute.subnetworks.list",
                                                 TAG_SCOPE, SCOPE_REGIONAL, TAG_REGION, region).items as List

      subnets = (subnets ?: []) + (hostSubnets ?: [])
    }

    return subnets
  }

  private CacheResult buildCacheResult(ProviderCache _, List<Subnetwork> subnetList) {
    log.debug("Describing items in ${agentType}")

    def cacheResultBuilder = new CacheResultBuilder()

    subnetList.each { Subnetwork subnet ->
      def subnetKey = Keys.getSubnetKey(deriveSubnetId(subnet), region, accountName)

      cacheResultBuilder.namespace(SUBNETS.ns).keep(subnetKey).with {
        attributes.subnet = subnet
      }
    }

    log.debug("Caching ${cacheResultBuilder.namespace(SUBNETS.ns).keepSize()} items in ${agentType}")

    cacheResultBuilder.build()
  }

  private String deriveSubnetId(Subnetwork subnet) {
    def subnetProject = GCEUtil.deriveProjectId(subnet.getSelfLink())
    def subnetId = subnet.getName()

    if (subnetProject != project) {
      subnetId = "$subnetProject/$subnetId"
    }

    return subnetId
  }
}
