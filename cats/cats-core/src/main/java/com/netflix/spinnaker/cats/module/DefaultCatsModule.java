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

package com.netflix.spinnaker.cats.module;

import com.netflix.spinnaker.cats.agent.AgentController;
import com.netflix.spinnaker.cats.agent.AgentScheduler;
import com.netflix.spinnaker.cats.agent.ExecutionInstrumentation;
import com.netflix.spinnaker.cats.cache.Cache;
import com.netflix.spinnaker.cats.cache.CompositeCache;
import com.netflix.spinnaker.cats.cache.NamedCacheFactory;
import com.netflix.spinnaker.cats.provider.DefaultProviderRegistry;
import com.netflix.spinnaker.cats.provider.Provider;
import com.netflix.spinnaker.cats.provider.ProviderRegistry;

import java.util.Collection;

public class DefaultCatsModule implements CatsModule {
    private final NamedCacheFactory namedCacheFactory;
    private final ProviderRegistry providerRegistry;
    private final AgentScheduler agentScheduler;
    private final Cache view;
    private final ExecutionInstrumentation executionInstrumentation;

    public DefaultCatsModule(ProviderRegistry registry,
                             Collection<Provider> providers,
                             NamedCacheFactory namedCacheFactory,
                             AgentScheduler agentScheduler,
                             ExecutionInstrumentation executionInstrumentation) {
        if (registry == null) {
          this.providerRegistry = new DefaultProviderRegistry(providers, namedCacheFactory);
        } else {
          this.providerRegistry = registry;
        }

      this.namedCacheFactory = namedCacheFactory;

      this.agentScheduler = agentScheduler;

        if (agentScheduler instanceof CatsModuleAware) {
          ((CatsModuleAware)agentScheduler).setCatsModule(this);
        }

        view = new CompositeCache(providerRegistry.getProviderCaches());
        this.executionInstrumentation = executionInstrumentation;
        new AgentController(providerRegistry, agentScheduler, executionInstrumentation);
    }

    public NamedCacheFactory getNamedCacheFactory() {
        return namedCacheFactory;
    }

    public ProviderRegistry getProviderRegistry() {
        return providerRegistry;
    }

    public AgentScheduler getAgentScheduler() {
        return agentScheduler;
    }

    @Override
    public Cache getView() {
        return view;
    }

    @Override
    public ExecutionInstrumentation getExecutionInstrumentation() {
      return executionInstrumentation;
    }
}
