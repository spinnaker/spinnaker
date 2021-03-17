package com.netflix.spinnaker.orca.clouddriver

import com.netflix.spinnaker.orca.api.operations.OperationsContext
import com.netflix.spinnaker.orca.api.operations.OperationsInput
import com.netflix.spinnaker.orca.clouddriver.model.KatoOperationsContext
import com.netflix.spinnaker.orca.clouddriver.model.TaskId
import com.netflix.spinnaker.orca.jackson.OrcaObjectMapper
import spock.lang.Specification
import spock.lang.Unroll

import static com.netflix.spinnaker.orca.test.model.ExecutionBuilder.stage

class KatoOperationsRunnerSpec extends Specification {

  @Unroll
  def "Runs a kato operation based on operation input"() {
    given:
    def taskId = new TaskId(UUID.randomUUID().toString())
    def katoService = Mock(KatoService)
    def katoOperationsRunner = new KatoOperationsRunner(katoService)
    def stage = stage {
      type = "test"
      context = ["account.name" : "test"]
    }

    def operationsInput = OperationsInput.builder()
      .cloudProvider(cloudProvider)
      .operations([[:]])
      .stageExecution(stage)
      .contextKey(contextKey)
      .build()

    when:
    def operationsContextResult = katoOperationsRunner.run(operationsInput)

    then:
    if (cloudProvider != null) {
      1 * katoService.requestOperations(operationsInput.cloudProvider, operationsInput.operations) >> { taskId }
    } else {
      1 * katoService.requestOperations(operationsInput.operations) >> { taskId }
    }
    operationsContextResult.contextKey() == expectedContextKey

    where:
    cloudProvider | contextKey    || expectedContextKey
    "aws"         | "example.key" || "example.key"
    null          | null          || "kato.last.task.id"
  }

  def "OperationsContext objectMappered to string succeeds"() {
    given:
    def mapper = OrcaObjectMapper.newInstance()
    TaskId taskId = new TaskId(UUID.randomUUID().toString())
    OperationsContext operationsContext = KatoOperationsContext.from(taskId, "test.key")
    def context = Map.of(operationsContext.contextKey(), operationsContext.contextValue())

    when:
    def result = mapper.writeValueAsString(context)

    then:
    result.contains("test.key")
  }
}
