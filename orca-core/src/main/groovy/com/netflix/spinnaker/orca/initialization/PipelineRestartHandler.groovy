package com.netflix.spinnaker.orca.initialization

import com.netflix.spinnaker.orca.notifications.AbstractNotificationHandler
import com.netflix.spinnaker.orca.pipeline.PipelineJobBuilder
import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.springframework.batch.core.BatchStatus
import org.springframework.batch.core.ExitStatus
import org.springframework.batch.core.JobExecution
import org.springframework.batch.core.explore.JobExplorer
import org.springframework.batch.core.repository.JobRepository
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
  @Autowired JobRepository jobRepository
  @Autowired JobExplorer jobExplorer
  @Autowired ExecutionRepository executionRepository

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
      resetJobExecutionFor(pipeline)
      pipelineStarter.resume(pipeline)
    } catch (e) {
      log.error("Unable to resume pipeline", e)
      throw e
    }
  }

  private void resetJobExecutionFor(Pipeline pipeline) {
    def jobName = pipelineJobBuilder.jobNameFor(pipeline)
    def executions = jobExplorer.findRunningJobExecutions(jobName)
    if (executions.size() == 1) {
      resetExecution(executions.first())
    } else {
      throw new IllegalStateException("Expected to a find one running job execution but found ${executions.size()}")
    }
  }

  /**
   * Because "restartability" of a Spring Batch job relies on it having been cleanly stopped and we can't guarantee
   * that we need to update the job to a STOPPED state.
   */
  private void resetExecution(JobExecution execution) {
    execution.setExitStatus(ExitStatus.STOPPED.addExitDescription("restarted after instance shutdown"))
    execution.setStatus(BatchStatus.STOPPED)
    execution.setEndTime(new Date())
    jobRepository.update(execution)
  }

}
