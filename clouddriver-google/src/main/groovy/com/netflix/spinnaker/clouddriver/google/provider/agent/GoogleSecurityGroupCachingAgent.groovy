/*
 * Copyright 2015 Google, Inc.
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

package com.netflix.spinnaker.clouddriver.google.provider.agent

import com.fasterxml.jackson.databind.ObjectMapper
import com.google.api.services.compute.model.Firewall
import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.cats.agent.AgentDataType
import com.netflix.spinnaker.cats.agent.CacheResult
import com.netflix.spinnaker.cats.provider.ProviderCache
import com.netflix.spinnaker.clouddriver.cache.OnDemandAgent
import com.netflix.spinnaker.clouddriver.cache.OnDemandMetricsSupport
import com.netflix.spinnaker.clouddriver.google.GoogleCloudProvider
import com.netflix.spinnaker.clouddriver.google.cache.CacheResultBuilder
import com.netflix.spinnaker.clouddriver.google.cache.Keys
import com.netflix.spinnaker.clouddriver.google.security.GoogleNamedAccountCredentials
import groovy.util.logging.Slf4j

import static com.netflix.spinnaker.cats.agent.AgentDataType.Authority.AUTHORITATIVE
import static com.netflix.spinnaker.clouddriver.google.cache.Keys.Namespace.SECURITY_GROUPS

@Slf4j
class GoogleSecurityGroupCachingAgent extends AbstractGoogleCachingAgent implements OnDemandAgent {

  final Set<AgentDataType> providedDataTypes = [
      AUTHORITATIVE.forType(SECURITY_GROUPS.ns)
  ] as Set

  String agentType = "${accountName}/global/${GoogleSecurityGroupCachingAgent.simpleName}"
  String onDemandAgentType = "${agentType}-OnDemand"
  final OnDemandMetricsSupport metricsSupport

  GoogleSecurityGroupCachingAgent(String clouddriverUserAgentApplicationName,
                                  GoogleNamedAccountCredentials credentials,
                                  ObjectMapper objectMapper,
                                  Registry registry) {
    super(clouddriverUserAgentApplicationName,
          credentials,
          objectMapper)
    this.metricsSupport = new OnDemandMetricsSupport(
        registry,
        this,
        "${GoogleCloudProvider.GCE}:${OnDemandAgent.OnDemandType.SecurityGroup}")
  }

  @Override
  OnDemandAgent.OnDemandResult handle(ProviderCache providerCache, Map<String, ? extends Object> data) {
    if (data.account != accountName || data.region != "global") {
      return null
    }

    List<Firewall> firewallList = metricsSupport.readData {
      loadFirewalls()
    }

    CacheResult result = metricsSupport.transformData {
      buildCacheResult(providerCache, firewallList)
    }

    new OnDemandAgent.OnDemandResult(
        sourceAgentType: getAgentType(),
        authoritativeTypes: [SECURITY_GROUPS.ns],
        cacheResult: result
    )
  }

  @Override
  Collection<Map> pendingOnDemandRequests(ProviderCache providerCache) {
    return []
  }

  @Override
  boolean handles(OnDemandAgent.OnDemandType type, String cloudProvider) {
    type == OnDemandAgent.OnDemandType.SecurityGroup && cloudProvider == GoogleCloudProvider.GCE
  }

  @Override
  CacheResult loadData(ProviderCache providerCache) {
    List<Firewall> firewallList = loadFirewalls()
    buildCacheResult(providerCache, firewallList)
  }

  List<Firewall> loadFirewalls() {
    compute.firewalls().list(project).execute().items as List<Firewall>
  }

  private CacheResult buildCacheResult(ProviderCache providerCache, List<Firewall> firewallList) {
    log.info("Describing items in ${agentType}")

    CacheResultBuilder cacheResultBuilder = new CacheResultBuilder()

    firewallList.collect { Firewall firewall ->
      def securityGroupKey = Keys.getSecurityGroupKey(firewall.getName(),
                                                      firewall.getName(),
                                                      "global",
                                                      accountName)

      cacheResultBuilder.namespace(SECURITY_GROUPS.ns).keep(securityGroupKey).with {
        attributes = [firewall: firewall]
      }
    }

    log.info("Caching ${cacheResultBuilder.namespace(SECURITY_GROUPS.ns).keepSize()} security groups in ${agentType}")

    cacheResultBuilder.build()
  }
}
