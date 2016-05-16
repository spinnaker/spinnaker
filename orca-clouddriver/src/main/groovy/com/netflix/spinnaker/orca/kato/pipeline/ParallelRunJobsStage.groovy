/*
 * Copyright 2016 Google, Inc.
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

package com.netflix.spinnaker.orca.kato.pipeline

import com.netflix.spinnaker.orca.DefaultTaskResult
import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.Task
import com.netflix.spinnaker.orca.TaskResult
import com.netflix.spinnaker.orca.clouddriver.pipeline.job.RunJobStage
import com.netflix.spinnaker.orca.pipeline.LinearStage
import com.netflix.spinnaker.orca.pipeline.ParallelStage
import com.netflix.spinnaker.orca.pipeline.model.AbstractStage
import com.netflix.spinnaker.orca.pipeline.model.Pipeline
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
class ParallelRunJobsStage extends ParallelStage {

  public static final String PIPELINE_CONFIG_TYPE = "runJobs"

  @Autowired
  List<LinearStage> stageBuilders

  ParallelRunJobsStage() {
    this(PIPELINE_CONFIG_TYPE)
  }

  protected ParallelRunJobsStage(String name) {
    super(name)
  }

  @Override
  protected List<Flow> buildFlows(Stage stage) {
    return parallelContexts(stage).collect { Map context ->
      def nextStage = newStage(
        stage.execution, context.type as String, context.name as String, new HashMap(context), stage, Stage.SyntheticStageOwner.STAGE_AFTER
      )

      def existingStage = stage.execution.stages.find { it.id == nextStage.id }
      nextStage = existingStage ?: nextStage

      if (!existingStage) {
        // in the case of a restart, this stage will already have been added to the execution
        ((AbstractStage) nextStage).type = PIPELINE_CONFIG_TYPE
        stage.execution.stages.add(nextStage)
      }

      def flowBuilder = new FlowBuilder<Flow>(context.name as String).start(
        buildStep(stage, "setupParallelRunJobs", new Task() {
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

  @CompileDynamic
  protected Map<String, Object> clusterContext(Stage stage, Map defaultStageContext, Map cluster) {
    def type = RunJobStage.PIPELINE_CONFIG_TYPE

    String name = "Run Jobs in ${cluster.region}"

    return defaultStageContext + [
      cloudProvider: cluster.cloudProvider,
      account: cluster.account ?: stage.context.account,
      cluster: cluster,
      type: type,
      name: name
    ]
  }

  @Override
  @CompileDynamic
  List<Map<String, Object>> parallelContexts(Stage stage) {

    if (stage.execution instanceof Pipeline) {
      Map trigger = ((Pipeline) stage.execution).trigger
      if (trigger.parameters?.strategy == true) {
        Map parentStage = trigger.parentExecution.stages.find {
          it.id == trigger.parameters.parentStageId
        }
        Map cluster = parentStage.context as Map
        cluster.strategy = 'none'
        stage.context.clusters = [cluster as Map<String, Object>]
      }
    }

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

    def toContext = this.&clusterContext.curry(stage, defaultStageContext)

    return clusters.collect(toContext)
  }

  @Override
  String parallelStageName(Stage stage, boolean hasParallelFlows) {
    return stage.name
  }

  @Override
  Task completeParallel() {
    return new Task() {
      TaskResult execute(Stage stage) {
        log.info("Completed Parallel Run Jobs")
        new DefaultTaskResult(ExecutionStatus.SUCCEEDED, [:], [:])
      }
    }
  }
}
