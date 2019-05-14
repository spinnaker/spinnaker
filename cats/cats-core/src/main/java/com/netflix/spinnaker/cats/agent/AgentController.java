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

package com.netflix.spinnaker.cats.agent;

import com.netflix.spinnaker.cats.provider.Provider;
import com.netflix.spinnaker.cats.provider.ProviderRegistry;

/**
 * AgentController schedules an AgentExecution for each Agent in each Provider in the
 * ProviderRegistry.
 *
 * <p>When the AgentControllers AgentExecution is invoked, it will trigger a load and cache cycle
 * for that agent.
 */
public class AgentController {
  public AgentController(
      ProviderRegistry providerRegistry,
      AgentScheduler agentScheduler,
      ExecutionInstrumentation executionInstrumentation) {
    for (Provider provider : providerRegistry.getProviders()) {
      if (provider instanceof AgentSchedulerAware) {
        ((AgentSchedulerAware) provider).setAgentScheduler(agentScheduler);
      }

      for (Agent agent : provider.getAgents()) {
        agentScheduler.schedule(
            agent, agent.getAgentExecution(providerRegistry), executionInstrumentation);
      }
    }
  }
}
