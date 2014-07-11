package com.netflix.spinnaker.orca.kato.tasks

import spock.lang.Specification
import spock.lang.Subject
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.guava.GuavaModule
import com.netflix.spinnaker.orca.SimpleTaskContext
import com.netflix.spinnaker.orca.TaskResult
import com.netflix.spinnaker.orca.kato.api.DestroyAsgOperation
import com.netflix.spinnaker.orca.kato.api.KatoService
import com.netflix.spinnaker.orca.kato.api.TaskId

class DestroyAsgTaskSpec extends Specification {
  @Subject task = new DestroyAsgTask()
  def context = new SimpleTaskContext()
  def mapper = new ObjectMapper()
  def taskId = new TaskId(UUID.randomUUID().toString())

  def destroyASGConfig = [
      asgName    : "test-asg",
      region     : "us-west-1",
      credentials: "fzlem"
  ]

  def setup() {
    mapper.registerModule(new GuavaModule())

    task.mapper = mapper

    destroyASGConfig.each {
      context."destroyAsg.$it.key" = it.value
    }
  }

  def "creates a destroy ASG task based on job parameters"() {
    given:
    def operations
    task.kato = Mock(KatoService) {
      1 * requestOperations(*_) >> {
        operations = it[0]
        rx.Observable.from(taskId)
      }
    }

    when:
    task.execute(context)

    then:
    operations.size() == 1
    with(operations[0].deleteAsgDescription) {
      it instanceof DestroyAsgOperation
      asgName == destroyASGConfig.asgName
      region == destroyASGConfig.region
      credentials == destroyASGConfig.credentials
    }
  }

  def "returns a success status with the kato task id"() {
    given:
    task.kato = Stub(KatoService) {
      requestOperations(*_) >> rx.Observable.from(taskId)
    }

    when:
    def result = task.execute(context)

    then:
    result.status == TaskResult.Status.SUCCEEDED
    result.outputs."kato.task.id" == taskId
    result.outputs."deploy.account.name" == destroyASGConfig.credentials
  }
}
