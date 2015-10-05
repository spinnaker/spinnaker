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
import com.netflix.spinnaker.orca.clouddriver.pipeline.CloneLastServerGroupStage
import com.netflix.spinnaker.orca.clouddriver.utils.OortHelper
import com.netflix.spinnaker.orca.kato.pipeline.CopyLastAsgStage
import com.netflix.spinnaker.orca.kato.pipeline.DeployStage
import com.netflix.spinnaker.orca.kato.pipeline.gce.DeployGoogleServerGroupStage
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
    Set<String> regions
  }


  @Override
  TaskResult execute(Stage stage) {
    def config = stage.mapTo(ClusterSelection)
    def names = Names.parseName(config.cluster)

    Optional<Map> cluster = oortHelper.getCluster(names.app, config.credentials, config.cluster, config.cloudProvider)
    if (!cluster.isPresent()) {
      return DefaultTaskResult.SUCCEEDED
    }

    List<Map> serverGroups = cluster.get().serverGroups

    Map<String, List<Map>> serverGroupsByRegion = serverGroups.groupBy { it.region }

    List<Map> filteredServerGroups = config.regions.findResults {
      def regionGroups = serverGroupsByRegion[it]
      if (!regionGroups) {
        return null
      }
      filterServerGroups(stage, config.credentials, it, regionGroups) ?: null
    }.flatten()

    List<Map<String, Map>> katoOps = filteredServerGroups.collect(this.&buildOperationPayloads.curry(config)).flatten()

    if (!katoOps) {
      return DefaultTaskResult.SUCCEEDED
    }

    def regionGroups = filteredServerGroups
      .groupBy { it.region }
      .collectEntries { String region, List<Map> serverGroup ->
        [(region): serverGroup.collect { it.name }]
      }

    def taskId = katoService.requestOperations(config.cloudProvider, katoOps).toBlocking().first()
    new DefaultTaskResult(ExecutionStatus.SUCCEEDED, [
      "notification.type"   : getNotificationType(),
      "deploy.account.name" : config.credentials,
      "kato.last.task.id"   : taskId,
      "deploy.server.groups": regionGroups
    ])
  }

  protected Map buildOperationPayload(ClusterSelection config, Map serverGroup) {
    [(getClouddriverOperation()): [
      credentials: config.credentials,
      accountName: config.credentials,

      //TODO(cfieber) - better way to do this would be nice:
      regions: [serverGroup.region],
      region: serverGroup.region,
      zone: serverGroup.zone ?: serverGroup.zones?.getAt(0) ?: null,
      zones: serverGroup.zones ?: null,

      //TODO(cfieber) - dedupe
      serverGroupName: serverGroup.name,
      asgName: serverGroup.name,

      //TODO(cfieber) - dedupe
      cloudProvider: config.cloudProvider,
      providerType: config.cloudProvider
    ]]
  }

  protected List<Map> buildOperationPayloads(ClusterSelection config, Map serverGroup) {
    [buildOperationPayload(config, serverGroup)]
  }

  List<Map> filterParentDeploys(Stage stage, String account, String region, List<Map> serverGroups) {
    //if we are a synthetic stage child of a deploy, don't operate on what we just deployed
    final Set<String> deployStageTypes = [
      DeployStage.PIPELINE_CONFIG_TYPE,
      CopyLastAsgStage.PIPELINE_CONFIG_TYPE,
      CloneLastServerGroupStage.PIPELINE_CONFIG_TYPE,
      DeployGoogleServerGroupStage.PIPELINE_CONFIG_TYPE
    ]
    List<String> parentDeploys = []
    if (stage.parentStageId) {
      parentDeploys = stage.execution.stages.findResult {
        if (it.id == stage.parentStageId &&
          (deployStageTypes.contains(it.type) || deployStageTypes.contains(it.context.type)) &&
          it.context.'deploy.account.name' == account
        ) {
          return it.context.'deploy.server.groups'?.getAt(region) ?: null
        }
        return null
      } ?: []
    }
    return serverGroups.findAll { !(it.region == region && parentDeploys.contains(it.name)) }
  }

  List<Map> filterServerGroups(Stage stage, String account, String region, List<Map> serverGroups) {
    if (!serverGroups) {
      return []
    }

    if (!serverGroups.every { it.region == region }) {
      throw new IllegalStateException("all server groups must be in the same region, found ${serverGroups*.region}")
    }

    return filterParentDeploys(stage, account, region, serverGroups)
  }

  static boolean isActive(Map serverGroup) {
    return serverGroup.disabled == false || serverGroup.instances.any { it.healthState == 'Up' }
  }

  static class IsActive implements Comparator<Map> {
    @Override
    int compare(Map o1, Map o2) {
      isActive(o2) <=> isActive(o1)
    }
  }

  static class InstanceCount implements Comparator<Map> {
    @Override
    int compare(Map o1, Map o2) {
      o2.instances.size() <=> o1.instances.size()
    }
  }

  static class CreatedTime implements Comparator<Map> {
    @Override
    int compare(Map o1, Map o2) {
      o2.createdTime <=> o1.createdTime
    }
  }

  static class CompositeComparitor implements Comparator<Map> {
    private final List<Comparator<Map>> comparators

    CompositeComparitor(List<Comparator<Map>> comparators) {
      this.comparators = comparators
    }

    @Override
    int compare(Map o1, Map o2) {
      comparators.findResult { it.compare(o1, o2) ?: null } ?: 0
    }
  }
}
