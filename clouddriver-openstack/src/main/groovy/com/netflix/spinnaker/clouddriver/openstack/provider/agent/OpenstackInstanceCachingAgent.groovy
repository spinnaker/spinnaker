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

import com.netflix.spinnaker.cats.agent.*
import com.netflix.spinnaker.cats.provider.ProviderCache
import com.netflix.spinnaker.clouddriver.openstack.cache.Keys
import com.netflix.spinnaker.clouddriver.openstack.provider.OpenstackInfastructureProvider
import com.netflix.spinnaker.clouddriver.openstack.provider.view.MutableCacheData
import com.netflix.spinnaker.clouddriver.openstack.security.OpenstackNamedAccountCredentials
import groovy.util.logging.Slf4j
import org.openstack4j.model.compute.Server

import static com.netflix.spinnaker.cats.agent.AgentDataType.Authority.AUTHORITATIVE

@Slf4j
class OpenstackInstanceCachingAgent implements CachingAgent, AccountAware {

  OpenstackNamedAccountCredentials account
  String region

  static final Set<AgentDataType> types = Collections.unmodifiableSet([
    AUTHORITATIVE.forType(Keys.Namespace.INSTANCES.ns)
  ] as Set)


  OpenstackInstanceCachingAgent(OpenstackNamedAccountCredentials account, String region) {
    this.account = account
    this.region = region
  }

  @Override
  String getProviderName() {
    OpenstackInfastructureProvider.PROVIDER_NAME
  }

  @Override
  String getAgentType() {
    "${account.name}/${region}/${OpenstackInstanceCachingAgent.simpleName}"
  }

  @Override
  String getAccountName() {
    account.name
  }

  @Override
  Collection<AgentDataType> getProvidedDataTypes() {
    types
  }

  @Override
  CacheResult loadData(ProviderCache providerCache) {
    Map<String, MutableCacheData> cachedInstances = MutableCacheData.mutableCacheMap()

    List<Server> servers = this.account.credentials.provider.getInstances(region)

    servers?.each { Server server ->
      String id = server.id
      String key = Keys.getInstanceKey(id, accountName, region)
      cachedInstances[key].with {
        attributes.name = server.name
        attributes.region = region
        attributes.metadata = server.metadata
        attributes.zone = server.availabilityZone
        attributes.instanceId = id
        attributes.launchedTime = server.launchedAt?.time ?: -1
        attributes.status = server.status
        attributes.keyName = server.keyName
      }
    }

    new DefaultCacheResult([(Keys.Namespace.INSTANCES.ns): cachedInstances.values()])
  }
}
