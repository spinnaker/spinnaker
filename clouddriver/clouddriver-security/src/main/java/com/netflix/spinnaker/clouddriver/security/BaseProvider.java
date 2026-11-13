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
import com.netflix.spinnaker.cats.agent.AccountAware;
import com.netflix.spinnaker.cats.agent.Agent;
import com.netflix.spinnaker.cats.agent.AgentScheduler;
import com.netflix.spinnaker.cats.agent.AgentSchedulerAware;
import com.netflix.spinnaker.cats.module.CatsModule;
import com.netflix.spinnaker.cats.module.CatsModuleAware;
import com.netflix.spinnaker.cats.provider.Provider;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class BaseProvider extends AgentSchedulerAware implements Provider {
  private static final Logger log = LoggerFactory.getLogger(BaseProvider.class);

  private final Collection<Agent> agents = Collections.newSetFromMap(new ConcurrentHashMap<>());

  // `agents`, `accountByAgent` and `agentCountByAccount` are only changed together while holding
  // this lock, so the account index always describes exactly the agents in `agents`. Readers don't
  // take the lock.
  private final Object agentsLock = new Object();

  // The account name each AccountAware agent reported when it was added. Removal uses this value
  // rather than asking the agent again, so the index stays correct even if the name changes.
  private final Map<Agent, String> accountByAgent = new HashMap<>();

  private final Map<String, Integer> agentCountByAccount = new ConcurrentHashMap<>();

  public final Collection<Agent> getAgents() {
    return ImmutableList.copyOf(agents);
  }

  /**
   * @return a snapshot of the names of the accounts that have at least one {@link AccountAware}
   *     agent in this provider
   */
  final Set<String> getScheduledAccounts() {
    return new HashSet<>(agentCountByAccount.keySet());
  }

  public final void addAgents(Collection<? extends Agent> agentsToSchedule) {
    // Read account names before taking the lock, so agent code never runs while it's held.
    Agent[] batch = agentsToSchedule.toArray(new Agent[0]);
    String[] accountNames = new String[batch.length];
    for (int i = 0; i < batch.length; i++) {
      accountNames[i] = accountNameOf(batch[i]);
    }

    synchronized (agentsLock) {
      for (int i = 0; i < batch.length; i++) {
        if (agents.add(batch[i]) && accountNames[i] != null) {
          accountByAgent.put(batch[i], accountNames[i]);
          agentCountByAccount.merge(accountNames[i], 1, Integer::sum);
        }
      }
    }

    AgentScheduler agentScheduler = getAgentScheduler();
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
          AgentScheduler scheduler = getAgentScheduler();
          List<Agent> agentsToDelete =
              agents.stream()
                  .filter(agent -> agent.handlesAccount(nameOfDeletedAccount))
                  .collect(Collectors.toList());
          if (scheduler != null) {
            agentsToDelete.forEach(scheduler::unschedule);
          }
          synchronized (agentsLock) {
            agentsToDelete.forEach(
                agent -> {
                  if (agents.remove(agent)) {
                    unindexAgent(agent);
                  }
                });
          }
        });
  }

  private static String accountNameOf(Agent agent) {
    if (!(agent instanceof AccountAware)) {
      return null;
    }
    try {
      return ((AccountAware) agent).getAccountName();
    } catch (RuntimeException e) {
      // A failing getAccountName must not stop the agent from being added and scheduled.
      log.warn(
          "Unable to read the account name of agent {}; it won't count as scheduled for any account",
          agent.getAgentType(),
          e);
      return null;
    }
  }

  private void unindexAgent(Agent agent) {
    String accountName = accountByAgent.remove(agent);
    if (accountName == null) {
      return;
    }
    agentCountByAccount.computeIfPresent(
        accountName, (name, count) -> count == 1 ? null : count - 1);
  }
}
