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

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import com.netflix.spinnaker.orca.batch.ExecutionListenerProvider
import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import org.springframework.batch.core.JobExecution
import org.springframework.batch.core.JobParameter
import org.springframework.batch.core.JobParameters
import org.springframework.batch.core.JobParametersBuilder
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Component
@CompileStatic
@Slf4j
class PipelineStarter extends ExecutionStarter<Pipeline> {

  @Autowired ExecutionRepository executionRepository
  @Autowired PipelineJobBuilder executionJobBuilder
  @Autowired(required = false) PipelineStartTracker startTracker
  @Autowired ExecutionListenerProvider executionListenerProvider

  PipelineStarter() {
    super("pipeline")
  }

  @Override
  protected Pipeline create(Map<String, Serializable> config) {
    Pipeline
      .builder()
      .withApplication(config.application.toString())
      .withName(config.name.toString())
      .withPipelineConfigId(config.id ? config.id.toString() : null)
      .withTrigger((Map<String, Object>) config.trigger)
      .withStages((List<Map<String, Object>>) config.stages)
      .withAppConfig((Map<String, Serializable>) config.appConfig)
      .withParallel(config.parallel as Boolean)
      .withLimitConcurrent(config.limitConcurrent as Boolean)
      .withKeepWaitingPipelines(config.keepWaitingPipelines as Boolean)
      .withExecutingInstance(currentInstanceId)
      .withExecutionEngine(config.executionEngine?.toString())
      .withNotifications((List<Map<String, Object>>) config.notifications)
      .build()
  }

  @Override
  protected void persistExecution(Pipeline pipeline) {
    executionRepository.store(pipeline)
  }

  @Override
  protected JobParameters createJobParameters(Pipeline pipeline) {
    def params = new JobParametersBuilder(super.createJobParameters(pipeline))
    params.addString("name", pipeline.name)
    params.toJobParameters()
  }

  @Override
  protected boolean queueExecution(Pipeline pipeline) {
    return pipeline.pipelineConfigId &&
      pipeline.limitConcurrent &&
      startTracker &&
      startTracker.queueIfNotStarted(pipeline.pipelineConfigId, pipeline.id)
  }

  @Override
  protected void afterJobLaunch(Pipeline pipeline) {
    startTracker?.addToStarted(pipeline.pipelineConfigId, pipeline.id)
  }

  @Override
  protected void onCompleteBeforeLaunch(Pipeline pipeline) {
    super.onCompleteBeforeLaunch(pipeline)

    executionListenerProvider?.allJobExecutionListeners()?.each {
      it.afterJob(new JobExecution(0L, new JobParameters([pipeline: new JobParameter(pipeline.id)])))
    }
  }
}
