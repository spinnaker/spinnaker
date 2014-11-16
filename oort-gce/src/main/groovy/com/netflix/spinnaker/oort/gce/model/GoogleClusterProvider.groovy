/*
 * Copyright 2014 Google, Inc.
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

package com.netflix.spinnaker.oort.gce.model

import com.codahale.metrics.Timer
import com.netflix.frigga.Names
import com.netflix.spinnaker.amos.AccountCredentialsProvider

import com.netflix.spinnaker.oort.model.*
import com.ryantenney.metrics.annotation.Metric
import groovy.transform.CompileStatic
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

import javax.annotation.PostConstruct

@Component
@CompileStatic
class GoogleClusterProvider implements ClusterProvider<GoogleCluster> {
  @Autowired
  AccountCredentialsProvider accountCredentialsProvider

  @Metric
  Timer allClusters

  @Metric
  Timer clustersByApplication

  @Metric
  Timer clustersByApplicationAndAccount

  @Metric
  Timer clustersById

  GoogleResourceRetriever googleResourceRetriever

  @PostConstruct
  void init() {
    googleResourceRetriever = new GoogleResourceRetriever()
    googleResourceRetriever.init(accountCredentialsProvider)
  }

  @Override
  Map<String, Set<GoogleCluster>> getClusters() {
    allClusters.time {
      def clusterMap = new HashMap<String, Set<GoogleCluster>>()

      for (def mapEntry : googleResourceRetriever.getApplicationsMap().entrySet()) {
        populateClusterMapFromApplication(mapEntry.value, clusterMap)
      }

      clusterMap
    }
  }

  @Override
  Map<String, Set<GoogleCluster>> getClusterSummaries(String application) {
    getClusterDetails(application) // TODO: Provide a higher level view (load balancer, security group names only)
  }

  @Override
  Map<String, Set<GoogleCluster>> getClusterDetails(String application) {
    clustersByApplication.time {
      def googleApplication = (googleResourceRetriever.getApplicationsMap())[application]
      def clusterMap = new HashMap<String, Set<GoogleCluster>>()

      populateClusterMapFromApplication(googleApplication, clusterMap)

      clusterMap
    }
  }

  @Override
  Set<GoogleCluster> getClusters(String application, String accountName) {
    clustersByApplicationAndAccount.time {
      def clusters = getClusterDetails(application)

      clusters[accountName]
    }
  }

  @Override
  GoogleCluster getCluster(String application, String account, String name) {
    clustersById.time {
      def clusters = getClusterDetails(application)
      def cluster

      if (clusters && clusters[account]) {
        cluster = clusters[account].find { it.name == name }
      }

      cluster
    }
  }

  @Override
  GoogleServerGroup getServerGroup(String account, String region, String name) {
    GoogleServerGroup serverGroup = null
    Names nameParts = Names.parseName(name)
    GoogleCluster cluster = getCluster(nameParts.app, account, nameParts.cluster)
    if (cluster) {
      serverGroup = cluster.serverGroups.find { it.name == name && it.region == region }
    }
    serverGroup
  }

  private void populateClusterMapFromApplication(GoogleApplication googleApplication, HashMap<String, Set<GoogleCluster>> clusterMap) {
    if (googleApplication) {
      googleApplication.clusters.entrySet().each {
        if (!clusterMap[it.key]) {
          clusterMap[it.key] = new HashSet<GoogleCluster>()
        }

        clusterMap[it.key].addAll(it.value.values())
      }
    }
  }
}
