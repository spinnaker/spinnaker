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
import groovy.transform.PackageScope
import com.google.common.annotations.VisibleForTesting
import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import org.springframework.batch.core.JobParameters
import org.springframework.batch.core.JobParametersBuilder
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Component
@CompileStatic
class PipelineStarter extends ExecutionStarter<Pipeline> {

  @Autowired ExecutionRepository executionRepository
  @Autowired PipelineJobBuilder executionJobBuilder

  PipelineStarter() {
    super("pipeline")
  }

  @Override
  protected Pipeline create(Map<String, Serializable> config) {
    return parseConfig(config)
  }

  @Override
  protected void persistExecution(Pipeline pipeline) {
    executionRepository.store(pipeline)
  }

  @VisibleForTesting
  @PackageScope
  static Pipeline parseConfig(Map<String, Serializable> config) {
    Pipeline.builder()
            .withApplication(config.application.toString())
            .withName(config.name.toString())
            .withPipelineConfigId(config.id ? config.id.toString() : null)
            .withTrigger((Map<String, Object>) config.trigger)
            .withStages((List<Map<String, Object>>) config.stages)
            .withAppConfig((Map<String, Serializable>) config.appConfig)
            .withParallel(config.parallel as Boolean)
            .withLimitConcurrent(config.limitConcurrent as Boolean)
            .build()
  }

  @Override
  protected JobParameters createJobParameters(Pipeline pipeline) {
    def params = new JobParametersBuilder(super.createJobParameters(pipeline))
    params.addString("name", pipeline.name)
    params.toJobParameters()
  }
}
