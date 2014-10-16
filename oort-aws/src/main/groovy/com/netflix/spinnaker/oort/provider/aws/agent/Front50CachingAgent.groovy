/*
 * Copyright 2014 Netflix, Inc.
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

package com.netflix.spinnaker.oort.provider.aws.agent

import com.netflix.spinnaker.amos.aws.NetflixAmazonCredentials
import com.netflix.spinnaker.cats.agent.AgentDataType
import com.netflix.spinnaker.cats.agent.CacheResult
import com.netflix.spinnaker.cats.agent.CachingAgent
import com.netflix.spinnaker.cats.agent.DefaultCacheResult
import com.netflix.spinnaker.cats.cache.CacheData
import com.netflix.spinnaker.cats.cache.DefaultCacheData
import com.netflix.spinnaker.oort.data.aws.Keys
import com.netflix.spinnaker.oort.provider.aws.AwsProvider
import org.springframework.web.client.RestTemplate

import static com.netflix.spinnaker.cats.agent.AgentDataType.Authority.AUTHORITATIVE
import static com.netflix.spinnaker.oort.data.aws.Keys.Namespace.APPLICATIONS

class Front50CachingAgent implements CachingAgent, OnDemandAgent {

  static final Set<AgentDataType> types = Collections.unmodifiableSet([
    AUTHORITATIVE.forType(APPLICATIONS.ns)
  ] as Set)

    @Override
  String getProviderName() {
    AwsProvider.PROVIDER_NAME
  }

  @Override
  String getAgentType() {
    "${account.name}/${Front50CachingAgent.simpleName}"
  }

  @Override
  Collection<AgentDataType> getProvidedDataTypes() {
    types
  }

  final NetflixAmazonCredentials account
  final RestTemplate restTemplate

  Front50CachingAgent(NetflixAmazonCredentials account, RestTemplate restTemplate) {
    this.account = account
    this.restTemplate = restTemplate
  }

  @Override
  CacheResult loadData() {
    def list = (List<Map<String, String>>) restTemplate.getForObject("${account.front50}/applications", List)

    Collection<CacheData> results = list.collect { Map<String, String> attributes ->
      new DefaultCacheData(Keys.getApplicationKey(attributes.name.toLowerCase()), attributes, [:])
    }

    new DefaultCacheResult((APPLICATIONS.ns): results)
  }

  @Override
  boolean handles(String type) {
    type == "Front50Applications"
  }

  @Override
  OnDemandAgent.OnDemandResult handle(Map<String, ? extends Object> data) {
    new OnDemandAgent.OnDemandResult(sourceAgentType: getAgentType(), cacheResult: loadData(), authoritativeTypes: [APPLICATIONS.ns])
  }
}
