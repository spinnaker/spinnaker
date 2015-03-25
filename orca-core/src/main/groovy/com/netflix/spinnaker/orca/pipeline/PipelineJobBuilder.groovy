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
import com.netflix.spinnaker.orca.batch.StageBuilder
import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import com.netflix.spinnaker.orca.pipeline.model.Stage
import org.springframework.batch.core.Job
import org.springframework.batch.core.job.builder.JobFlowBuilder
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
    buildFlow(jobBuilder, pipeline).build().build()
  }

  @Override
  String jobNameFor(Pipeline pipeline) {
    "Pipeline:${pipeline.application}:${pipeline.name}:${pipeline.id}"
  }

  private JobFlowBuilder buildStart(Pipeline pipeline) {
    def jobBuilder = jobs.get(jobNameFor(pipeline))
    pipelineListeners.each {
      jobBuilder = jobBuilder.listener(it)
    }
    jobBuilder.flow(initializationStep(steps, pipeline))
  }

  private JobFlowBuilder buildFlow(JobFlowBuilder jobBuilder, Pipeline pipeline) {
    def stages = []
    stages.addAll(pipeline.stages.findAll {
      // only consider non-synthetic stages when building the flow
      return it.parentStageId == null
    })
    stages.inject(jobBuilder, this.&createStage) as JobFlowBuilder
  }

  protected JobFlowBuilder createStage(JobFlowBuilder jobBuilder, Stage<Pipeline> stage) {
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
