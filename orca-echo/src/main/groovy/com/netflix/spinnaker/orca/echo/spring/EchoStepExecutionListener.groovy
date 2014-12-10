package com.netflix.spinnaker.orca.echo.spring

import groovy.transform.CompileStatic
import com.netflix.spinnaker.orca.echo.EchoService
import org.springframework.batch.core.ExitStatus
import org.springframework.batch.core.StepExecution
import org.springframework.batch.core.listener.StepExecutionListenerSupport
import org.springframework.beans.factory.annotation.Autowired

/**
 * Converts step execution events to Echo events.
 */
@CompileStatic
class EchoStepExecutionListener extends StepExecutionListenerSupport {

  private final EchoService echoService

  @Autowired
  EchoStepExecutionListener(EchoService echoService) {
    this.echoService = echoService
  }

  @Override
  ExitStatus afterStep(StepExecution stepExecution) {
    echoService.recordEvent([:])
    return super.afterStep(stepExecution)
  }
}
