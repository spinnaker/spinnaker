package com.netflix.spinnaker.orca.restart

import com.netflix.spectator.api.ExtendedRegistry
import com.netflix.spinnaker.orca.notifications.AbstractNotificationHandler
import com.netflix.spinnaker.orca.pipeline.PipelineJobBuilder
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Scope
import org.springframework.stereotype.Component
import static org.springframework.beans.factory.config.ConfigurableBeanFactory.SCOPE_PROTOTYPE

@Component
@Scope(SCOPE_PROTOTYPE)
@Slf4j
@CompileStatic
class PipelineRestartHandler extends AbstractNotificationHandler {

  @Autowired PipelineJobBuilder pipelineJobBuilder
  @Autowired ExecutionRepository executionRepository
  @Autowired ExtendedRegistry extendedRegistry

  PipelineRestartHandler(Map input) {
    super(input)
  }

  @Override
  String getHandlerType() {
    PipelineRestartAgent.NOTIFICATION_TYPE
  }

  @Override
  void handle(Map input) {
    try {
      def pipeline = executionRepository.retrievePipeline(input.id as String)
      log.warn "Restarting pipeline $pipeline.application $pipeline.name $pipeline.id with status $pipeline.status"
      pipelineStarter.resume(pipeline)
      extendedRegistry.counter("pipeline.restarts").increment()
    } catch (IllegalStateException e) {
      log.error("Unable to resume pipeline: $e.message")
      extendedRegistry.counter("pipeline.failed.restarts").increment()
    } catch (e) {
      log.error("Unable to resume pipeline", e)
      extendedRegistry.counter("pipeline.failed.restarts").increment()
      throw e
    }
  }
}
