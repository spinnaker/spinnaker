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
import com.netflix.frigga.autoscaling.AutoScalingGroupNameBuilder
import com.netflix.spinnaker.orca.CancellableStage
import com.netflix.spinnaker.orca.clouddriver.tasks.cluster.ShrinkClusterTask
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
  @Autowired ShrinkClusterTask shrinkClusterTask

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
  CancellableStage.Result cancel(Stage stage) {
    log.info("Cancelling stage (stageId: ${stage.id}, executionId: ${stage.execution.id}, context: ${stage.context as Map})")

    // it's possible the server groups haven't been created yet, allow a grace period before cleanup
    Thread.sleep(TimeUnit.MINUTES.toMillis(2))

    Collection<Map<String, Object>> shrinkContexts = []
    stage.context.clusterPairs.each { Map<String, Map> clusterPair ->
      [clusterPair.baseline, clusterPair.canary].each { Map<String, String> cluster ->

        def builder = new AutoScalingGroupNameBuilder()
        builder.appName = cluster.application
        builder.stack = cluster.stack
        builder.detail = cluster.freeFormDetails

        String region = cluster.region ?: (cluster.availabilityZones as Map).keySet().first()

        shrinkContexts << [
          cluster          : builder.buildGroupName(),
          region           : region,
          shrinkToSize     : 0,
          allowDeleteActive: true,
          credentials      : cluster.account,
          cloudProvider    : cluster.cloudProvider ?: 'aws'
        ]
      }
    }

    def shrinkResults = shrinkContexts.collect {
      def shrinkStage = new Stage<>()
      shrinkStage.context.putAll(it)
      shrinkClusterTask.execute(shrinkStage)
    }

    return new CancellableStage.Result(stage, [
      shrinkContexts: shrinkContexts,
      shrinkResults : shrinkResults
    ])
  }
}
