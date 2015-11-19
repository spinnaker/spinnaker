/*
 * Copyright 2015 Netflix, Inc.
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

package com.netflix.spinnaker.orca.clouddriver.tasks

import com.netflix.frigga.Names
import com.netflix.spinnaker.orca.DefaultTaskResult
import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.RetryableTask
import com.netflix.spinnaker.orca.TaskResult
import com.netflix.spinnaker.orca.clouddriver.KatoService
import com.netflix.spinnaker.orca.clouddriver.pipeline.CloneServerGroupStage
import com.netflix.spinnaker.orca.clouddriver.pipeline.CreateServerGroupStage
import com.netflix.spinnaker.orca.clouddriver.pipeline.support.Location
import com.netflix.spinnaker.orca.clouddriver.pipeline.support.TargetServerGroup
import com.netflix.spinnaker.orca.clouddriver.utils.OortHelper
import com.netflix.spinnaker.orca.kato.pipeline.CopyLastAsgStage
import com.netflix.spinnaker.orca.kato.pipeline.DeployStage
import com.netflix.spinnaker.orca.pipeline.model.Stage
import groovy.transform.Canonical
import org.springframework.beans.factory.annotation.Autowired

/**
 * A task that operates on some subset of the ServerGroups in a cluster.
 */
abstract class AbstractClusterWideClouddriverTask extends AbstractCloudProviderAwareTask implements RetryableTask {

  @Override
  public long getBackoffPeriod() {
    5000
  }

  @Override
  public long getTimeout() {
    30000
  }

  abstract String getClouddriverOperation()

  String getNotificationType() {
    return getClouddriverOperation().toLowerCase()
  }

  @Autowired OortHelper oortHelper
  @Autowired KatoService katoService

  @Canonical
  static class ClusterSelection {
    String cluster
    String cloudProvider = 'aws'
    String credentials

    @Override
    String toString() {
      "Cluster $cloudProvider/$credentials/$cluster"
    }
  }

  protected TaskResult missingClusterResult(Stage stage, ClusterSelection clusterSelection) {
    throw new IllegalStateException("no cluster details found for $clusterSelection")
  }

  protected TaskResult emptyClusterResult(Stage stage, ClusterSelection clusterSelection, Map cluster) {
    throw new IllegalStateException("No ServerGroups found in cluster $clusterSelection")
  }


  @Override
  TaskResult execute(Stage stage) {
    def clusterSelection = stage.mapTo(ClusterSelection)
    def names = Names.parseName(clusterSelection.cluster)

    Optional<Map> cluster = oortHelper.getCluster(names.app,
                                                  clusterSelection.credentials,
                                                  clusterSelection.cluster,
                                                  clusterSelection.cloudProvider)
    if (!cluster.isPresent()) {
      return missingClusterResult(stage, clusterSelection)
    }

    List<Map> serverGroups = cluster.get().serverGroups

    if (!serverGroups) {
      return emptyClusterResult(stage, clusterSelection, cluster.get())
    }

    Map<Location, List<TargetServerGroup>> targetServerGroupsByLocation = serverGroups.collect {
      new TargetServerGroup(serverGroup: it)
    }.groupBy { it.getLocation() }

    def locations = stage.context.regions ?: stage.context.zones ?: []
    List<TargetServerGroup> filteredServerGroups = locations.collect {
      TargetServerGroup.Support.locationFromCloudProviderValue(clusterSelection.cloudProvider, it)
    }.findResults { Location l ->
      def tsgs = targetServerGroupsByLocation[l]
      if (!tsgs) {
        return null
      }
      filterServerGroups(stage, clusterSelection.credentials, l, tsgs) ?: null
    }.flatten()

    List<Map<String, Map>> katoOps = filteredServerGroups.collect(this.&buildOperationPayloads.curry(clusterSelection)).flatten()

    if (!katoOps) {
      return DefaultTaskResult.SUCCEEDED
    }

    // "deploy.server.groups" is keyed by region, and all TSGs will have this value.
    def locationGroups = filteredServerGroups.groupBy {
      it.region
    }.collectEntries { String region, List<TargetServerGroup> serverGroup ->
      [(region): serverGroup.collect { it.name }]
    }

    def taskId = katoService.requestOperations(clusterSelection.cloudProvider, katoOps).toBlocking().first()
    new DefaultTaskResult(ExecutionStatus.SUCCEEDED, [
      "notification.type"   : getNotificationType(),
      "deploy.account.name" : clusterSelection.credentials,
      "kato.last.task.id"   : taskId,
      "deploy.server.groups": locationGroups
    ])
  }

