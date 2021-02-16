/*
 * Copyright 2014 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.orchestration

import com.fasterxml.jackson.databind.ObjectMapper
import com.google.common.util.concurrent.ThreadFactoryBuilder
import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.event.exceptions.DuplicateEventAggregateException
import com.netflix.spinnaker.clouddriver.metrics.TimedCallable
import com.netflix.spinnaker.clouddriver.orchestration.events.OperationEvent
import com.netflix.spinnaker.clouddriver.orchestration.events.OperationEventHandler
import com.netflix.spinnaker.kork.api.exceptions.ExceptionSummary
import com.netflix.spinnaker.kork.web.context.RequestContextProvider
import com.netflix.spinnaker.kork.web.exceptions.ExceptionSummaryService
import groovy.transform.Canonical
import groovy.util.logging.Slf4j
import org.springframework.context.ApplicationContext

import javax.annotation.Nonnull
import javax.annotation.Nullable
import java.util.concurrent.ExecutorService
import java.util.concurrent.SynchronousQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

import static com.netflix.spinnaker.security.AuthenticatedRequest.propagate

@Slf4j
class DefaultOrchestrationProcessor implements OrchestrationProcessor {
  private static final String TASK_PHASE = "ORCHESTRATION"

  protected ExecutorService executorService = new ThreadPoolExecutor(0, Integer.MAX_VALUE,
    60L, TimeUnit.SECONDS,
    new SynchronousQueue<Runnable>(),
    new ThreadFactoryBuilder().setNameFormat(DefaultOrchestrationProcessor.class.getSimpleName() + "-%d").build()) {
    @Override
    protected void afterExecute(Runnable r, Throwable t) {
      clearRequestContext()
      super.afterExecute(r, t)
    }
  }

  private final TaskRepository taskRepository
  private final ApplicationContext applicationContext
  private final Registry registry
  private final Collection<OperationEventHandler> operationEventHandlers
  private final ObjectMapper objectMapper
  private final ExceptionClassifier exceptionClassifier
  private final RequestContextProvider contextProvider
  private final ExceptionSummaryService exceptionSummaryService

  DefaultOrchestrationProcessor(
    TaskRepository taskRepository,
    ApplicationContext applicationContext,
    Registry registry,
    Optional<Collection<OperationEventHandler>> operationEventHandlers,
    ObjectMapper objectMapper,
    ExceptionClassifier exceptionClassifier,
    RequestContextProvider contextProvider,
    ExceptionSummaryService exceptionSummaryService
  ) {
    this.taskRepository = taskRepository
    this.applicationContext = applicationContext
    this.registry = registry
    this.operationEventHandlers = operationEventHandlers.orElse([])
    this.objectMapper = objectMapper
    this.exceptionClassifier = exceptionClassifier
    this.contextProvider = contextProvider
    this.exceptionSummaryService = exceptionSummaryService
  }

  @Override
  Task process(@Nullable String cloudProvider,
               @Nonnull List<AtomicOperation> atomicOperations,
               @Nonnull String clientRequestId) {
    def orchestrationsId = registry.createId('orchestrations').withTag("cloudProvider", cloudProvider ?: "unknown")
    def atomicOperationId = registry.createId('operations').withTag("cloudProvider", cloudProvider ?: "unknown")
    def tasksId = registry.createId('tasks').withTag("cloudProvider", cloudProvider ?: "unknown")

    // Get the task (either an existing one, or a new one). If the task already exists, `shouldExecute` will be false
    // if the task is in a failed state and the failure is not retryable.
    def result = getTask(clientRequestId)
    def task = result.task
    if (!result.shouldExecute) {
      return task
    }

    def operationClosure = {
      try {
        // Autowire the atomic operations
        for (op in atomicOperations) {
          autowire op
        }
        TaskRepository.threadLocalTask.set(task)
        def results = []
        for (AtomicOperation atomicOperation : atomicOperations) {
          def thisOp = atomicOperationId.withTag("OperationType", atomicOperation.class.simpleName)
          task.updateStatus TASK_PHASE, "Processing op: ${atomicOperation.class.simpleName}"
          try {
            TimedCallable.forClosure(registry, thisOp) {
              results << atomicOperation.operate(results)

              atomicOperation.events.each { OperationEvent event ->
                operationEventHandlers.each {
                  try {
                    it.handle(event)
                  } catch (e) {
                    log.warn("Error handling event (${event}): ${atomicOperation.class.simpleName}", e)
                    task.updateStatus TASK_PHASE, "Error handling event (${event}): ${atomicOperation.class.simpleName} | ${e.class.simpleName}: [${e.message}]"
                  }
                }
              }

              if (task.status?.failed) {
                task.updateStatus(TASK_PHASE, "Orchestration completed with errors, see prior task logs.")
              } else {
                task.updateStatus(TASK_PHASE, "Orchestration completed.")
              }
            }.call()
          } catch (AtomicOperationException e) {
            task.updateStatus TASK_PHASE, "Orchestration failed: ${atomicOperation.class.simpleName} | ${e.class.simpleName}: [${e.errors.join(', ')}]"
            task.addResultObjects([extractExceptionSummary(e, e.errors.join(", "), [operation: atomicOperation.class.simpleName])])
            failTask(task, e)
          } catch (DuplicateEventAggregateException e) {
            // In this case, we can safely assume that the atomic operation is being run elsewhere and can just return
            // the existing task.
            log.warn("Received duplicate event aggregate: Indicative of receiving the same operation twice. Noop'ing and returning the task pointer", e)
            return getTask(clientRequestId)
          } catch (e) {
            def message = e.message
            def stringWriter = new StringWriter()
            def printWriter = new PrintWriter(stringWriter)
            e.printStackTrace(printWriter)
            def stackTrace = stringWriter.toString()
            if (!message) {
              message = stackTrace
            }
            task.updateStatus TASK_PHASE, "Orchestration failed: ${atomicOperation.class.simpleName} | ${e.class.simpleName}: [${message}]"
            task.addResultObjects([extractExceptionSummary(e, message, [operation: atomicOperation.class.simpleName])])

            log.error(stackTrace)
            failTask(task, e)
          }
        }
        task.addResultObjects(results.findResults { it })
        if (!task.status?.isCompleted()) {
          task.complete()
        }
        registry.counter(tasksId.withTag("success", "true")).increment()
      } catch (Exception e) {
        registry.counter(tasksId.withTag("success", "false").withTag("cause", e.class.simpleName)).increment()
        if (e instanceof TimeoutException) {
          task.updateStatus "INIT", "Orchestration timed out."
          task.addResultObjects([extractExceptionSummary(e, "Orchestration timed out.")])
          failTask(task, e)
        } else {
          def stringWriter = new StringWriter()
          def printWriter = new PrintWriter(stringWriter)
          e.printStackTrace(printWriter)
          task.updateStatus("INIT", "Unknown failure -- ${stringWriter.toString()}")
          task.addResultObjects([extractExceptionSummary(e, "Failed for unknown reason.")])
          failTask(task, e)
        }
      } finally {
        if (!task.status?.isCompleted()) {
          task.complete()
        }
      }
    }

    def timedCallable = TimedCallable.forCallable(registry, orchestrationsId, propagate(operationClosure, true))
    executorService.submit(timedCallable)

    task
  }

  void autowire(obj) {
    applicationContext.autowireCapableBeanFactory.autowireBean obj
  }

  /**
   * Ensure that the Spinnaker-related context values are cleared.
   *
   * This is particularly important for the inheritable values that are used to transmit the auth context.
   */
  void clearRequestContext() {
    try {
      def context = contextProvider.get()
      context.setUser(null)
      context.setAccounts(null as String)
      context.setExecutionId(null)
    } catch (Exception e) {
      log.error("Unable to clear request context", e)
    }
  }

  /**
   * For backwards compatibility.
   *
   * TODO(rz): Not 100% sure we should keep these two methods.
   */
  Map<String, Object> extractExceptionSummary(Throwable e, String userMessage) {
    ExceptionSummary summary = exceptionSummaryService.summary(e)
    Map<String, Object> map = objectMapper.convertValue(summary, Map)
    map["message"] = userMessage
    map["type"] = "EXCEPTION"
    return map
  }

  /**
   * For backwards compatibility.
   *
   * TODO(rz): Add "additionalFields" to ExceptionSummary?
   */
  Map<String, Object> extractExceptionSummary(Throwable e, String userMessage, Map<String, Object> additionalFields) {
    Map<String, Object> summary = extractExceptionSummary(e, userMessage)
    summary.putAll(additionalFields)
    return summary
  }

  @Nonnull
  private GetTaskResult getTask(String clientRequestId) {
    def existingTask = taskRepository.getByClientRequestId(clientRequestId)
    if (existingTask) {
      if (!existingTask.isRetryable()) {
        return new GetTaskResult(existingTask, false)
      }
      existingTask.updateStatus(TASK_PHASE, "Re-initializing Orchestration Task (failure is retryable)")
      existingTask.retry()
      return new GetTaskResult(existingTask, true)
    }
    return new GetTaskResult(
      taskRepository.create(TASK_PHASE, "Initializing Orchestration Task", clientRequestId),
      true
    )
  }

  private void failTask(@Nonnull Task task, @Nonnull Exception e) {
    if (task.hasSagaIds()) {
      task.fail(exceptionClassifier.isRetryable(e))
    } else {
      // Tasks that are not Saga-backed are automatically assumed to not be retryable.
      task.fail(false)
    }
  }

  @Canonical
  private static class GetTaskResult {
    Task task
    boolean shouldExecute
  }
}
