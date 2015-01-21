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

package com.netflix.spinnaker.cats.test

import com.netflix.spinnaker.cats.agent.AgentDataType
import com.netflix.spinnaker.cats.agent.CacheResult
import com.netflix.spinnaker.cats.agent.CachingAgent
import com.netflix.spinnaker.cats.agent.DefaultCacheResult
import com.netflix.spinnaker.cats.cache.CacheData
import com.netflix.spinnaker.cats.provider.ProviderCache

import static com.netflix.spinnaker.cats.agent.AgentDataType.Authority.AUTHORITATIVE
import static com.netflix.spinnaker.cats.agent.AgentDataType.Authority.INFORMATIVE

class TestAgent implements CachingAgent {

    String scope = UUID.randomUUID().toString()
    Set<String> authoritative = []
    Set<String> types = []
    Map<String, Collection<CacheData>> results = [:].withDefault { new HashSet<CacheData>() }

    @Override
    String getProviderName() {
        TestProvider.PROVIDER_NAME
    }

    @Override
    String getAgentType() {
        "$scope/${TestAgent.simpleName}"
    }

    @Override
    Collection<AgentDataType> getProvidedDataTypes() {
        (types + authoritative).collect {
            authoritative.contains(it) ? AUTHORITATIVE.forType(it) : INFORMATIVE.forType(it)
        }
    }

    @Override
    CacheResult loadData(ProviderCache cache) {
        new DefaultCacheResult(results)
    }
}
