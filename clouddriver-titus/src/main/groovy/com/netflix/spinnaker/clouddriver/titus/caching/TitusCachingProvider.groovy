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

package com.netflix.spinnaker.clouddriver.titus.caching
import com.netflix.spinnaker.cats.agent.Agent
import com.netflix.spinnaker.cats.agent.CachingAgent
import com.netflix.spinnaker.cats.provider.Provider
import com.netflix.spinnaker.clouddriver.aws.provider.agent.HealthProvidingCachingAgent //TODO-Move health agent to common module

class TitusCachingProvider implements Provider {

  public static final String PROVIDER_NAME = TitusCachingProvider.simpleName

  private final Collection<CachingAgent> agents
  private final Collection<HealthProvidingCachingAgent> healthAgents

  TitusCachingProvider(Collection<CachingAgent> agents) {
    this.agents = Collections.unmodifiableCollection(agents)
    this.healthAgents = Collections.unmodifiableCollection(agents.findAll { it instanceof HealthProvidingCachingAgent } as List<HealthProvidingCachingAgent>)
  }

  @Override
  String getProviderName() {
    PROVIDER_NAME
  }

  Collection<HealthProvidingCachingAgent> getHealthAgents() {
    def allHealthAgents = []
    allHealthAgents.addAll(this.healthAgents)
    Collections.unmodifiableCollection(allHealthAgents)
  }

  @Override
  Collection<Agent> getAgents() {
    agents
  }
}
