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
import com.google.api.services.compute.Compute
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
import groovy.util.logging.Slf4j

import static com.netflix.spinnaker.cats.agent.AgentDataType.Authority.AUTHORITATIVE
import static com.netflix.spinnaker.clouddriver.google.cache.Keys.Namespace.SECURITY_GROUPS

@Slf4j
class GoogleSecurityGroupCachingAgent extends AbstractGoogleCachingAgent implements OnDemandAgent {

  @Deprecated
  private static final String LEGACY_ON_DEMAND_TYPE = 'GoogleSecurityGroup'

  private static final String ON_DEMAND_TYPE = 'SecurityGroup'

  final OnDemandMetricsSupport metricsSupport

  final Set<AgentDataType> providedDataTypes = [
    AUTHORITATIVE.forType(SECURITY_GROUPS.ns)
  ] as Set

  String agentType = "${accountName}/global/${GoogleSecurityGroupCachingAgent.simpleName}"

  GoogleSecurityGroupCachingAgent(GoogleCloudProvider googleCloudProvider,
                                  String accountName,
                                  String project,
                                  Compute compute,
                                  ObjectMapper objectMapper,
                                  Registry registry) {
    this.googleCloudProvider = googleCloudProvider
    this.accountName = accountName
    this.project = project
    this.compute = compute
    this.objectMapper = objectMapper
    this.metricsSupport = new OnDemandMetricsSupport(registry, this, googleCloudProvider.id + ":" + ON_DEMAND_TYPE)
  }

  @Override
  String getOnDemandAgentType() {
    getAgentType()
  }

  @Override
  OnDemandAgent.OnDemandResult handle(ProviderCache providerCache, Map<String, ? extends Object> data) {
    if (data.account != accountName) {
      return null
    }

    if (data.region != "global") {
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
  boolean handles(String type) {
    type == LEGACY_ON_DEMAND_TYPE
  }

  @Override
  boolean handles(String type, String cloudProvider) {
    type == ON_DEMAND_TYPE && cloudProvider == googleCloudProvider.id
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

    CacheResultBuilder crb = new CacheResultBuilder()

    firewallList.collect { Firewall firewall ->
      def securityGroupKey = Keys.getSecurityGroupKey(googleCloudProvider,
                                                      firewall.getName(),
                                                      firewall.getName(),
                                                      "global",
                                                      accountName)

      crb.namespace(SECURITY_GROUPS.ns).get(securityGroupKey).with {
        attributes = [firewall: firewall]
      }
    }

    log.info("Caching ${crb.namespace(SECURITY_GROUPS.ns).size()} security groups in ${agentType}")

    crb.build()
  }
}
