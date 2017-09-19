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

package com.netflix.spinnaker.orca.mine.pipeline

import com.netflix.spinnaker.orca.CancellableStage.Result
import com.netflix.spinnaker.orca.clouddriver.tasks.servergroup.DestroyServerGroupTask
import com.netflix.spinnaker.orca.clouddriver.utils.OortHelper

import java.util.concurrent.TimeUnit
import com.netflix.frigga.autoscaling.AutoScalingGroupNameBuilder
import com.netflix.spinnaker.orca.CancellableStage
import com.netflix.spinnaker.orca.pipeline.StageDefinitionBuilder
import com.netflix.spinnaker.orca.pipeline.model.Execution
import com.netflix.spinnaker.orca.pipeline.model.Stage
import com.netflix.spinnaker.orca.pipeline.model.SyntheticStageOwner
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Slf4j
@Component
class CanaryStage implements StageDefinitionBuilder, CancellableStage {
  public static final String PIPELINE_CONFIG_TYPE = "canary"

  @Autowired DeployCanaryStage deployCanaryStage
  @Autowired MonitorCanaryStage monitorCanaryStage
  @Autowired DestroyServerGroupTask destroyServerGroupTask
  @Autowired OortHelper oortHelper

  @Override
  def <T extends Execution<T>> List<Stage<T>> aroundStages(Stage<T> stage) {
    Map canaryStageId = [
      canaryStageId: stage.id,
      failPipeline: stage.context.failPipeline,
      continuePipeline: stage.context.continuePipeline
    ]

    Map<String, Object> deployContext = canaryStageId + stage.context
    Map<String, Object> monitorContext = canaryStageId + [scaleUp: stage.context.scaleUp ?: [:]]

    return [
      newStage(stage.execution, deployCanaryStage.type, "Deploy Canary", deployContext, stage, SyntheticStageOwner.STAGE_AFTER),
      newStage(stage.execution, monitorCanaryStage.type, "Monitor Canary", monitorContext, stage, SyntheticStageOwner.STAGE_AFTER)
    ]
  }

  @Override
  Result cancel(Stage stage) {
    log.info("Cancelling stage (stageId: ${stage.id}, executionId: ${stage.execution.id}, context: ${stage.context as Map})")

    // it's possible the server groups haven't been created yet, allow a grace period before cleanup
    Thread.sleep(TimeUnit.MINUTES.toMillis(2))

    return cleanupCanary(stage)
  }

  protected Result cleanupCanary(Stage stage) {
    Collection<Map<String, Object>> destroyContexts = []

    stage.context.clusterPairs.each { Map<String, Map> clusterPair ->
      [clusterPair.baseline, clusterPair.canary].each { Map<String, String> cluster ->

        def builder = new AutoScalingGroupNameBuilder()
        builder.appName = cluster.application
        builder.stack = cluster.stack
        builder.detail = cluster.freeFormDetails

        Map deployedCluster = oortHelper.getCluster(cluster.application, cluster.account, builder.buildGroupName(), cluster.cloudProvider ?: 'aws').orElse([:])
        Long start = stage.startTime
        // add a small buffer to deal with latency between the cloud provider and Orca
        Long createdTimeCutoff = stage.endTime + 5000

        List<Map> serverGroups = deployedCluster.serverGroups ?: []

        String clusterRegion = cluster.region ?: (cluster.availabilityZones as Map).keySet().first()
        List<Map> matches = serverGroups.findAll {
          it.region == clusterRegion && it.createdTime > start && it.createdTime < createdTimeCutoff
        } ?: []

        // really hope they're not running concurrent canaries in the same cluster
        matches.each {
          destroyContexts << [
            serverGroupName: it.name,
            region         : it.region,
            credentials    : cluster.account,
            cloudProvider  : it.cloudProvider ?: 'aws'
          ]
        }
      }
    }

    def destroyResults = destroyContexts.collect {
      def destroyStage = new Stage<>()
      destroyStage.execution = stage.execution
      destroyStage.context.putAll(it)
      destroyServerGroupTask.execute(destroyStage)
    }

    return new Result(stage, [
      destroyContexts: destroyContexts,
      destroyResults : destroyResults
    ])
  }
}
