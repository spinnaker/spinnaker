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

import com.netflix.spinnaker.cats.agent.CachingAgent
import com.netflix.spinnaker.cats.provider.Provider

class TestProvider implements Provider {
    public static final String PROVIDER_NAME = TestProvider.simpleName

    private final Collection<CachingAgent> agents;

    TestProvider(CachingAgent... agents) {
        this(Arrays.asList(agents))
    }

    TestProvider(Collection<CachingAgent> agents) {
        this.agents = agents
    }

    @Override
    String getProviderName() {
        PROVIDER_NAME
    }

    @Override
    Collection<CachingAgent> getAgents() {
        agents
    }
}
