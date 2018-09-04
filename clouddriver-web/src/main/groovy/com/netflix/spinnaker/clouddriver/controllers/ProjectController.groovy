/*
 * Copyright 2015 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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
 */

package com.netflix.spinnaker.clouddriver.controllers

import com.netflix.frigga.Names
import com.netflix.spinnaker.clouddriver.core.services.Front50Service
import com.netflix.spinnaker.clouddriver.model.Cluster
import com.netflix.spinnaker.clouddriver.model.ClusterProvider
import com.netflix.spinnaker.clouddriver.model.ServerGroup
import com.netflix.spinnaker.clouddriver.requestqueue.RequestQueue
import com.netflix.spinnaker.kork.web.exceptions.NotFoundException
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestMethod
import org.springframework.web.bind.annotation.RestController

import com.netflix.spinnaker.clouddriver.model.ServerGroup.InstanceCounts as InstanceCounts

@Slf4j
@RestController
@RequestMapping("/projects/{project}")
class ProjectController {

  @Autowired
  Front50Service front50Service

  @Autowired
  List<ClusterProvider> clusterProviders

  @Autowired
  RequestQueue requestQueue

  @RequestMapping(method= RequestMethod.GET, value = "/clusters")
  List<ClusterModel> getClusters(@PathVariable String project) {
    Map projectConfig = null
    try {
      projectConfig = front50Service.getProject(project)
    } catch (e) {
      log.error("Unable to fetch project (${project})", e)
      throw new NotFoundException("Project not found (name: ${project})")
    }

    if (projectConfig.config.clusters.size() == 0) {
      return []
    }

    List<String> applicationsToRetrieve = projectConfig.config.applications ?: []
    Map<String, Set<Cluster>> allClusters = retrieveClusters(project, applicationsToRetrieve)

    projectConfig.config.clusters.findResults { Map projectCluster ->
      List<String> applications = projectCluster.applications ?: projectConfig.config.applications
      def applicationModels = applications.findResults { String application ->
        def appClusters = allClusters[application]
        Set<Cluster> clusterMatches = findClustersForProject(appClusters, projectCluster)
        new ApplicationClusterModel(application, clusterMatches)
      }
      new ClusterModel(
          account: projectCluster.account,
          stack: projectCluster.stack,
          detail: projectCluster.detail,
          applications: applicationModels
      )
    }
  }

  private static HashSet<Cluster> findClustersForProject(Set<Cluster> appClusters, Map projectCluster) {
    if (!appClusters) {
      return []
    }
    appClusters.findAll { appCluster ->
      Names clusterNameParts = Names.parseName(appCluster.name)
      appCluster.accountName == projectCluster.account &&
          nameMatches("stack", clusterNameParts, projectCluster) &&
          nameMatches("detail", clusterNameParts, projectCluster)
    }
  }

  private Map<String, Set<Cluster>> retrieveClusters(String project, List<String> applications) {
    Map<String, Set<Cluster>> allClusters = [:]

    for (String application: applications) {
      for (RetrievedClusters clusters : retrieveApplication(project, application)) {
        allClusters
          .computeIfAbsent(clusters.application, { new HashSet<Cluster>() })
          .addAll(clusters.clusters)
      }
    }

    return allClusters
  }

  private List<RetrievedClusters> retrieveApplication(String project, String application) {
    return clusterProviders.findResults { ClusterProvider provider ->
      Map<String, Set<Cluster>> details = requestQueue.execute(project, { provider.getClusterDetails(application) }) ?: [:]
      details ?
        details.findResults {
          it.value ? new RetrievedClusters(application: application, clusters: it.value) : null
        } :
        null
    }.flatten()
  }

  static boolean nameMatches(String field, Names clusterName, Map projectCluster) {
    return projectCluster[field] == clusterName[field] || projectCluster[field] == "*" ||
        (!projectCluster[field] && !clusterName[field])
  }


  // Internal model - used to return all clusters for a given application
  static class RetrievedClusters {
    String application
    Set<Cluster> clusters
  }


  // Represents all the data needed to render a specific project cluster view
  static class ClusterModel {
    String account
    String stack
    String detail
    List<ApplicationClusterModel> applications = []
    InstanceCounts getInstanceCounts() {
      List<InstanceCounts> clusterCounts = applications.clusters.flatten().instanceCounts
      new InstanceCounts(
          total: (Integer) clusterCounts.total.sum(),
          down: (Integer) clusterCounts.down.sum(),
          outOfService: (Integer) clusterCounts.outOfService.sum(),
          up: (Integer) clusterCounts.up.sum(),
          unknown: (Integer) clusterCounts.unknown.sum(),
          starting: (Integer) clusterCounts.starting.sum()
      )
    }
  }

  // Represents the cluster data for a particular application
  static class ApplicationClusterModel {
    String application
    Set<RegionClusterModel> clusters = []
    Long getLastPush() {
      clusters.lastPush.max()
    }

    ApplicationClusterModel(String applicationName, Set<Cluster> appClusters) {
      application = applicationName
      Map<String, RegionClusterModel> regionClusters = [:]
      appClusters.serverGroups.flatten().findAll {
        !it.isDisabled() && it.instanceCounts.total > 0
      }.each { serverGroup ->
        if (!regionClusters.containsKey(serverGroup.region)) {
          regionClusters.put(serverGroup.region, new RegionClusterModel(region: serverGroup.region))
        }
        RegionClusterModel regionCluster = regionClusters.get(serverGroup.region)
        incrementInstanceCounts(serverGroup, regionCluster.instanceCounts)
        def buildNumber = serverGroup.imageSummary?.buildInfo?.jenkins?.number ?: "0"
        def host = serverGroup.imageSummary?.buildInfo?.jenkins?.host
        def job = serverGroup.imageSummary?.buildInfo?.jenkins?.name
        def existingBuild = regionCluster.builds.find {
          it.buildNumber == buildNumber && it.host == host && it.job == job
        }
        if (!existingBuild) {
          regionCluster.builds << new DeployedBuild(
              host: host,
              job: job,
              buildNumber: buildNumber,
              deployed: serverGroup.createdTime,
              images: serverGroup.imageSummary?.buildInfo?.images)
        } else {
          existingBuild.deployed = Math.max(existingBuild.deployed, serverGroup.createdTime)
        }
      }
      clusters = regionClusters.values()
    }
  }

  // Represents the cluster data for a particular application in a particular region
  static class RegionClusterModel {
    String region
    List<DeployedBuild> builds = []
    InstanceCounts instanceCounts = new InstanceCounts(total: 0, up: 0, down: 0, starting: 0, outOfService: 0, unknown: 0)
    Long getLastPush() {
      builds.deployed.max()
    }
  }

  static class DeployedBuild {
    String host
    String job
    String buildNumber
    Long deployed
    List images
  }


  private static void incrementInstanceCounts(ServerGroup source, InstanceCounts target) {
    InstanceCounts sourceCounts = source.instanceCounts
    target.total += sourceCounts.total
    target.down += sourceCounts.down
    target.up += sourceCounts.up
    target.outOfService += sourceCounts.outOfService
    target.starting += sourceCounts.starting
    target.unknown += sourceCounts.unknown
  }
}
