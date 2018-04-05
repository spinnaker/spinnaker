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

import java.util.concurrent.TimeUnit
import javax.annotation.Nonnull
import com.netflix.frigga.autoscaling.AutoScalingGroupNameBuilder
import com.netflix.spinnaker.kork.core.RetrySupport
import com.netflix.spinnaker.orca.CancellableStage
import com.netflix.spinnaker.orca.clouddriver.KatoService
import com.netflix.spinnaker.orca.clouddriver.tasks.servergroup.DestroyServerGroupTask
import com.netflix.spinnaker.orca.clouddriver.utils.OortHelper
import com.netflix.spinnaker.orca.pipeline.StageDefinitionBuilder
import com.netflix.spinnaker.orca.pipeline.graph.StageGraphBuilder
import com.netflix.spinnaker.orca.pipeline.model.Stage
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Slf4j
@Component
class CanaryStage implements StageDefinitionBuilder, CancellableStage {
  public static final String PIPELINE_CONFIG_TYPE = "canary"
  public static final Integer DEFAULT_CLUSTER_DISABLE_WAIT_TIME = 180
  @Autowired DeployCanaryStage deployCanaryStage
  @Autowired MonitorCanaryStage monitorCanaryStage
  @Autowired DestroyServerGroupTask destroyServerGroupTask
  @Autowired OortHelper oortHelper
  @Autowired KatoService katoService
  @Autowired RetrySupport retrySupport

  @Override
  void afterStages(@Nonnull Stage parent, @Nonnull StageGraphBuilder graph) {
    Map canaryStageId = [
      canaryStageId   : parent.id,
      failPipeline    : parent.context.failPipeline,
      continuePipeline: parent.context.continuePipeline
    ]

    Map<String, Object> deployContext = canaryStageId + parent.context
    Map<String, Object> monitorContext = canaryStageId + [scaleUp: parent.context.scaleUp ?: [:]]

    graph.append {
      it.type = deployCanaryStage.type
      it.name = "Deploy Canary"
      it.context = deployContext
    }
    graph.append {
      it.type = monitorCanaryStage.type
      it.name = "Monitor Canary"
      it.context = monitorContext
    }
  }

  @Override
  Result cancel(Stage stage) {
    Collection<Map<String, Object>> disableContexts = []
    Collection<Map<String, Object>> destroyContexts = []

    log.info("Cancelling stage (stageId: ${stage.id}, executionId: ${stage.execution.id}, context: ${stage.context as Map})")

    stage.context.clusterPairs.each { Map<String, Map> clusterPair ->
      [clusterPair.baseline, clusterPair.canary].each { Map<String, String> cluster ->

        def builder = new AutoScalingGroupNameBuilder()
        builder.appName = cluster.application
        builder.stack = cluster.stack
        builder.detail = cluster.freeFormDetails

        def cloudProvider = cluster.cloudProvider ?: 'aws'
        // it's possible the server groups haven't been created yet, retry with backoff before cleanup
        Map<String, Object> deployedCluster = [:]
        retrySupport.retry({
          deployedCluster = oortHelper.getCluster(cluster.application, cluster.account, builder.buildGroupName(), cloudProvider).orElse([:])
          if (deployedCluster.serverGroups == null || deployedCluster.serverGroups?.size() == 0) {
            throw new IllegalStateException("Expected serverGroup matching cluster {$cluster}")
          }
        }, 8, TimeUnit.SECONDS.toMillis(15), false)
        Long start = stage.startTime
        // add a small buffer to deal with latency between the cloud provider and Orca
        Long createdTimeCutoff = (stage.endTime ?: System.currentTimeMillis()) + 5000

        List<Map> serverGroups = deployedCluster.serverGroups ?: []

        String clusterRegion = cluster.region ?: (cluster.availabilityZones as Map).keySet().first()
        List<Map> matches = serverGroups.findAll {
          it.region == clusterRegion && it.createdTime > start && it.createdTime < createdTimeCutoff
        } ?: []

        // really hope they're not running concurrent canaries in the same cluster
        matches.each {
          disableContexts << [
            disableServerGroup: [
              serverGroupName             : it.name,
              region                      : it.region,
              credentials                 : cluster.account,
              cloudProvider               : cloudProvider,
              remainingEnabledServerGroups: 0,
              preferLargerOverNewer       : false
            ]
          ]
          destroyContexts << [
            serverGroupName: it.name,
            region         : it.region,
            credentials    : cluster.account,
            cloudProvider  : cloudProvider
          ]
        }
      }
    }

    if (disableContexts) {
      try {
        katoService.requestOperations(
          disableContexts.first().disableServerGroup.cloudProvider,
          disableContexts
        ).toBlocking().first()
        Thread.sleep(TimeUnit.SECONDS.toMillis(
          stage.context.clusterDisableWaitTime != null ? stage.context.clusterDisableWaitTime : DEFAULT_CLUSTER_DISABLE_WAIT_TIME)
        )
      } catch (Exception e) {
        log.error("Error disabling canary clusters in ${stage.id} with ${disableContexts}", e)
      }
    }

    def destroyResults = destroyContexts.collect {
      def destroyStage = new Stage()
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
