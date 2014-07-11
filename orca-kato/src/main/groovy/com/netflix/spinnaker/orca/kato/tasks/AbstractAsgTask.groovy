package com.netflix.spinnaker.orca.kato.tasks

import groovy.transform.CompileStatic
import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.orca.DefaultTaskResult
import com.netflix.spinnaker.orca.Task
import com.netflix.spinnaker.orca.TaskContext
import com.netflix.spinnaker.orca.TaskResult
import com.netflix.spinnaker.orca.kato.api.EnableOrDisableAsgOperation
import com.netflix.spinnaker.orca.kato.api.KatoService
import org.springframework.beans.factory.annotation.Autowired
import static com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES

@CompileStatic
abstract class AbstractAsgTask implements Task {

  @Autowired
  KatoService kato

  @Autowired
  ObjectMapper mapper

  abstract String getAsgAction()

  @Override
  TaskResult execute(TaskContext context) {
    EnableOrDisableAsgOperation operation = operationFromContext(context)
    def taskId = kato.requestOperations([[("${asgAction}Description".toString()): operation]])
        .toBlockingObservable().first()
    new DefaultTaskResult(TaskResult.Status.SUCCEEDED, [
        "kato.task.id"                     : taskId,
        "deploy.account.name"              : operation.credentials,
        "targetop.asg.${asgAction}.name"   : operation.asgName,
        "targetop.asg.${asgAction}.regions": operation.regions
    ])
  }

  private EnableOrDisableAsgOperation operationFromContext(TaskContext context) {
    def operation = mapper.copy()
        .configure(FAIL_ON_UNKNOWN_PROPERTIES, false)
        .convertValue(context.getInputs(asgAction), EnableOrDisableAsgOperation)
    return operation
  }
}
