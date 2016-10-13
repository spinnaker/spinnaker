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

package com.netflix.spinnaker.orca.pipeline

import groovy.transform.CompileStatic
import groovy.transform.PackageScope
import com.google.common.annotations.VisibleForTesting
import com.netflix.spinnaker.orca.batch.StageBuilder
import com.netflix.spinnaker.orca.pipeline.model.AbstractStage
import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import com.netflix.spinnaker.orca.pipeline.model.Stage
import com.netflix.spinnaker.orca.pipeline.model.SyntheticStageOwner
import org.springframework.batch.core.Job
import org.springframework.batch.core.job.builder.FlowBuilder
import org.springframework.batch.core.job.builder.JobFlowBuilder
import org.springframework.batch.core.job.flow.Flow
import org.springframework.stereotype.Component
import static com.netflix.spinnaker.orca.batch.PipelineInitializerTasklet.initializationStep

@Component
@CompileStatic
class PipelineJobBuilder extends ExecutionJobBuilder<Pipeline> {

  /**
   * Builds a _pipeline_ based on config from _Mayo_.
   *
   * @param configJson _Mayo_ pipeline configuration.
   * @return the pipeline that was created.
   */
  @Override
  Job build(Pipeline pipeline) {
    def jobBuilder = buildStart(pipeline)

    if (pipeline.parallel) {
      return buildFlowParallel(jobBuilder, pipeline).build().build()
    }

    return buildFlowLinear(jobBuilder, pipeline).build().build()
  }

  @Override
  String jobNameFor(Pipeline pipeline) {
    "Pipeline:${pipeline.application}:${pipeline.name}:${pipeline.id}"
  }

  @VisibleForTesting
  @PackageScope
  JobFlowBuilder buildStart(Pipeline pipeline) {
    def jobBuilder = jobs.get(jobNameFor(pipeline))
    getPipelineListeners().each {
      jobBuilder = jobBuilder.listener(it)
    }
    jobBuilder.flow(initializationStep(steps, pipeline))
  }

  @Deprecated
  private JobFlowBuilder buildFlowLinear(JobFlowBuilder jobBuilder, Pipeline pipeline) {
    def stages = []
    stages.addAll(pipeline.stages.findAll {
      // only consider non-synthetic stages when building the flow
      return it.parentStageId == null
    })
    return stages.inject(jobBuilder, this.&createStage) as JobFlowBuilder
  }

  private JobFlowBuilder buildFlowParallel(JobFlowBuilder jobBuilder, Pipeline pipeline) {
    Stage initializationStage
    if (pipeline.stages[0].id == "${pipeline.id}-initialize" as String) {
      // already initialized, no need to do it again
      initializationStage = pipeline.stages[0]
    } else {
      initializationStage = StageBuilder.newStage(
        pipeline,
        "pipelineInitialization",
        "Initialize",
        [:],
        null as Stage,
        null as SyntheticStageOwner
      )

      ((AbstractStage) initializationStage).id = "${pipeline.id}-initialize"
      initializationStage.initializationStage = true
      initializationStage.refId = "*"

      def stages = [] as List<Stage>
      stages.addAll(pipeline.stages.findAll {
        // only consider non-synthetic non-child stages when building the root flow
        return !it.parentStageId && !it.requisiteStageRefIds && it.refId != initializationStage.refId
      })
      stages.each { Stage stage ->
        // add the initialization stage as a requisite, this ensures that each root will be executed in parallel
        stage.requisiteStageRefIds = [initializationStage.refId]
      }

      // the initialization stage should be injected at the beginning of the pipeline
      pipeline.stages.add(0, initializationStage)
    }

    def initializationBuilder = new FlowBuilder<Flow>("Initialization.${pipeline.id}")
    createStage(initializationBuilder, initializationStage)

    jobBuilder.next(initializationBuilder.build())
    return jobBuilder
  }

  protected FlowBuilder createStage(FlowBuilder jobBuilder, Stage<Pipeline> stage) {
    builderFor(stage).build(jobBuilder, stage)
    return jobBuilder
  }

  protected StageBuilder builderFor(Stage<Pipeline> stage) {
    if (stages.containsKey(stage.type)) {
      stages.get(stage.type)
    } else {
      throw new NoSuchStageException(stage.type)
    }
  }
}
