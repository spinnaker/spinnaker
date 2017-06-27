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
import com.netflix.spinnaker.clouddriver.dcos.provider.DcosProvider
import com.netflix.spinnaker.clouddriver.dcos.provider.MutableCacheData
import com.netflix.spinnaker.clouddriver.dcos.security.DcosAccountCredentials
import com.netflix.spinnaker.clouddriver.dcos.security.DcosClusterCredentials
import groovy.util.logging.Slf4j
import mesosphere.dcos.client.DCOS
import mesosphere.dcos.client.DCOSException

import static com.netflix.spinnaker.cats.agent.AgentDataType.Authority.AUTHORITATIVE

@Slf4j
class DcosSecretsCachingAgent implements CachingAgent, AccountAware {

  private final String accountName
  private final String clusterName
  private final DcosClusterCredentials clusterCredentials
  private final DcosAccountCredentials credentials
  private final DCOS dcosClient
  private final ObjectMapper objectMapper

  static final Set<AgentDataType> types = Collections.unmodifiableSet([
                                                                        AUTHORITATIVE.forType(Keys.Namespace.SECRETS.ns),
                                                                      ] as Set)

  DcosSecretsCachingAgent(String accountName,
                          String clusterName,
                          DcosAccountCredentials credentials,
                          DcosClientProvider clientProvider,
                          ObjectMapper objectMapper) {
    this.accountName = accountName
    this.clusterName = clusterName
    this.clusterCredentials = credentials.getCredentialsByCluster(clusterName)
    this.credentials = credentials
    this.dcosClient = clientProvider.getDcosClient(credentials, clusterName)
    this.objectMapper = objectMapper
  }


  @Override
  String getAgentType() {
    "${accountName}/${clusterName}/${DcosSecretsCachingAgent.simpleName}"
  }

  @Override
  String getProviderName() {
    DcosProvider.name
  }

  @Override
  String getAccountName() {
    accountName
  }

  @Override
  Collection<AgentDataType> getProvidedDataTypes() {
    types
  }

  @Override
  CacheResult loadData(ProviderCache providerCache) {
    log.info("Loading secrets in $agentType")

    def secrets = []
    try {
      secrets = dcosClient.listSecrets(clusterCredentials.secretStore, "").secrets
    } catch (DCOSException e) {
      log.error("Unable to cache secrets for account [${accountName}] and cluster [${clusterName}].", e)
    }

    buildCacheResult(secrets)

  }

  private CacheResult buildCacheResult(List<String> secrets) {
    log.info("Describing items in ${agentType}")

    Map<String, MutableCacheData> cachedSecrets = MutableCacheData.mutableCacheMap()

    for (String secretPath : secrets) {

      if (secretPath == null || secretPath.isEmpty()) {
        continue
      }

      def key = Keys.getSecretKey(clusterName, secretPath)
      cachedSecrets[key].with {
        attributes.secretPath = secretPath
      }
    }

    log.info("Caching ${cachedSecrets.size()} secrets in ${agentType}")

    new DefaultCacheResult([
                             (Keys.Namespace.SECRETS.ns): cachedSecrets.values(),
                           ], [:])
  }
}
