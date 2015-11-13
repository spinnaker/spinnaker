/*
 * Copyright 2015 The original authors.
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

package com.netflix.spinnaker.oort.cf.model

import com.netflix.spectator.api.Registry
import com.netflix.spectator.api.Timer
import com.netflix.spinnaker.oort.model.ClusterProvider
import com.netflix.spinnaker.oort.model.ServerGroup
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

import javax.annotation.PostConstruct
import java.util.concurrent.Callable

@Component
class CloudFoundryClusterProvider implements ClusterProvider<CloudFoundryCluster> {

  @Autowired
  Registry registry

  @Autowired
  CloudFoundryResourceRetriever cloudFoundryResourceRetriever

  Timer clusters

  Timer clusterSummaries

  Timer clustersByApplicationAccount

  Timer serverGroup

  @PostConstruct
  void init() {
    String[] tags = ['className', this.class.simpleName]
    clusters = registry.timer('clusters', tags)
    clusterSummaries = registry.timer('clusterSummaries', tags)
    clustersByApplicationAccount = registry.timer('clustersByApplicationAccount', tags)
    serverGroup = registry.timer('serverGroup', tags)
  }

  @Override
  Map<String, Set<CloudFoundryCluster>> getClusters() {
    clusters.record({
      Collections.unmodifiableMap(
        cloudFoundryResourceRetriever.clustersByAccount
      )
    } as Callable<Map<String, Set<CloudFoundryCluster>>>)
  }

  @Override
  Map<String, Set<CloudFoundryCluster>> getClusterSummaries(String application) {
    clusterSummaries.record({
      Collections.unmodifiableMap(
        cloudFoundryResourceRetriever.clustersByApplicationAndAccount[application]
      )
    } as Callable<Map<String, Set<CloudFoundryCluster>>>)
  }

  @Override
  Map<String, Set<CloudFoundryCluster>> getClusterDetails(String application) {
    this.getClusterSummaries(application)
  }

  @Override
  Set<CloudFoundryCluster> getClusters(String application, String account) {
    clustersByApplicationAccount.record({
      Collections.unmodifiableSet(
          cloudFoundryResourceRetriever.clustersByApplicationAndAccount[application][account]
      )
    } as Callable<Set<CloudFoundryCluster>>)
  }

  @Override
  CloudFoundryCluster getCluster(String application, String account, String name) {
    this.getClusters(application, account).find {it.name == name}
  }

  @Override
  ServerGroup getServerGroup(String account, String region, String name) {
    serverGroup.record({
      cloudFoundryResourceRetriever.serverGroupByAccountAndServerGroupName[account][name]
    } as Callable<ServerGroup>)
  }
}