  protected Map buildOperationPayload(ClusterSelection clusterSelection, TargetServerGroup serverGroup) {
    serverGroup.toClouddriverOperationPayload(clusterSelection.credentials)
  }

  protected List<Map> buildOperationPayloads(ClusterSelection clusterSelection, TargetServerGroup serverGroup) {
    [[(getClouddriverOperation()): buildOperationPayload(clusterSelection, serverGroup)]]
  }

  protected List<TargetServerGroup> filterActiveGroups(boolean includeActive, List<TargetServerGroup> serverGroups) {
    if (includeActive) {
      return serverGroups
    }
    return serverGroups.findAll { !isActive(it) }
  }

  protected List<TargetServerGroup> filterParentDeploys(Stage stage,
                                                        String account,
                                                        Location location,
                                                        List<TargetServerGroup> serverGroups) {
    //if we are a synthetic stage child of a deploy, don't operate on what we just deployed
    final Set<String> deployStageTypes = [
      DeployStage.PIPELINE_CONFIG_TYPE,
      CopyLastAsgStage.PIPELINE_CONFIG_TYPE,
      CloneServerGroupStage.PIPELINE_CONFIG_TYPE,
      CreateServerGroupStage.PIPELINE_CONFIG_TYPE,
    ]
    List<TargetServerGroup> deployedServerGroups = []
    if (stage.parentStageId) {
      Stage parentDeployStage = stage.execution.stages.find {
        it.id == stage.parentStageId &&
          //Stage type is the context.type value when the stage is running as a child stage of a parallel deploy, or
          // the stage.type attribute when it is running directly as part of an Orchestration or Pipeline
          (deployStageTypes.contains(it.type) || deployStageTypes.contains(it.context.type)) &&
          it.context.'deploy.account.name' == account
      }
      if (parentDeployStage) {
        Map<String, String> dsgs = (parentDeployStage.context.'deploy.server.groups' ?: [:]) as Map
        switch(location.type) {
          case Location.Type.ZONE:
            deployedServerGroups = serverGroups.findAll { it.zones.contains(location.value) && dsgs[it.region].contains(it.name)}
            break;
          case Location.Type.REGION:
            deployedServerGroups = serverGroups.findAll { it.region == location.value && dsgs[location.value].contains(it.name) }
            break;
        }
      }
    }

    return serverGroups - deployedServerGroups
  }

  List<TargetServerGroup> filterServerGroups(Stage stage,
                                             String account,
                                             Location location,
                                             List<TargetServerGroup> serverGroups) {
    if (!serverGroups) {
      return []
    }

    if (!serverGroups.every { it.getLocation() == location }) {
      throw new IllegalStateException("all server groups must be in the same location, found ${serverGroups*.getLocation()}")
    }

    return filterParentDeploys(stage, account, location, serverGroups)
  }

  static boolean isActive(TargetServerGroup serverGroup) {
    return serverGroup.disabled == false || serverGroup.instances.any { it.healthState == 'Up' }
  }

  static class IsActive implements Comparator<TargetServerGroup> {
    @Override
    int compare(TargetServerGroup o1, TargetServerGroup o2) {
      isActive(o2) <=> isActive(o1)
    }
  }

  static class InstanceCount implements Comparator<TargetServerGroup> {
    @Override
    int compare(TargetServerGroup o1, TargetServerGroup o2) {
      o2.instances.size() <=> o1.instances.size()
    }
  }

  static class CreatedTime implements Comparator<TargetServerGroup> {
    @Override
    int compare(TargetServerGroup o1, TargetServerGroup o2) {
      o2.createdTime <=> o1.createdTime
    }
  }

  static class CompositeComparator implements Comparator<TargetServerGroup> {
    private final List<Comparator<TargetServerGroup>> comparators

    CompositeComparator(List<Comparator<TargetServerGroup>> comparators) {
      this.comparators = comparators
    }

    @Override
    int compare(TargetServerGroup o1, TargetServerGroup o2) {
      comparators.findResult { it.compare(o1, o2) ?: null } ?: 0
    }
  }
}
