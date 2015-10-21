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

import com.netflix.spinnaker.cats.agent.AgentScheduler;
import com.netflix.spinnaker.cats.agent.CompositeExecutionInstrumentation;
import com.netflix.spinnaker.cats.agent.DefaultAgentScheduler;
import com.netflix.spinnaker.cats.agent.ExecutionInstrumentation;
import com.netflix.spinnaker.cats.agent.NoopExecutionInstrumentation;
import com.netflix.spinnaker.cats.cache.Cache;
import com.netflix.spinnaker.cats.cache.NamedCacheFactory;
import com.netflix.spinnaker.cats.mem.InMemoryNamedCacheFactory;
import com.netflix.spinnaker.cats.provider.Provider;
import com.netflix.spinnaker.cats.provider.ProviderRegistry;

import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedList;
import java.util.concurrent.TimeUnit;

/**
 * A CatsModule should provide the component configuration for caching a
 * collection of Providers, and return a readable view Cache for access to
 * the cached data.
 */
public interface CatsModule {

    NamedCacheFactory getNamedCacheFactory();

    ProviderRegistry getProviderRegistry();

    AgentScheduler getAgentScheduler();

    Cache getView();

    ExecutionInstrumentation getExecutionInstrumentation();

    public static class Builder {
        private NamedCacheFactory cacheFactory;
        private AgentScheduler scheduler;
        private Collection<ExecutionInstrumentation> instrumentations = new LinkedList<>();

        public Builder scheduler(AgentScheduler agentScheduler) {
            if (this.scheduler != null) {
                throw new IllegalStateException("AgentScheduler already configured");
            }
            this.scheduler = agentScheduler;
            return this;
        }

        public Builder intervalScheduler(long interval) {
            return scheduler(new DefaultAgentScheduler(interval, TimeUnit.MILLISECONDS));
        }

        public Builder intervalScheduler(long interval, TimeUnit unit) {
            return intervalScheduler(unit.toMillis(interval));
        }

        public Builder instrumentation(Collection<ExecutionInstrumentation> instrumentation) {
            instrumentations.addAll(instrumentation);
            return this;
        }

        public Builder instrumentation(ExecutionInstrumentation... instrumentation) {
            return instrumentation(Arrays.asList(instrumentation));
        }

        public Builder cacheFactory(NamedCacheFactory namedCacheFactory) {
            if (this.cacheFactory != null) {
                throw new IllegalStateException("NamedCacheFactory already configured");
            }
            this.cacheFactory = namedCacheFactory;
            return this;
        }

        public CatsModule build(Provider... providers) {
            return build(Arrays.asList(providers));
        }

        public CatsModule build(Collection<Provider> providers) {
            final ExecutionInstrumentation instrumentation;
            if (instrumentations.isEmpty()) {
                instrumentation = new NoopExecutionInstrumentation();
            } else {
                instrumentation = new CompositeExecutionInstrumentation(instrumentations);
            }

            if (scheduler == null) {
                scheduler = new DefaultAgentScheduler();
            }

            if (cacheFactory == null) {
                cacheFactory = new InMemoryNamedCacheFactory();
            }
            return new DefaultCatsModule(providers, cacheFactory, scheduler, instrumentation);
        }
    }

}
