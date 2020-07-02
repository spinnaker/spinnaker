/*
 * Copyright 2017 Netflix, Inc.
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

package com.netflix.spinnaker.cats.agent

import com.netflix.spinnaker.cats.cache.DefaultCacheData
import com.netflix.spinnaker.cats.provider.ProviderCache
import com.netflix.spinnaker.cats.provider.ProviderRegistry
import spock.lang.Specification

import static com.netflix.spinnaker.cats.agent.AgentDataType.Authority.AUTHORITATIVE;

class CacheExecutionSpec extends Specification {
  def providerRegistry = Mock(ProviderRegistry)
  def cachingAgent = Mock(CachingAgent)
  def providerCache = Mock(ProviderCache)

  void "should evict keys that were NOT cached by the responsible agent"() {
    given:
    def cacheExecution = new CachingAgent.CacheExecution(providerRegistry)
    def result = new DefaultCacheResult([
      "securityGroups": [new DefaultCacheData("securityGroups:foo:test:us-west-1", [:], [:])]
    ], [:])

    when:
    cacheExecution.storeAgentResult(cachingAgent, result)

    then:
    1 * cachingAgent.getProvidedDataTypes() >> {
      return [
        AUTHORITATIVE.forType("securityGroups")
      ]
    }
    1 * cachingAgent.getCacheKeyPatterns() >> {
      return [
        "securityGroups": "securityGroups:*:test:us-west-1"
      ]
    }
    1 * providerCache.filterIdentifiers("securityGroups", "securityGroups:*:test:us-west-1") >> {
      return [
        "securityGroups:foo:test:us-west-1",
        "securityGroups:bar:test:us-west-1"
      ]
    }
    1 * providerRegistry.getProviderCache(_) >> { return providerCache }

    result.evictions["securityGroups"] == ["securityGroups:bar:test:us-west-1"]
  }

  void "should skip stale keys check if agent supplies no cache key patterns"() {
    given:
    def cacheExecution = new CachingAgent.CacheExecution(providerRegistry)
    def result = new DefaultCacheResult([
      "securityGroups": [new DefaultCacheData("securityGroups:foo:test:us-west-1", [:], [:])]
    ], [:])

    when:
    cacheExecution.storeAgentResult(cachingAgent, result)

    then:
    1 * cachingAgent.getProvidedDataTypes() >> {
      return [
        AUTHORITATIVE.forType("securityGroups")
      ]
    }
    1 * cachingAgent.getCacheKeyPatterns() >> { return Optional.empty() }
    1 * providerRegistry.getProviderCache(_) >> { return providerCache }

    result.evictions.isEmpty()
  }
}
