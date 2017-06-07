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
    LoadBalancerConfig loadBalancer
    List<LinkedDockerRegistryConfiguration> dockerRegistries
    boolean insecureSkipTlsVerify
    String secretStore
  }

  static class ClusterCredential {
    String name
    String uid
    String password
    String serviceKey
  }

  static class Account {
    String name
    String environment
    String accountType
    List<ClusterCredential> clusters
    List<LinkedDockerRegistryConfiguration> dockerRegistries
    List<String> requiredGroupMembership
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
      .withCaCertFile(cluster.caCertFile).build()
  }

  private static DCOSAuthCredentials buildDCOSAuthCredentials(Account account, ClusterCredential clusterConfig) {
    DCOSAuthCredentials dcosAuthCredentials = null

    if (!clusterConfig.uid) {
      return dcosAuthCredentials
    }

    if (clusterConfig.password && clusterConfig.serviceKey) {
      throw new IllegalStateException("Both a password and serviceKey were supplied for the account with name [${account.name}] and region [${clusterConfig.name}]. Only one should be configured.")
    } else if (clusterConfig.password) {
      dcosAuthCredentials = DCOSAuthCredentials.forUserAccount(clusterConfig.uid, clusterConfig.password)
    } else if (clusterConfig.serviceKey) {
      dcosAuthCredentials = DCOSAuthCredentials.forServiceAccount(clusterConfig.uid, clusterConfig.serviceKey)
    }

    return dcosAuthCredentials
  }
}
