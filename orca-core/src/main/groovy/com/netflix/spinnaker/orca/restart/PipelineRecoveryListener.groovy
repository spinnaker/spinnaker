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

import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.orca.pipeline.ExecutionRunner
import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression
import org.springframework.context.ApplicationListener
import org.springframework.context.event.ContextRefreshedEvent
import org.springframework.stereotype.Component
import static com.netflix.spinnaker.orca.ExecutionStatus.NOT_STARTED
import static com.netflix.spinnaker.orca.ExecutionStatus.RUNNING

/**
 * Looks for pipelines that were previously running on the current instance before it restarted and resumes them.
 */
@Deprecated
@Component
@ConditionalOnExpression('${pollers.stalePipelines.enabled:true}')
@Slf4j
@CompileStatic
class PipelineRecoveryListener implements ApplicationListener<ContextRefreshedEvent> {

  private final ExecutionRepository executionRepository
  private final ExecutionRunner pipelineStarter
  private final String currentInstanceId
  private final Registry registry

  @Autowired
  PipelineRecoveryListener(ExecutionRepository executionRepository,
                           @Qualifier("springBatchExecutionRunner") ExecutionRunner pipelineStarter,
                           String currentInstanceId,
                           Registry registry) {
    this.currentInstanceId = currentInstanceId
    this.executionRepository = executionRepository
    this.pipelineStarter = pipelineStarter
    this.registry = registry
  }

  @Override
  void onApplicationEvent(ContextRefreshedEvent event) {
    log.info("Looking for in-progress pipelines owned by this instance")
    executionRepository.retrievePipelines()
                       .doOnCompleted { log.info("Finished looking for in-progress pipelines owned by this instance") }
                       .doOnError { err -> log.error "Error fetching executions", err }
                       .retry()
                       .filter { it.status in [NOT_STARTED, RUNNING] && it.executingInstance == currentInstanceId }
                       .doOnNext { log.warn "Found pipeline $it.application $it.name owned by this instance" }
                       .subscribe this.&onResumablePipeline
  }

  private void onResumablePipeline(Pipeline pipeline) {
    try {
      pipelineStarter.restart(pipeline)
      registry.counter("pipeline.restarts").increment()
    } catch (Exception e) {
      registry.counter("pipeline.failed.restarts").increment()
    }
  }
}
