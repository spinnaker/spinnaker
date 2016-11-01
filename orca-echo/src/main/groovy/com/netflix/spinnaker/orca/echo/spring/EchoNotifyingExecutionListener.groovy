package com.netflix.spinnaker.orca.echo.spring

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.echo.EchoService
import com.netflix.spinnaker.orca.listeners.ExecutionListener
import com.netflix.spinnaker.orca.listeners.Persister
import com.netflix.spinnaker.orca.pipeline.model.Execution
import com.netflix.spinnaker.orca.pipeline.model.Pipeline

@Slf4j
@CompileStatic
class EchoNotifyingExecutionListener implements ExecutionListener {

  private final EchoService echoService

  EchoNotifyingExecutionListener(EchoService echoService) {
    this.echoService = echoService
  }

  @Override
  void beforeExecution(Persister persister, Execution execution) {
    if (execution instanceof Pipeline) {
      try {
        if (execution.status != ExecutionStatus.SUSPENDED) {
          echoService.recordEvent(
            details: [
              source     : "orca",
              type       : "orca:pipeline:starting",
              application: execution.application,
            ],
            content: [
              execution  : execution,
              executionId: execution.id
            ]
          )
        }
      } catch (Exception e) {
        log.error("Failed to send pipeline start event: ${execution?.id}")
      }
    }
  }

  @Override
  void afterExecution(Persister persister,
                      Execution execution,
                      ExecutionStatus executionStatus,
                      boolean wasSuccessful) {
    if (execution instanceof Pipeline) {
      try {
        if (execution.status != ExecutionStatus.SUSPENDED) {
          echoService.recordEvent(
            details: [
              source     : "orca",
              type       : "orca:pipeline:${wasSuccessful ? "complete" : "failed"}".toString(),
              application: execution.application,
            ],
            content: [
              execution  : execution,
              executionId: execution.id
            ]
          )
        }
      } catch (Exception e) {
        log.error("Failed to send pipeline end event: ${execution?.id}")
      }
    }
  }
}
