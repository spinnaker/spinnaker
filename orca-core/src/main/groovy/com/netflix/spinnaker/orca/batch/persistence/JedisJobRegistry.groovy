package com.netflix.spinnaker.orca.batch.persistence

import com.netflix.spinnaker.orca.pipeline.PipelineStarter
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import org.springframework.batch.core.Job
import org.springframework.batch.core.configuration.DuplicateJobException
import org.springframework.batch.core.configuration.JobFactory
import org.springframework.batch.core.configuration.JobRegistry
import org.springframework.batch.core.explore.JobExplorer
import org.springframework.batch.core.launch.NoSuchJobException

class JedisJobRegistry implements JobRegistry {

  private final JobExplorer jobExplorer
  private final ExecutionRepository executionRepository
  private final PipelineStarter pipelineStarter

  public JedisJobRegistry(JobExplorer jobExplorer,
                          ExecutionRepository executionRepository,
                          PipelineStarter pipelineStarter) {
    this.jobExplorer = jobExplorer
    this.executionRepository = executionRepository
    this.pipelineStarter = pipelineStarter
  }

  @Override
  public void register(JobFactory jobFactory) throws DuplicateJobException {
    throw new UnsupportedOperationException()
  }

  @Override public void unregister(String jobName) {
    throw new UnsupportedOperationException()
  }

  @Override public Collection<String> getJobNames() {
    jobExplorer.jobNames
  }

  @Override public Job getJob(String name) throws NoSuchJobException {
    def jobInstances = jobExplorer.getJobInstances(name, 0, 1)
    if (jobInstances.isEmpty()) {
      throw new NoSuchJobException("No job configuration with the name [${name}] was registered")
    }
    def jobInstance = jobInstances.get(0)
    def jobExecutions = jobExplorer.getJobExecutions(jobInstance)
    if (jobExecutions.isEmpty()) {
      throw new NoSuchJobException("Unable to recreate the job [${name}] as it has never been executed")
    }
    // jobExecutions are ordered newest first
    def latestExecution = jobExecutions.get(0)
    def pipelineId = latestExecution.jobParameters.getString("pipeline")
    def pipeline = executionRepository.retrievePipeline(pipelineId)
    pipelineStarter.build(pipeline.initialConfig, pipeline)
  }
}
