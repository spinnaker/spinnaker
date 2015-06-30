package com.netflix.spinnaker.orca.restart

import com.netflix.appinfo.InstanceInfo
import com.netflix.spinnaker.orca.pipeline.model.Execution
import com.netflix.spinnaker.orca.pipeline.model.Orchestration
import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import groovy.transform.CompileStatic
import org.springframework.batch.core.JobExecution
import org.springframework.batch.core.JobExecutionListener
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Component
@CompileStatic
class ExecutionTracker implements JobExecutionListener {

  private final ExecutionRepository executionRepository
  private final InstanceInfo currentInstance

  @Autowired
  ExecutionTracker(ExecutionRepository executionRepository, InstanceInfo currentInstance) {
    this.executionRepository = executionRepository
    this.currentInstance = currentInstance
  }

  void beforeExecution(Execution<?> execution) {
    execution.executingInstance = currentInstance.id
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

  void afterExecution(Execution<?> execution) {

  }

  @Override
  final void beforeJob(JobExecution jobExecution) {
    beforeExecution(currentExecution(jobExecution))
  }

  @Override
  final void afterJob(JobExecution jobExecution) {
    afterExecution(currentExecution(jobExecution))
  }

  private Execution currentExecution(JobExecution jobExecution) {
    if (jobExecution.jobParameters.parameters.containsKey("pipeline")) {
      String id = jobExecution.jobParameters.getString("pipeline")
      executionRepository.retrievePipeline(id)
    } else {
      String id = jobExecution.jobParameters.getString("orchestration")
      executionRepository.retrieveOrchestration(id)
    }
  }
}
