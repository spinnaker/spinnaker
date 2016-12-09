package com.netflix.spinnaker.orca.echo.spring

import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.echo.EchoService
import com.netflix.spinnaker.orca.listeners.Persister
import com.netflix.spinnaker.orca.listeners.StageListener
import com.netflix.spinnaker.orca.pipeline.model.*
import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import static com.netflix.spinnaker.orca.ExecutionStatus.*
import static java.lang.System.currentTimeMillis

/**
 * Converts execution events to Echo events.
 */
@CompileStatic
@Slf4j
class EchoNotifyingStageListener implements StageListener {

  private final EchoService echoService

  @Autowired
  EchoNotifyingStageListener(EchoService echoService) {
    this.echoService = echoService
  }

  @Override
  <T extends Execution<T>> void beforeTask(Persister persister,
                                           Stage<T> stage,
                                           Task task) {
    if (task.status == NOT_STARTED) {
      recordEvent('task', 'starting', stage, task)
    }
  }

  @Override
  @CompileDynamic
  <T extends Execution<T>> void beforeStage(Persister persister,
                                            Stage<T> stage) {
    if (stage.status == NOT_STARTED) {
      def details = [
        name       : stage.name,
        type       : stage.type,
        // because this listener runs before the one setting the startTime
        // TODO: handle better when we remove v1 path
        startTime  : stage.startTime ?: currentTimeMillis(),
        isSynthetic: stage.syntheticStageOwner != null
      ]
      stage.context.stageDetails = details
      persister.save(stage)

      log.debug("***** $stage.execution.id Echo stage $stage.name starting v2")
      recordEvent("stage", "starting", stage)
    }
  }

  @Override
  <T extends Execution<T>> void afterTask(Persister persister,
                                          Stage<T> stage,
                                          Task task,
                                          ExecutionStatus executionStatus,
                                          boolean wasSuccessful) {
    if (executionStatus == RUNNING) {
      return
    }

    recordEvent('task', (wasSuccessful ? "complete" : "failed"), stage, task)

    // TODO: this should all be deleted once we move to v2 engine
    if (stage.execution instanceof Pipeline && stage.execution.executionEngine == "v1") {
      if (wasSuccessful) {
        if (task.name.contains('stageEnd')) {
          log.debug("***** $stage.execution.id Echo stage $stage.name complete")
          recordEvent('stage', 'complete', stage, task)
        } else if (task.name.contains('stageStart')) {
          log.debug("***** $stage.execution.id Echo stage $stage.name starting")
          recordEvent('stage', 'starting', stage, task)
        }
      } else {
        log.debug("***** $stage.execution.id Echo stage $stage.name failed")
        recordEvent('stage', 'failed', stage, task)
      }
    }
  }

  @Override
  @CompileDynamic
  <T extends Execution<T>> void afterStage(Persister persister,
                                           Stage<T> stage) {
    if (stage.execution instanceof Pipeline) {
      if (stage.endTime) {
        stage.context.stageDetails.endTime = stage.endTime
      }
      persister.save(stage)

      // STOPPED stages are "successful" because they allow the pipeline to
      // proceed but they are still failures in terms of the stage and should
      // send failure notifications
      if (stage.status in [SUCCEEDED, SKIPPED]) {
        log.debug("***** $stage.execution.id Echo stage $stage.name complete v2")
        recordEvent('stage', 'complete', stage)
      } else {
        log.debug("***** $stage.execution.id Echo stage $stage.name failed v2")
        recordEvent('stage', 'failed', stage)
      }
    }
  }

  private void recordEvent(String type, String phase, Stage stage, Task task) {
    recordEvent(type, phase, stage, Optional.of(task))
  }

  private void recordEvent(String type, String phase, Stage stage) {
    recordEvent(type, phase, stage, Optional.empty())
  }

  private void recordEvent(String type, String phase, Stage stage, Optional<Task> maybeTask) {
    try {
      def event = [
        details: [
          source     : "orca",
          type       : "orca:${type}:${phase}".toString(),
          application: stage.execution.application
        ],
        content: [
          standalone : stage.execution instanceof Orchestration,
          canceled   : stage.execution.canceled,
          context    : stage.context,
          startTime  : stage.startTime,
          endTime    : stage.endTime,
          execution  : stage.execution,
          executionId: stage.execution.id
        ]
      ]
      maybeTask.ifPresent { Task task ->
        event.content.taskName = "${stage.type}.${task.name}".toString()
      }
      echoService.recordEvent(event)
    } catch (Exception e) {
      log.error("Failed to send ${type} event ${phase} ${stage.execution.id} ${maybeTask.map { Task task -> task.name }}", e)
    }
  }
}
