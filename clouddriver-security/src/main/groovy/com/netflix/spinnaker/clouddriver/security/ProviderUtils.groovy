/*
 * Copyright 2015 Google, Inc.
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

package com.netflix.spinnaker.clouddriver.security

import com.netflix.spinnaker.cats.agent.AccountAware
import com.netflix.spinnaker.cats.agent.Agent
import com.netflix.spinnaker.cats.agent.AgentScheduler
import com.netflix.spinnaker.cats.agent.AgentSchedulerAware
import com.netflix.spinnaker.cats.module.CatsModule
import com.netflix.spinnaker.cats.module.CatsModuleAware
import com.netflix.spinnaker.cats.provider.Provider
import com.netflix.spinnaker.cats.provider.ProviderRegistry
import groovy.util.logging.Slf4j

import java.util.concurrent.ConcurrentHashMap

@Slf4j
/**
 * This class contains a set of static utility methods that are used when credentials are refreshed in a running system.
 */
public class ProviderUtils {

  public static Set<String> getScheduledAccounts(Provider provider) {
    provider.agents.findAll { agent ->
      agent instanceof AccountAware
    }.collect { agent ->
      ((AccountAware)agent).accountName
    } as Set
  }

  /**
   * Build a thread-safe set containing each account in the accountCredentialsRepository that is of type
   * credentialsType.
   */
  public static <T extends AccountCredentials> Set<T> buildThreadSafeSetOfAccounts(AccountCredentialsRepository accountCredentialsRepository, Class<T> credentialsType) {
    buildThreadSafeSetOfAccounts(accountCredentialsRepository, credentialsType, null)
  }

  /**
   * Build a thread-safe set containing each account in the accountCredentialsRepository that is of type
   * credentialsType, and (if specified) of provdierVersion version.
   */
  public static <T extends AccountCredentials> Set<T> buildThreadSafeSetOfAccounts(AccountCredentialsRepository accountCredentialsRepository, Class<T> credentialsType, ProviderVersion version) {
    def allAccounts = Collections.newSetFromMap(new ConcurrentHashMap<T, Boolean>())
    allAccounts.addAll(accountCredentialsRepository.all.findResults { credentialsType.isInstance(it) ? credentialsType.cast(it) : null })

    if (version != null) {
      allAccounts = allAccounts.findAll { acc -> acc.providerVersion == version }
    }

    return allAccounts
  }

  /**
   * Use the agent scheduler associated with the provider to schedule each agent.
   */
  public static void rescheduleAgents(Provider provider, List<Agent> agentsToSchedule) {
    if (provider instanceof AgentSchedulerAware) {
      AgentScheduler agentScheduler = ((AgentSchedulerAware)provider).agentScheduler

      if (agentScheduler instanceof CatsModuleAware) {
        CatsModule catsModule = agentScheduler.catsModule

        agentsToSchedule.each { agent ->
          agentScheduler.schedule(agent, agent.getAgentExecution(catsModule.providerRegistry), catsModule.executionInstrumentation)
        }
      }
    }
  }

  /**
   * Determine the accounts that need to be deleted and added with respect to the account credentials repository, and
   * delete the accounts. It is the responsibility of the caller to add the identified accounts after this method
   * returns. Returns a list of size 2: the first element is the set of accounts to add, and the second element is a set
   * containing the names of the accounts that were deleted from the account credentials repository.
   */
  public static List calculateAccountDeltas(def accountCredentialsRepository, def credentialsType, def desiredAccounts) {
    def oldNames = accountCredentialsRepository.all.findAll {
      credentialsType.isInstance(it)
    }.collect {
      it.name
    }

    def newNames = desiredAccounts.collect {
      it.name
    }

    def accountNamesToDelete = oldNames - newNames
    def accountNamesToAdd = newNames - oldNames

    if (accountNamesToDelete) {
      log.info("Deleting accounts $accountNamesToDelete of type $credentialsType.simpleName...")
    }

    if (accountNamesToAdd) {
      log.info("Adding accounts $accountNamesToAdd of type $credentialsType.simpleName...")
    }

    accountNamesToDelete.each { accountName ->
      accountCredentialsRepository.delete(accountName)
    }

    def accountsToAdd = desiredAccounts.findAll { account ->
      accountNamesToAdd.contains(account.name)
    }

    return [accountsToAdd, accountNamesToDelete]
  }

  /**
   * Use the provider registry and agent scheduler associated with the cats module to unschedule and deregister
   * each agent that handles one of the specified accounts.
   */
  public static void unscheduleAndDeregisterAgents(def namesOfDeletedAccounts, def catsModule) {
    namesOfDeletedAccounts.each { nameOfDeletedAccount ->
      ProviderRegistry providerRegistry = catsModule.getProviderRegistry()

      for (Provider provider : providerRegistry.providers) {
        List<Agent> agentsToDelete = []

        if (provider instanceof AgentSchedulerAware) {
          AgentScheduler agentScheduler = ((AgentSchedulerAware)provider).agentScheduler

          provider.agents.each { agent ->
            if (agent.handlesAccount(nameOfDeletedAccount)) {
              agentScheduler?.unschedule(agent)
              agentsToDelete << agent
            }
          }
        }

        provider.agents.removeAll(agentsToDelete)
      }
    }
  }
}
