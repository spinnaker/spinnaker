/*
 * Copyright 2015 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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

package com.netflix.spinnaker.orca.kato.pipeline

import com.netflix.spinnaker.orca.DefaultTaskResult
import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.Task
import com.netflix.spinnaker.orca.TaskResult
import com.netflix.spinnaker.orca.pipeline.LinearStage
import com.netflix.spinnaker.orca.pipeline.ParallelStage
import com.netflix.spinnaker.orca.pipeline.model.AbstractStage
import com.netflix.spinnaker.orca.pipeline.model.PipelineStage
import com.netflix.spinnaker.orca.pipeline.model.Stage
import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.springframework.batch.core.job.builder.FlowBuilder
import org.springframework.batch.core.job.flow.Flow
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Slf4j
@Component
@CompileStatic
class ParallelDeployStage extends ParallelStage {

  public static final String MAYO_CONFIG_TYPE = "deploy"

  @Autowired
  List<LinearStage> stageBuilders

  ParallelDeployStage() {
    super(MAYO_CONFIG_TYPE)
  }

  @Override
  protected List<Flow> buildFlows(Stage stage) {
    return parallelContexts(stage).collect { Map context ->
      def nextStage = newStage(
        stage.execution, context.type as String, context.name as String, new HashMap(context), stage, Stage.SyntheticStageOwner.STAGE_AFTER
      )
      ((AbstractStage) nextStage).type = MAYO_CONFIG_TYPE
      stage.execution.stages.add(nextStage)

      def flowBuilder = new FlowBuilder<Flow>(context.name as String).start(
        buildStep(stage, "setupParallelDeploy", new Task() {
          @Override
          TaskResult execute(Stage ignored) {
            return new DefaultTaskResult(ExecutionStatus.SUCCEEDED)
          }
        })
      )
      def stageBuilder = stageBuilders.find { it.type == context.type }
      stageBuilder.build(flowBuilder, nextStage)
      return flowBuilder.end()
    }
  }

  @Override
  @CompileDynamic
  List<Map<String, Object>> parallelContexts(Stage stage) {
    def defaultStageContext = new HashMap(stage.context)

    List<Map<String, Object>> clusters = []
    if (stage.context.cluster) {
      clusters.add(stage.context.cluster as Map<String, Object>)
      defaultStageContext.remove("cluster")
    }
    if (stage.context.clusters) {
      clusters.addAll(stage.context.clusters as List<Map<String, Object>>)
      defaultStageContext.remove("clusters")
    }

    if (!stage.context.cluster && !stage.context.clusters) {
      // support invoking this stage as an orchestration without nested target cluster details
      clusters.add(stage.context)
      defaultStageContext.clear()
    }

    return clusters.collect {
      return defaultStageContext + [
        account            : it.account ?: stage.context.account,
        cluster            : it,
        type               : DeployStage.MAYO_CONFIG_TYPE,
        name               : "Deploy in ${(it.availabilityZones as Map).keySet()[0]}"
      ]
    }
  }

  @Override
  String parallelStageName(Stage stage, boolean hasParallelFlows) {
    return stage.name
  }

  @Override
  Task completeParallel() {
    return new Task() {
      TaskResult execute(Stage stage) {
        log.info("Completed Parallel Deploy")
        new DefaultTaskResult(ExecutionStatus.SUCCEEDED, [:], [:])
      }
    }
  }
}
