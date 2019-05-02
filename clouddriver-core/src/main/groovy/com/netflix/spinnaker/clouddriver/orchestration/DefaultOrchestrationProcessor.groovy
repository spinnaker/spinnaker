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

import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.metrics.TimedCallable
import com.netflix.spinnaker.clouddriver.orchestration.events.OperationEvent
import com.netflix.spinnaker.clouddriver.orchestration.events.OperationEventHandler
import com.netflix.spinnaker.security.AuthenticatedRequest
import groovy.util.logging.Slf4j
import org.slf4j.MDC
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationContext

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
    new SynchronousQueue<Runnable>()) {
    @Override
    protected void afterExecute(Runnable r, Throwable t) {
      resetMDC()
      super.afterExecute(r, t)
    }
  }

  @Autowired
  TaskRepository taskRepository

  @Autowired
  ApplicationContext applicationContext

  @Autowired
  Registry registry

  @Autowired(required = false)
  Collection<OperationEventHandler> operationEventHandlers = []

  @Override
  Task process(List<AtomicOperation> atomicOperations, String clientRequestId) {

    def orchestrationsId = registry.createId('orchestrations')
    def atomicOperationId = registry.createId('operations')
    def tasksId = registry.createId('tasks')
    def existingTask = taskRepository.getByClientRequestId(clientRequestId)
    if (existingTask) {
      return existingTask
    }
    def task = taskRepository.create(TASK_PHASE, "Initializing Orchestration Task...", clientRequestId)
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
                    task.updateStatus TASK_PHASE, "Error handling event (${event}): ${atomicOperation.class.simpleName} | ${e.class.simpleName}: [${e.message}]"
                  }
                }
              }

              task.updateStatus(TASK_PHASE, "Orchestration completed.")
            }.call()
          } catch (AtomicOperationException e) {
            task.updateStatus TASK_PHASE, "Orchestration failed: ${atomicOperation.class.simpleName} | ${e.class.simpleName}: [${e.errors.join(', ')}]"
            task.addResultObjects([[type: "EXCEPTION", operation: atomicOperation.class.simpleName, cause: e.class.simpleName, message: e.errors.join(", ")]])
            task.fail()
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
            task.addResultObjects([[type: "EXCEPTION", operation: atomicOperation.class.simpleName, cause: e.class.simpleName, message: message]])

            log.error(stackTrace)
            task.fail()
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
          task.addResultObjects([[type: "EXCEPTION", cause: e.class.simpleName, message: "Orchestration timed out."]])
          task.fail()
        } else {
          def stringWriter = new StringWriter()
          def printWriter = new PrintWriter(stringWriter)
          e.printStackTrace(printWriter)
          task.updateStatus("INIT", "Unknown failure -- ${stringWriter.toString()}")
          task.addResultObjects([[type: "EXCEPTION", cause: e.class.simpleName, message: "Failed for unknown reason."]])
          task.fail()
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
   * Ensure that the Spinnaker-related MDC values are cleared.
   *
   * This is particularly important for the inheritable MDC variables that are commonly to transmit the auth context.
   */
  static void resetMDC() {
    try {
      MDC.remove(AuthenticatedRequest.Header.USER.header)
      MDC.remove(AuthenticatedRequest.Header.ACCOUNTS.header)
      MDC.remove(AuthenticatedRequest.Header.EXECUTION_ID.header)
    } catch (Exception e) {
      log.error("Unable to clear thread locals, reason: ${e.message}")
    }
  }
}
