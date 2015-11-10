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

import com.netflix.frigga.autoscaling.AutoScalingGroupNameBuilder
import com.netflix.spinnaker.orca.CancellableStage
import com.netflix.spinnaker.orca.clouddriver.tasks.ShrinkClusterTask
import com.netflix.spinnaker.orca.pipeline.LinearStage
import com.netflix.spinnaker.orca.pipeline.model.PipelineStage
import com.netflix.spinnaker.orca.pipeline.model.Stage
import groovy.util.logging.Slf4j
import org.springframework.batch.core.Step
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

import java.util.concurrent.TimeUnit

@Slf4j
@Component
class CanaryStage extends LinearStage implements CancellableStage {
  public static final String PIPELINE_CONFIG_TYPE = "canary"

  @Autowired DeployCanaryStage deployCanaryStage
  @Autowired MonitorCanaryStage monitorCanaryStage
  @Autowired ShrinkClusterTask shrinkClusterTask

  CanaryStage() {
    super(PIPELINE_CONFIG_TYPE)
  }

  @Override
  List<Step> buildSteps(Stage stage) {
    Map canaryStageId = [canaryStageId: stage.id]

    Map deployContext = canaryStageId + stage.context
    Map monitorContext = canaryStageId + [scaleUp: stage.context.scaleUp ?: [:]]

    injectAfter(stage, "Deploy Canary", deployCanaryStage, deployContext)
    injectAfter(stage, "Monitor Canary", monitorCanaryStage, monitorContext)
    []
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

        shrinkContexts << [
          cluster          : builder.buildGroupName(),
          regions          : (cluster.availabilityZones as Map).keySet(),
          shrinkToSize     : 0,
          allowDeleteActive: true,
          credentials      : cluster.account
        ]
      }
    }

    def shrinkResults = shrinkContexts.collect {
      def shrinkStage = new PipelineStage()
      shrinkStage.context.putAll(it)
      shrinkClusterTask.execute(shrinkStage)
    }

    return new CancellableStage.Result(stage, [
      shrinkContexts: shrinkContexts,
      shrinkResults : shrinkResults
    ])
  }
}
