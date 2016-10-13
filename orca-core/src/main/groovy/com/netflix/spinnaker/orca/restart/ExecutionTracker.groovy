/*
 * Copyright 2016 Netflix, Inc.
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

package com.netflix.spinnaker.orca.restart

import com.netflix.spinnaker.orca.listeners.ExecutionListener
import com.netflix.spinnaker.orca.listeners.Persister
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import com.netflix.spinnaker.orca.pipeline.model.Execution
import com.netflix.spinnaker.orca.pipeline.model.Orchestration
import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component

@Slf4j
@Order(0)
@Component
@CompileStatic
class ExecutionTracker implements ExecutionListener {

  private final ExecutionRepository executionRepository
  private final String currentInstanceId

  @Autowired
  ExecutionTracker(ExecutionRepository executionRepository, String currentInstanceId) {
    this.executionRepository = executionRepository
    log.info "current instance: ${currentInstanceId}"
    this.currentInstanceId = currentInstanceId
  }

  void beforeExecution(Persister persister, Execution execution) {
    execution.executingInstance = currentInstanceId
    // I really don't want to do this but the craziness of the repository API is too much to deal with today
    switch (execution) {
      case Pipeline:
        executionRepository.store((Pipeline) execution)
        break
      case Orchestration:
        executionRepository.store((Orchestration) execution)
        break
    }
  }
}
