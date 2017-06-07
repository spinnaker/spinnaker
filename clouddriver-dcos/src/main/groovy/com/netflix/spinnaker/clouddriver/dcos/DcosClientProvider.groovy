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

class DcosClientProvider {

  private final Map<String, DCOS> dcosClients = new ConcurrentHashMap<>()
  private final AccountCredentialsProvider credentialsProvider

  DcosClientProvider(AccountCredentialsProvider credentialsProvider) {
    this.credentialsProvider = credentialsProvider
  }

  DCOS getDcosClient(DcosAccountCredentials credentials, String clusterName) {
    def compositeKey = DcosClientCompositeKey.buildFromVerbose(credentials.account, clusterName).get()
    def trueCredentials = credentials.getCredentialsByCluster(clusterName)

    return dcosClients.computeIfAbsent(compositeKey.toString(), { k -> DCOSClient.getInstance(trueCredentials.dcosUrl, trueCredentials.dcosConfig) })
  }

  DCOS getDcosClient(DcosClusterCredentials credentials) {
    def compositeKey = DcosClientCompositeKey.buildFrom(credentials.account, credentials.cluster).get()

    return dcosClients.computeIfAbsent(compositeKey.toString(), { k -> DCOSClient.getInstance(credentials.dcosUrl, credentials.dcosConfig) })
  }

  DCOS getDcosClient(String accountName, String clusterName) {
    def compositeKey = DcosClientCompositeKey.buildFrom(accountName, clusterName).get()

    return dcosClients.computeIfAbsent(compositeKey.toString(), { k ->
      def credentials = credentialsProvider.getCredentials(accountName)

      if (!(credentials instanceof DcosAccountCredentials)) {
        throw new IllegalArgumentException("Account [${accountName}] is not a valid DC/OS account!")
      }

      def trueCredentials = credentials.getCredentials().getCredentialsByCluster(clusterName)

      if (!trueCredentials) {
        throw new IllegalArgumentException("Cluster [${clusterName}] is not a valid DC/OS cluster for the DC/OS account [${accountName}]!")
      }

      DCOSClient.getInstance(trueCredentials.dcosUrl, trueCredentials.dcosConfig)
    })
  }
}
