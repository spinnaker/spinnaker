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
import com.netflix.spectator.api.Registry
import com.netflix.spectator.api.Timer
import com.netflix.spinnaker.clouddriver.model.ClusterProvider
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider
import groovy.transform.CompileStatic
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component

import javax.annotation.PostConstruct
import java.util.concurrent.Callable

@Deprecated
@ConditionalOnProperty(value = "google.providerImpl", havingValue = "old", matchIfMissing = true)
@Component
@CompileStatic
class GoogleClusterProvider implements ClusterProvider<GoogleCluster> {
  @Autowired
  AccountCredentialsProvider accountCredentialsProvider

  @Autowired
  GoogleResourceRetriever googleResourceRetriever

  @Autowired
  Registry registry

  Timer allClusters

  Timer clustersByApplication

  Timer clustersByApplicationAndAccount

  Timer clustersById

  @PostConstruct
  void init() {
    String[] tags = ['className', this.class.simpleName]
    allClusters = registry.timer('allClusters', tags)
    clustersByApplication = registry.timer('clustersByApplications', tags)
    clustersByApplicationAndAccount = registry.timer('clustersByApplicationAndAccount', tags)
    clustersById = registry.timer('clustersById', tags)
  }

  @Override
  Map<String, Set<GoogleCluster>> getClusters() {
    allClusters.record({
      def clusterMap = new HashMap<String, Set<GoogleCluster>>()

      for (def mapEntry : googleResourceRetriever.getApplicationsMap().entrySet()) {
        populateClusterMapFromApplication(mapEntry.value, clusterMap)
      }

      clusterMap
    } as Callable<Map<String, Set<GoogleCluster>>>)
  }

  @Override
  Map<String, Set<GoogleCluster>> getClusterSummaries(String application) {
    getClusterDetails(application) // TODO: Provide a higher level view (load balancer, security group names only)
  }

  @Override
  Map<String, Set<GoogleCluster>> getClusterDetails(String application) {
    clustersByApplication.record({
      def googleApplication = (googleResourceRetriever.getApplicationsMap())[application]
      def clusterMap = new HashMap<String, Set<GoogleCluster>>()

      populateClusterMapFromApplication(googleApplication, clusterMap)

      clusterMap
    } as Callable<Map<String, Set<GoogleCluster>>>)
  }

  @Override
  Set<GoogleCluster> getClusters(String application, String accountName) {
    clustersByApplicationAndAccount.record({
      def clusters = getClusterDetails(application)

      clusters[accountName]
    } as Callable<Set<GoogleCluster>>)
  }

  @Override
  GoogleCluster getCluster(String application, String account, String name) {
    clustersById.record({
      def clusters = getClusterDetails(application)
      def cluster = null

      if (clusters && clusters[account]) {
        cluster = clusters[account].find { it.name == name }
      }

      cluster
    } as Callable<GoogleCluster>)
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
