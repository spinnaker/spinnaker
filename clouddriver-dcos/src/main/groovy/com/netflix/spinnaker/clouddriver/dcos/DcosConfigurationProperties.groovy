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

import com.netflix.spinnaker.fiat.model.resources.Permissions
import groovy.transform.ToString
import mesosphere.dcos.client.Config
import mesosphere.dcos.client.model.DCOSAuthCredentials

class DcosConfigurationProperties {
  public static final int ASYNC_OPERATION_TIMEOUT_SECONDS_DEFAULT = 300
  public static final int ASYNC_OPERATION_MAX_POLLING_INTERVAL_SECONDS = 8

  List<Cluster> clusters = []
  List<Account> accounts = []

  int asyncOperationTimeoutSecondsDefault = ASYNC_OPERATION_TIMEOUT_SECONDS_DEFAULT
  int asyncOperationMaxPollingIntervalSeconds = ASYNC_OPERATION_MAX_POLLING_INTERVAL_SECONDS

  static class Cluster {
    String name
    String dcosUrl
    String caCertData
    String caCertFile
    String marathonPath
    String metronomePath
    LoadBalancerConfig loadBalancer
    List<LinkedDockerRegistryConfiguration> dockerRegistries
    boolean insecureSkipTlsVerify
    String secretStore
  }

  static class ClusterCredential {
    String name
    String uid
    String password
    String serviceKeyData
    String serviceKeyFile
  }

  static class Account {
    String name
    String environment
    String accountType
    List<ClusterCredential> clusters
    List<LinkedDockerRegistryConfiguration> dockerRegistries
    List<String> requiredGroupMembership
    Permissions.Builder permissions = new Permissions.Builder()
  }

  static class LoadBalancerConfig {
    String image
    String serviceAccountSecret
  }

  // In case we want to add any additional information here.
  @ToString(includeNames = true)
  static class LinkedDockerRegistryConfiguration {
    String accountName
  }

  public static Config buildConfig(
    final Account account, final Cluster cluster, final ClusterCredential clusterConfig) {
    Config.builder().withCredentials(buildDCOSAuthCredentials(account, clusterConfig))
      .withInsecureSkipTlsVerify(cluster.insecureSkipTlsVerify)
      .withCaCertData(cluster.caCertData)
      .withCaCertFile(cluster.caCertFile)
      .withMarathonPath(cluster.marathonPath)
      .withMetronomePath(cluster.metronomePath)
      .build()
  }

  private static String getServiceKeyData(Account account, ClusterCredential clusterConfig) {
    if (clusterConfig.serviceKeyData != null && clusterConfig.serviceKeyFile != null) {
      throw new IllegalStateException("Both a serviceKeyData and serviceKeyFile were supplied for the account with name [${account.name}] and region [${clusterConfig.name}]. Only one should be configured.")
    } else if (clusterConfig.serviceKeyData != null) {
        return clusterConfig.serviceKeyData
    } else if (clusterConfig.serviceKeyFile != null) {
      try {
        def actualFile = new File(clusterConfig.serviceKeyFile)

        return actualFile.withReader('UTF-8') {
          it.readLines().join('\n')
        }
      } catch (IOException e) {
        throw new RuntimeException(e)
      }
    }

    // We should never reach this part of the function given that we are only calling this if one of the arguments is provided.
    throw new IllegalStateException("Either a serviceKeyData and serviceKeyFile should be supplied for the account with name [${account.name}] and region [${clusterConfig.name}]. Neither was configured.")
  }

  private static DCOSAuthCredentials buildDCOSAuthCredentials(Account account, ClusterCredential clusterConfig) {
    DCOSAuthCredentials dcosAuthCredentials = null

    if (clusterConfig.uid && clusterConfig.password && (clusterConfig.serviceKeyData || clusterConfig.serviceKeyFile)) {
      throw new IllegalStateException("Both a password and serviceKey were supplied for the account with name [${account.name}] and region [${clusterConfig.name}]. Only one should be configured.")
    } else if (clusterConfig.uid && clusterConfig.password) {
      dcosAuthCredentials = DCOSAuthCredentials.forUserAccount(clusterConfig.uid, clusterConfig.password)
    } else if (clusterConfig.uid && (clusterConfig.serviceKeyData || clusterConfig.serviceKeyFile)) {
      clusterConfig.serviceKeyData = getServiceKeyData(account, clusterConfig)
      dcosAuthCredentials = DCOSAuthCredentials.forServiceAccount(clusterConfig.uid, clusterConfig.serviceKeyData)
    }

    dcosAuthCredentials
  }
}
