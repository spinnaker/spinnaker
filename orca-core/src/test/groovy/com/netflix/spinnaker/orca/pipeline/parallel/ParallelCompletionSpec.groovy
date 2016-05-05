package com.netflix.spinnaker.orca.pipeline.parallel

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.orca.DefaultTaskResult
import com.netflix.spinnaker.orca.Task
import com.netflix.spinnaker.orca.config.JesqueConfiguration
import com.netflix.spinnaker.orca.config.OrcaConfiguration
import com.netflix.spinnaker.orca.config.OrcaPersistenceConfiguration
import com.netflix.spinnaker.orca.pipeline.PipelineStarter
import com.netflix.spinnaker.orca.pipeline.StageDetailsTask
import com.netflix.spinnaker.orca.pipeline.model.Stage
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import com.netflix.spinnaker.orca.restart.PipelineRestartingSpec
import com.netflix.spinnaker.orca.test.JobCompletionListener
import com.netflix.spinnaker.orca.test.TestConfiguration
import com.netflix.spinnaker.orca.test.batch.BatchTestConfiguration
import com.netflix.spinnaker.orca.test.redis.EmbeddedRedisConfiguration
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll
import static com.netflix.spinnaker.orca.ExecutionStatus.*

class ParallelCompletionSpec extends Specification {

  def applicationContext = new AnnotationConfigApplicationContext()

  @Autowired PipelineStarter pipelineStarter
  @Autowired JobCompletionListener jobCompletionListener
  @Autowired ExecutionRepository repository

  def task = Stub(Task)

  def setup() {
    def stage = new PipelineRestartingSpec.AutowiredTestStage("test", task)
    applicationContext.with {
      register(
        EmbeddedRedisConfiguration,
        JesqueConfiguration,
        TestConfiguration,
        JobCompletionListener,
        BatchTestConfiguration,
        OrcaConfiguration,
        OrcaPersistenceConfiguration,
        StageDetailsTask
      )
      beanFactory.registerSingleton("testStage", stage)
      refresh()
      beanFactory.autowireBean(stage)
      beanFactory.autowireBean(this)
    }
    stage.applicationContext = applicationContext
  }

  @Unroll
  def "pipeline fails when branch #branch fails with status #failureStatus"() {
    given: "one stage will fail but all others (if executed) will succeed"
    task.execute(_) >> { Stage stage ->
      new DefaultTaskResult(stage.name == failAtStage ? failureStatus : SUCCEEDED)
    }

    when: "the pipeline runs"
    def id = pipelineStarter.start(pipelineJson).id
    jobCompletionListener.await()

    then:
    with(repository.retrievePipeline(id)) {
      status == failureStatus
      stages.find { it.name == failAtStage }.status == failureStatus
      stages.findAll { return it.name.startsWith(branchThatShouldGetCanceled) }*.status.contains(CANCELED)
      stages.find { it.name == "B3" }.status == NOT_STARTED
      stages.find { it.name == "AB" }.status == NOT_STARTED
    }

    where:
    failAtStage | branchThatShouldGetCanceled
    "A1"        | "B"
    "B1"        | "A"

    branch = failAtStage.substring(0, 1)
    failureStatus = TERMINAL
  }

  @Shared pipelineDefinition = [
    application    : "idontcare",
    name           : "whatever",
    stages         : [
      [
        refId               : "1",
        requisiteStageRefIds: [],
        type                : "test",
        name                : "A1"
      ],
      [
        refId               : "2",
        requisiteStageRefIds: ["1"],
        type                : "test",
        name                : "A2"
      ],
      [
        refId               : "3",
        requisiteStageRefIds: [],
        type                : "test",
        name                : "B1"
      ],
      [
        refId               : "4",
        requisiteStageRefIds: ["3"],
        type                : "test",
        name                : "B2"
      ],
      [
        refId               : "5",
        requisiteStageRefIds: ["4"],
        type                : "test",
        name                : "B3"
      ],
      [
        refId               : "6",
        requisiteStageRefIds: ["2", "5"],
        type                : "test",
        name                : "AB"
      ]
    ],
    triggers       : [],
    limitConcurrent: false,
    parallel       : true
  ]
  @Shared ObjectMapper mapper = new ObjectMapper()
  @Shared pipelineJson = mapper.writeValueAsString(pipelineDefinition)

}
