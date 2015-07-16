/*
 * Copyright 2014 Netflix, Inc.
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

package com.netflix.spinnaker.orca.batch.lifecycle

import com.netflix.spinnaker.orca.jackson.OrcaObjectMapper
import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import com.netflix.spinnaker.orca.pipeline.persistence.memory.InMemoryExecutionRepository
import org.springframework.batch.core.JobExecution

abstract class AbstractBatchLifecycleSpec extends BatchExecutionSpec {

  def objectMapper = new OrcaObjectMapper()
  def executionRepository = new InMemoryExecutionRepository()
  def pipeline = createPipeline()

  void setup() {
    executionRepository.store(pipeline)
  }

  abstract Pipeline createPipeline()

  @Override
  JobExecution launchJob() {
    def exec = super.launchJob(pipeline: pipeline.id)
    pipeline.stages[0].startTime = System.currentTimeMillis()
    exec
  }
}
