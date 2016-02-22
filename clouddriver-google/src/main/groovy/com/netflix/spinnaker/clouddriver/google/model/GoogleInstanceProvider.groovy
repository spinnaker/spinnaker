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

package com.netflix.spinnaker.clouddriver.google.model

import com.netflix.frigga.Names
import com.netflix.spinnaker.clouddriver.google.security.GoogleCredentials
import com.netflix.spinnaker.clouddriver.model.InstanceProvider
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Conditional
import org.springframework.stereotype.Component

@Deprecated
@ConditionalOnProperty(value = "google.providerImpl", havingValue = "old", matchIfMissing = true)
@Component
class GoogleInstanceProvider implements InstanceProvider<GoogleInstance> {

  @Autowired
  AccountCredentialsProvider accountCredentialsProvider

  @Autowired
  GoogleResourceRetriever googleResourceRetriever

  String platform = "gce"

  @Override
  GoogleInstance getInstance(String account, String region, String id) {
    // TODO(duftler): Create a unit test.
    def standaloneInstance = googleResourceRetriever.standaloneInstanceMap[account]?.find { instance ->
      instance.name == id
    }

    if (standaloneInstance) {
      return standaloneInstance
    }

    String serverGroupName = getInstanceGroupBaseName(id)
    Names nameParts = Names.parseName(serverGroupName)
    GoogleApplication googleApplication = (googleResourceRetriever.getApplicationsMap())[nameParts.app]

    if (googleApplication) {
      Map<String, Map<String, GoogleCluster>> accountNameToClustersMap = googleApplication.clusters
      Map<String, GoogleCluster> clusterMap = accountNameToClustersMap[account]

      if (clusterMap) {
        GoogleCluster cluster = clusterMap[nameParts.cluster]

        if (cluster) {
          GoogleServerGroup serverGroup = cluster.serverGroups.find { it.name == nameParts.group && it.region == region }

          if (serverGroup) {
            return (GoogleInstance) serverGroup.instances.find { it.name == id }
          }
        }
      }
    }

    return null
  }

  @Override
  String getConsoleOutput(String account, String region, String id) {
    def accountCredentials = accountCredentialsProvider.getCredentials(account)

    if (!(accountCredentials?.credentials instanceof GoogleCredentials)) {
      throw new IllegalArgumentException("Invalid credentials: ${account}:${region}")
    }

    def credentials = accountCredentials.credentials
    def project = credentials.project
    def compute = credentials.compute
    def googleInstance = getInstance(account, region, id)

    if (googleInstance) {
      return compute.instances().getSerialPortOutput(project, googleInstance.zone, id).execute().contents
    }

    return null
  }

  // Strip off the final segment of the instance id (the unique portion that is added onto the instance group name).
  private static String getInstanceGroupBaseName(String instanceId) {
    int lastIndex = instanceId.lastIndexOf('-')

    return lastIndex != -1 ? instanceId.substring(0, lastIndex) : instanceId
  }

}
