/*
 * Copyright 2020 Armory
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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
 *
 */

package com.netflix.spinnaker.clouddriver.security;

import com.google.common.collect.ImmutableList;
import com.netflix.spinnaker.cats.agent.Agent;
import com.netflix.spinnaker.cats.agent.AgentScheduler;
import com.netflix.spinnaker.cats.agent.AgentSchedulerAware;
import com.netflix.spinnaker.cats.module.CatsModule;
import com.netflix.spinnaker.cats.module.CatsModuleAware;
import com.netflix.spinnaker.cats.provider.Provider;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public abstract class BaseProvider extends AgentSchedulerAware implements Provider {
  private final Collection<Agent> agents = Collections.newSetFromMap(new ConcurrentHashMap<>());

  public final Collection<Agent> getAgents() {
    return ImmutableList.copyOf(agents);
  }

  public final void addAgents(Collection<? extends Agent> agentsToSchedule) {
    agents.addAll(agentsToSchedule);

    AgentScheduler<?> agentScheduler = getAgentScheduler();
    if (agentScheduler instanceof CatsModuleAware) {
      CatsModule catsModule = ((CatsModuleAware) agentScheduler).getCatsModule();
      agentsToSchedule.forEach(
          agent ->
              agentScheduler.schedule(
                  agent,
                  agent.getAgentExecution(catsModule.getProviderRegistry()),
                  catsModule.getExecutionInstrumentation()));
    }
  }

  public final void removeAgentsForAccounts(Collection<String> namesOfDeletedAccounts) {
    namesOfDeletedAccounts.forEach(
        nameOfDeletedAccount -> {
          AgentScheduler<?> scheduler = getAgentScheduler();
          List<Agent> agentsToDelete =
              agents.stream()
                  .filter(agent -> agent.handlesAccount(nameOfDeletedAccount))
                  .collect(Collectors.toList());
          if (scheduler != null) {
            agentsToDelete.forEach(scheduler::unschedule);
          }
          agents.removeAll(agentsToDelete);
        });
  }
}
