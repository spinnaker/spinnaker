/*
 * Copyright 2016 Target Inc.
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

import com.netflix.frigga.Names
import com.netflix.spinnaker.cats.agent.AgentDataType
import com.netflix.spinnaker.cats.agent.CacheResult
import com.netflix.spinnaker.cats.provider.ProviderCache
import com.netflix.spinnaker.clouddriver.openstack.cache.CacheResultBuilder
import com.netflix.spinnaker.clouddriver.openstack.cache.Keys
import com.netflix.spinnaker.clouddriver.openstack.security.OpenstackNamedAccountCredentials
import groovy.util.logging.Slf4j
import org.openstack4j.model.heat.Stack

import static com.netflix.spinnaker.cats.agent.AgentDataType.Authority.AUTHORITATIVE
import static com.netflix.spinnaker.cats.agent.AgentDataType.Authority.INFORMATIVE

import static com.netflix.spinnaker.clouddriver.core.provider.agent.Namespace.APPLICATIONS
import static com.netflix.spinnaker.clouddriver.core.provider.agent.Namespace.CLUSTERS
import static com.netflix.spinnaker.clouddriver.core.provider.agent.Namespace.SERVER_GROUPS

//TODO - Implement full server group caching and on-demand caching
@Slf4j
class OpenstackServerGroupCachingAgent extends AbstractOpenstackCachingAgent {

  static final Set<AgentDataType> types = Collections.unmodifiableSet([
    AUTHORITATIVE.forType(SERVER_GROUPS.ns),
    INFORMATIVE.forType(APPLICATIONS.ns),
    INFORMATIVE.forType(CLUSTERS.ns)
  ] as Set)

  OpenstackServerGroupCachingAgent(OpenstackNamedAccountCredentials account, String region) {
    super(account, region)
  }

  @Override
  String getAgentType() {
    "${account.name}/${region}/${OpenstackServerGroupCachingAgent.simpleName}"
  }

  @Override
  Collection<AgentDataType> getProvidedDataTypes() {
    types
  }

  @Override
  CacheResult loadData(ProviderCache providerCache) {
    CacheResultBuilder cacheResultBuilder = new CacheResultBuilder(startTime: Long.MAX_VALUE)

    clientProvider.listStacks(region)?.each { Stack stack ->
      String serverGroupName = stack.name
      Names names = Names.parseName(serverGroupName)
      if (!names && !names.app && !names.cluster) {
        log.info("Skipping server group ${serverGroupName}")
      } else {
        String applicationName = names.app
        String clusterName = names.cluster

        String serverGroupKey = Keys.getServerGroupKey(serverGroupName, accountName, region)
        String clusterKey = Keys.getClusterKey(accountName, applicationName, clusterName)
        String appKey = Keys.getApplicationKey(applicationName)

        cacheResultBuilder.namespace(APPLICATIONS.ns).keep(appKey).with {
          attributes.name = applicationName
          relationships[CLUSTERS.ns].add(clusterKey)
        }

        cacheResultBuilder.namespace(CLUSTERS.ns).keep(clusterKey).with {
          attributes.name = clusterName
          attributes.accountName = accountName
          relationships[APPLICATIONS.ns].add(appKey)
          relationships[SERVER_GROUPS.ns].add(serverGroupKey)
        }

        //TODO - Need to add server group attributes and instance/load balancer relationships
        cacheResultBuilder.namespace(SERVER_GROUPS.ns).keep(serverGroupKey).with {
          relationships[APPLICATIONS.ns].add(appKey)
          relationships[CLUSTERS.ns].add(clusterKey)
        }
      }
    }

    cacheResultBuilder.namespaceBuilders.keySet().each { String namespace ->
      log.info("Caching ${cacheResultBuilder.namespace(namespace).keepSize()} ${namespace} in ${agentType}")
    }

    log.info("Caching ${cacheResultBuilder.onDemand.toKeep.size()} onDemand entries in ${agentType}")
    log.info("Evicting ${cacheResultBuilder.onDemand.toEvict.size()} onDemand entries in ${agentType}")

    cacheResultBuilder.build()
  }
}
