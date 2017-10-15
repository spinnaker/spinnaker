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

package com.netflix.spinnaker.clouddriver.dcos

import com.netflix.spinnaker.clouddriver.dcos.security.DcosAccountCredentials
import com.netflix.spinnaker.clouddriver.dcos.security.DcosClusterCredentials
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider
import mesosphere.dcos.client.DCOS
import mesosphere.dcos.client.DCOSClient

import java.util.concurrent.ConcurrentHashMap

import static java.lang.reflect.Proxy.newProxyInstance

class DcosClientProvider {

  private final Map<String, DCOS> dcosClients = new ConcurrentHashMap<>()
  private final AccountCredentialsProvider credentialsProvider

  DcosClientProvider(AccountCredentialsProvider credentialsProvider) {
    this.credentialsProvider = credentialsProvider
  }

  DCOS getDcosClient(DcosAccountCredentials credentials, String clusterName) {
    def compositeKey = DcosClientCompositeKey.buildFromVerbose(credentials.account, clusterName).get()
    def trueCredentials = credentials.getCredentialsByCluster(clusterName)
    def clientInterfaces = new Class<?>[1]
    clientInterfaces[0] = DCOS.class

    return dcosClients.computeIfAbsent(compositeKey.toString(), { k ->
      newProxyInstance(DCOS.class.getClassLoader(), [DCOS.class] as Class<?>[],
              new DcosSpectatorHandler(DCOSClient.getInstance(trueCredentials.dcosUrl, trueCredentials.dcosConfig),
                      credentials.account, clusterName, credentials.spectatorRegistry))
    })
  }

  DCOS getDcosClient(DcosClusterCredentials credentials) {
    def compositeKey = DcosClientCompositeKey.buildFrom(credentials.account, credentials.cluster).get()

    return dcosClients.computeIfAbsent(compositeKey.toString(), { k ->
      newProxyInstance(DCOS.class.getClassLoader(), [DCOS.class] as Class<?>[],
              new DcosSpectatorHandler(DCOSClient.getInstance(credentials.dcosUrl, credentials.dcosConfig),
                      credentials.account, credentials.cluster, credentials.spectatorRegistry))
    })
  }
}