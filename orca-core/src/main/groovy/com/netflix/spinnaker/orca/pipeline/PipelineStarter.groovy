/*
 * Copyright 2014 Netflix, Inc.
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

package com.netflix.spinnaker.orca.pipeline

import com.google.common.annotations.VisibleForTesting
import groovy.transform.CompileStatic
import org.springframework.batch.core.Job
import org.springframework.batch.core.job.builder.JobFlowBuilder
import org.springframework.stereotype.Component
import rx.subjects.ReplaySubject


import static com.netflix.spinnaker.orca.batch.PipelineFulfillerTasklet.initializeFulfiller
import static com.netflix.spinnaker.orca.batch.PipelineInitializerTasklet.initializationStep
import static java.lang.System.currentTimeMillis

@Component
@CompileStatic
class PipelineStarter extends AbstractOrchestrationInitiator {

  /**
   * Builds a _pipeline_ based on config from _Mayo_.
   *
   * @param configJson _Mayo_ pipeline configuration.
   * @return the pipeline that was created.
   */
  Job build(Map<String, Object> config, ReplaySubject subject) {
    def pipeline = parseConfig(config)
    createJobFrom(pipeline, subject)
  }

  @VisibleForTesting
  private static Pipeline parseConfig(Map<String, Object> config) {
    Pipeline.builder()
      .withApplication(config.application.toString())
      .withName(config.name.toString())
      .withStages((List<Map<String, Serializable>>) config.stages)
      .build()
  }

  private Job createJobFrom(Pipeline pipeline, ReplaySubject subject) {
    // TODO: can we get any kind of meaningful identifier from the mayo config?
    def jobBuilder = jobs.get("orca-pipeline-${pipeline.application}-${pipeline.name}-${currentTimeMillis()}")
      .flow(initializationStep(steps, pipeline))
      .next(initializeFulfiller(steps, pipeline, subject)) as JobFlowBuilder
    buildFlow(jobBuilder, pipeline).build().build()
  }

  private JobFlowBuilder buildFlow(JobFlowBuilder jobBuilder, Pipeline pipeline) {
    (JobFlowBuilder) pipeline.stages.inject(jobBuilder, this.&createStage)
  }

}
