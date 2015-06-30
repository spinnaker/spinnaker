package com.netflix.spinnaker.orca.restart

import com.netflix.spinnaker.orca.pipeline.PipelineJobBuilder
import com.netflix.spinnaker.orca.pipeline.PipelineStarter
import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import org.springframework.batch.core.*
import org.springframework.batch.core.explore.JobExplorer
import org.springframework.batch.core.repository.JobRepository
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll
import static org.apache.commons.lang.math.JVMRandom.nextLong

class PipelineRestartHandlerSpec extends Specification {

  def jobRepository = Mock(JobRepository)
  def jobExplorer = Mock(JobExplorer)
  def pipelineStarter = Mock(PipelineStarter)
  def executionRepository = Stub(ExecutionRepository) {
    retrievePipeline(input.id) >> pipeline
  }
  @Shared pipelineJobBuilder = new PipelineJobBuilder()

  @Shared def input = [id: "1", application: "orca", name: "test"]
  @Shared def pipeline = new Pipeline(id: input.id, application: input.application, name: input.name)
  @Shared def jobName = pipelineJobBuilder.jobNameFor(pipeline)
  @Subject handler = handlerFor(input)

  def "handler resets underlying job then resumes the pipeline"() {
    given:
    jobExplorer.getJobInstances(jobName, _, _) >> [new JobInstance(1L, jobName)]
    jobExplorer.getJobExecutions({ it.jobName == jobName }) >> [jobExecution(jobName)]

    when:
    handler.run()

    then:
    1 * jobRepository.update({ JobExecution it ->
      it.status == BatchStatus.STOPPED && it.exitStatus.exitCode == ExitStatus.STOPPED.exitCode && it.endTime != null
    })

    then:
    1 * pipelineStarter.resume(pipeline)
  }

  @Unroll
  def "if the handler finds #n executions it ignores the job"() {
    given:
    jobExplorer.getJobInstances(jobName, _, _) >> [new JobInstance(1L, jobName)]
    jobExplorer.getJobExecutions({ it.jobName == jobName }) >> executions

    when:
    handler.run()

    then:
    0 * jobRepository.update(_)
    0 * pipelineStarter.resume(_)

    where:
    executions                                     | _
    []                                             | _
    [jobExecution(jobName), jobExecution(jobName)] | _

    n = executions.size()
  }

  private PipelineRestartHandler handlerFor(Map input) {
    def handler = new PipelineRestartHandler(input)
    handler.pipelineStarter = pipelineStarter
    handler.jobRepository = jobRepository
    handler.jobExplorer = jobExplorer
    handler.executionRepository = executionRepository
    handler.pipelineJobBuilder = pipelineJobBuilder
    return handler
  }

  private JobExecution jobExecution(String name) {
    new JobExecution(nextLong(100L),
                     new JobParametersBuilder().addString("pipeline", "pipeline-$name").toJobParameters())
  }
}
