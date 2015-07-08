package com.netflix.spinnaker.orca.restart

import com.netflix.appinfo.InstanceInfo
import com.netflix.spectator.api.ExtendedRegistry
import com.netflix.spinnaker.orca.pipeline.PipelineStarter
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
@Component
@ConditionalOnExpression('${pollers.stalePipelines.enabled:true}')
@Slf4j
@CompileStatic
class PipelineRecoveryListener implements ApplicationListener<ContextRefreshedEvent> {

  private final ExecutionRepository executionRepository
  private final PipelineStarter pipelineStarter
  private final InstanceInfo currentInstance
  private final ExtendedRegistry extendedRegistry

  @Autowired
  PipelineRecoveryListener(ExecutionRepository executionRepository,
                           PipelineStarter pipelineStarter,
                           @Qualifier("instanceInfo") InstanceInfo currentInstance,
                           ExtendedRegistry extendedRegistry) {
    this.currentInstance = currentInstance
    this.executionRepository = executionRepository
    this.pipelineStarter = pipelineStarter
    this.extendedRegistry = extendedRegistry
  }

  @Override
  void onApplicationEvent(ContextRefreshedEvent event) {
    log.info("Looking for in-progress pipelines owned by this instance")
    executionRepository.retrievePipelines()
                       .doOnCompleted { log.info("Finished looking for in-progress pipelines owned by this instance") }
                       .doOnError { err -> log.error "Error fetching executions", err }
                       .retry()
                       .filter { it.status in [NOT_STARTED, RUNNING] && it.executingInstance == currentInstance.id }
                       .doOnNext { log.warn "Found pipeline $it.application $it.name owned by this instance" }
                       .subscribe this.&onResumablePipeline
  }

  private void onResumablePipeline(Pipeline pipeline) {
    try {
      pipelineStarter.resume(pipeline)
      extendedRegistry.counter("pipeline.restarts").increment()
    } catch (Exception e) {
      extendedRegistry.counter("pipeline.failed.restarts").increment()
    }
  }
}
