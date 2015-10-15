package com.netflix.spinnaker.orca.batch.pipeline

import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.pipeline.PipelineStartTracker
import com.netflix.spinnaker.orca.pipeline.PipelineStarter
import com.netflix.spinnaker.orca.pipeline.PipelineStarterListener
import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import com.netflix.spinnaker.orca.pipeline.model.PipelineStage
import com.netflix.spinnaker.orca.pipeline.model.Stage
import com.netflix.spinnaker.orca.pipeline.model.Task
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import org.springframework.batch.core.JobExecution
import org.springframework.batch.core.JobParameter
import org.springframework.batch.core.JobParameters
import spock.lang.Specification
import spock.lang.Subject

class PipelineStarterListenerSpec extends Specification {

  @Subject
  PipelineStarterListener listener = new PipelineStarterListener()

  def jobExecution = new JobExecution(0L, new JobParameters([pipeline: new JobParameter('something')]))

  void setup() {
    listener.executionRepository = Mock(ExecutionRepository)
    listener.startTracker = Mock(PipelineStartTracker)
    listener.pipelineStarter = Mock(PipelineStarter)
  }

  def "should do nothing if there is no started pipelines"() {
    when:
    listener.afterJob(jobExecution)

    then:
    1 * listener.startTracker.getAllStartedExecutions() >> []

    and:
    0 * _._
  }

  def "should start next pipeline in the queue"() {
    when:
    listener.afterJob(jobExecution)

    then:
    1 * listener.startTracker.getAllStartedExecutions() >> ['123']
    1 * listener.startTracker.getQueuedPipelines(_) >> ['123']

    and:
    1 * listener.startTracker.markAsFinished('abc', '123')
    2 * listener.executionRepository.retrievePipeline(_) >> new Pipeline(id: '123', pipelineConfigId: 'abc', canceled: true)
    1 * listener.pipelineStarter.startExecution(_)
    1 * listener.startTracker.removeFromQueue('abc', '123')
    0 * _._
  }

  def "should mark waiting pipelines that are not started as canceled"() {
    def canceledPipelines = []

    when:
    listener.afterJob(jobExecution)

    then:
    1 * listener.startTracker.getQueuedPipelines(_) >> ['123', '124', '125']
    listener.executionRepository.store(_) >> { Pipeline pipeline ->
      if (pipeline.canceled == true) {
        canceledPipelines.add(pipeline)
      }
    }

    and:
    1 * listener.startTracker.getAllStartedExecutions() >> ['121']
    1 * listener.startTracker.markAsFinished('abc', '121')
    1 * listener.executionRepository.retrievePipeline('121') >> new Pipeline(id: '121', pipelineConfigId: 'abc', canceled: true)
    1 * listener.executionRepository.retrievePipeline('123') >> new Pipeline(id: '123', pipelineConfigId: 'abc')
    1 * listener.executionRepository.retrievePipeline('124') >> new Pipeline(id: '124', pipelineConfigId: 'abc')
    1 * listener.executionRepository.retrievePipeline('125') >> new Pipeline(id: '125', pipelineConfigId: 'abc')
    1 * listener.pipelineStarter.startExecution({ it.id == '123' })
    1 * listener.startTracker.removeFromQueue('abc', '123')
    1 * listener.startTracker.removeFromQueue('abc', '124')
    1 * listener.startTracker.removeFromQueue('abc', '125')

    and:
    canceledPipelines.size() == 2
  }

  def "should mark started executions as finished even without a pipelineConfigId"() {
    when:
    listener.afterJob(jobExecution)

    then:
    1 * listener.startTracker.getAllStartedExecutions() >> ['123']
    1 * listener.executionRepository.retrievePipeline(_) >> new Pipeline(id: '123', canceled:true)
    1 * listener.startTracker.markAsFinished(null, '123')
    0 * _._
  }

  def "should process all pipelines in a completed state"() {
    when:
    listener.afterJob(jobExecution)

    then:
    1 * listener.startTracker.getAllStartedExecutions() >> ['123', '124', '125', '126']
    1 * listener.executionRepository.retrievePipeline('123') >> new Pipeline(id: '123')
    1 * listener.executionRepository.retrievePipeline('124') >> new Pipeline(id: '124', canceled:true)
    1 * listener.executionRepository.retrievePipeline('125') >> new Pipeline(id: '125')
    1 * listener.executionRepository.retrievePipeline('126') >> new Pipeline(id: '126', canceled:true)
    1 * listener.startTracker.markAsFinished(null, '124')
    1 * listener.startTracker.markAsFinished(null, '126')
    0 * _._
  }

  def "should skip pipelines that are not started"() {
    when:
    listener.afterJob(jobExecution)

    then:
    1 * listener.startTracker.getAllStartedExecutions() >> ['123']
    1 * listener.executionRepository.retrievePipeline(_) >> new Pipeline(id: '123')
    0 * _._
  }

}
