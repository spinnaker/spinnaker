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

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.google.api.client.googleapis.batch.BatchRequest
import com.google.api.client.googleapis.batch.json.JsonBatchCallback
import com.google.api.client.googleapis.json.GoogleJsonError
import com.google.api.client.http.HttpHeaders
import com.google.api.services.compute.Compute
import com.google.api.services.compute.model.ForwardingRule
import com.netflix.spinnaker.cats.agent.AccountAware
import com.netflix.spinnaker.cats.agent.AgentDataType
import com.netflix.spinnaker.cats.agent.CacheResult
import com.netflix.spinnaker.cats.agent.CachingAgent
import com.netflix.spinnaker.cats.agent.DefaultCacheResult
import com.netflix.spinnaker.cats.cache.CacheData
import com.netflix.spinnaker.cats.cache.DefaultCacheData
import com.netflix.spinnaker.cats.provider.ProviderCache
import com.netflix.spinnaker.clouddriver.google.GoogleCloudProvider
import com.netflix.spinnaker.clouddriver.google.cache.Keys
import com.netflix.spinnaker.clouddriver.google.model.GoogleLoadBalancer
import com.netflix.spinnaker.clouddriver.google.model.GoogleResourceRetriever
import com.netflix.spinnaker.clouddriver.google.model.callbacks.Utils
import com.netflix.spinnaker.clouddriver.google.provider.GoogleInfrastructureProvider
import groovy.transform.TupleConstructor
import groovy.util.logging.Slf4j

import static com.netflix.spinnaker.cats.agent.AgentDataType.Authority.AUTHORITATIVE

@Slf4j
class GoogleLoadBalancerCachingAgent implements CachingAgent, AccountAware {

  // TODO(ttomsu): This will be shared across a few caching agents. Move to common class.
  static final TypeReference<Map<String, Object>> ATTRIBUTES = new TypeReference<Map<String, Object>>() {}

  final GoogleCloudProvider googleCloudProvider
  final String googleApplicationName // "Spinnaker/${version}" HTTP header string
  final String accountName
  final String region
  final String project
  final Compute compute
  final ObjectMapper objectMapper

  final String providerName = GoogleInfrastructureProvider.PROVIDER_NAME
  final Set<AgentDataType> providedDataTypes = Collections.unmodifiableSet([
    AUTHORITATIVE.forType(Keys.Namespace.LOAD_BALANCERS.ns)
  ] as Set)

  String agentType = "${accountName}/${region}/${GoogleLoadBalancerCachingAgent.simpleName}"

  GoogleLoadBalancerCachingAgent(GoogleCloudProvider googleCloudProvider,
                                 String googleApplicationName,
                                 String accountName,
                                 String region,
                                 String project,
                                 Compute compute,
                                 ObjectMapper objectMapper) {
    this.googleCloudProvider = googleCloudProvider
    this.googleApplicationName = googleApplicationName
    this.accountName = accountName
    this.region = region
    this.project = project
    this.compute = compute
    this.objectMapper = objectMapper.enable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
  }

  @Override
  CacheResult loadData(ProviderCache providerCache) {
    List<GoogleLoadBalancer> loadBalancers = getLoadBalancers()
    buildCacheResult(providerCache, loadBalancers)
  }

  List<GoogleLoadBalancer> getLoadBalancers() {
    List<GoogleLoadBalancer> loadBalancers = new ArrayList<GoogleLoadBalancer>()

    BatchRequest forwardingRulesRequest = GoogleResourceRetriever.buildBatchRequest(compute, googleApplicationName)

    ForwardingRulesCallback callback = new ForwardingRulesCallback(loadBalancers)
    compute.forwardingRules().list(project, region).queue(forwardingRulesRequest, callback)

    GoogleResourceRetriever.executeIfRequestsAreQueued(forwardingRulesRequest)

    return loadBalancers
  }

  CacheResult buildCacheResult(ProviderCache providerCache, List<GoogleLoadBalancer> loadBalancers) {
    log.info "Describing items in ${agentType}"

    List<CacheData> data = loadBalancers.collect { GoogleLoadBalancer loadBalancer ->
      Map<String, Object> attributes = objectMapper.convertValue(loadBalancer, ATTRIBUTES)
      def key = Keys.getLoadBalancerKey(googleCloudProvider,
                                        loadBalancer.region,
                                        loadBalancer.account,
                                        loadBalancer.name)
      // TODO(ttomsu): Add server group relationship here.
      new DefaultCacheData(key, attributes, [:])
    }

    log.info "Caching ${data.size()} items in ${agentType}"

    new DefaultCacheResult([(Keys.Namespace.LOAD_BALANCERS.ns): data])
  }

  @TupleConstructor
  class ForwardingRulesCallback<ForwardingRuleList> extends JsonBatchCallback<ForwardingRuleList> {

    List<GoogleLoadBalancer> loadBalancers

    @Override
    void onSuccess(ForwardingRuleList forwardingRuleList, HttpHeaders responseHeaders) throws IOException {
      forwardingRuleList?.items?.each { ForwardingRule forwardingRule ->
        loadBalancers << new GoogleLoadBalancer(
            name: forwardingRule.name,
            account: accountName,
            region: region,
            createdTime: Utils.getTimeFromTimestamp(forwardingRule.creationTimestamp),
            ipAddress: forwardingRule.IPAddress,
            ipProtocol: forwardingRule.IPProtocol,
            portRange: forwardingRule.portRange)
      }
    }

    @Override
    void onFailure(GoogleJsonError e, HttpHeaders responseHeaders) throws IOException {
      log.error e.getMessage()
    }
  }
}
