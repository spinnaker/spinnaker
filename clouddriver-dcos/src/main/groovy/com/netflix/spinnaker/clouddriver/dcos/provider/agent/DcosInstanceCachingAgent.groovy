/*
 * Copyright 2017 Cerner Corporation
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

package com.netflix.spinnaker.clouddriver.dcos.provider.agent

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.cats.agent.*
import com.netflix.spinnaker.cats.provider.ProviderCache
import com.netflix.spinnaker.clouddriver.dcos.DcosClientProvider
import com.netflix.spinnaker.clouddriver.dcos.cache.Keys
import com.netflix.spinnaker.clouddriver.dcos.deploy.util.id.DcosSpinnakerAppId
import com.netflix.spinnaker.clouddriver.dcos.model.DcosInstance
import com.netflix.spinnaker.clouddriver.dcos.provider.DcosProvider
import com.netflix.spinnaker.clouddriver.dcos.provider.MutableCacheData
import com.netflix.spinnaker.clouddriver.dcos.security.DcosAccountCredentials
import groovy.util.logging.Slf4j
import mesosphere.dcos.client.DCOS
import mesosphere.marathon.client.model.v2.Deployment
import mesosphere.marathon.client.model.v2.Task

import static com.netflix.spinnaker.cats.agent.AgentDataType.Authority.AUTHORITATIVE

@Slf4j
class DcosInstanceCachingAgent implements CachingAgent, AccountAware {

  private final String accountName
  private final String clusterName
  private final DCOS dcosClient
  private final ObjectMapper objectMapper

  static final Set<AgentDataType> types = Collections.unmodifiableSet([
                                                                        AUTHORITATIVE.forType(Keys.Namespace.INSTANCES.ns),
                                                                      ] as Set)

  DcosInstanceCachingAgent(String accountName,
                           String clusterName,
                           DcosAccountCredentials credentials,
                           DcosClientProvider clientProvider,
                           ObjectMapper objectMapper) {
    this.accountName = accountName
    this.clusterName = clusterName
    this.objectMapper = objectMapper
    this.dcosClient = clientProvider.getDcosClient(credentials, clusterName)
  }

  @Override
  Collection<AgentDataType> getProvidedDataTypes() {
    return types
  }

  @Override
  String getProviderName() {
    DcosProvider.name
  }

  @Override
  String getAccountName() {
    return accountName
  }

  @Override
  String getAgentType() {
    "${accountName}/${clusterName}/${DcosInstanceCachingAgent.simpleName}"
  }

  @Override
  CacheResult loadData(ProviderCache providerCache) {
    log.info("Loading tasks in $agentType")

    // The tasks API returns all tasks, but we want to ensure we only cache ones valid for the current account.
    def tasks = dcosClient.getTasks().tasks.findAll {
      DcosSpinnakerAppId.parse(it.appId, accountName).isPresent()
    }
    def deployments = dcosClient.getDeployments()

    return buildCacheResult(tasks, deployments)
  }

  private CacheResult buildCacheResult(List<Task> tasks, List<Deployment> deployments) {
    log.info("Describing items in ${agentType}")

    Map<String, MutableCacheData> cachedInstances = MutableCacheData.mutableCacheMap()

    for (Task task : tasks) {
      if (!task) {
        continue
      }

      def deploymentsActive = deployments.stream().filter({ it.affectedApps.contains(task.appId) }).count() > 0
      String safeGroup = DcosSpinnakerAppId.parse(task.getAppId()).get().getSafeGroup()
      def groupName = clusterName
      if (!safeGroup.isEmpty()) {
        groupName = "${clusterName}_${safeGroup}"
      }

      def key = Keys.getInstanceKey(accountName, groupName, task.id)

      cachedInstances[key].with {
        attributes.name = task.id
        attributes.instance = new DcosInstance(task, accountName, clusterName, deploymentsActive)
      }
    }

    log.info("Caching ${cachedInstances.size()} instances in ${agentType}")

    return new DefaultCacheResult([
                                    (Keys.Namespace.INSTANCES.ns): cachedInstances.values(),
                                  ], [:])
  }
}
