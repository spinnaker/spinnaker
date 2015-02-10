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


package com.netflix.spinnaker.kato.orchestration

import com.netflix.spinnaker.kato.data.task.Task
import com.netflix.spinnaker.kato.data.task.TaskRepository
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationContext

import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeoutException

class DefaultOrchestrationProcessor implements OrchestrationProcessor {
  private static final String TASK_PHASE = "ORCHESTRATION"

  protected ExecutorService executorService = Executors.newCachedThreadPool()

  @Autowired
  TaskRepository taskRepository

  @Autowired
  ApplicationContext applicationContext

  Task process(List<AtomicOperation> atomicOperations) {
    def task = taskRepository.create(TASK_PHASE, "Initializing Orchestration Task...")
    executorService.submit {
      try {
        // Autowire the atomic operations
        for (op in atomicOperations) {
          autowire op
        }
        TaskRepository.threadLocalTask.set(task)
        def results = []
        for (AtomicOperation atomicOperation : atomicOperations) {
          task.updateStatus TASK_PHASE, "Processing op: ${atomicOperation.class.simpleName}"
          try {
            results << atomicOperation.operate(results)
            task.updateStatus(TASK_PHASE, "Orchestration completed.")
          } catch (e) {
            def message = e.message
            e.printStackTrace()
            if (!message) {
              def stringWriter = new StringWriter()
              def printWriter = new PrintWriter(stringWriter)
              e.printStackTrace(printWriter)
              message = stringWriter.toString()
            }
            task.updateStatus TASK_PHASE, "Orchestration failed: ${atomicOperation.class.simpleName} | ${e.class.simpleName}: [${message}]"
            task.addResultObjects([[type: "EXCEPTION", operation: atomicOperation.class.simpleName, cause: e.class.simpleName, message: message]])
            task.fail()
          }
        }
        task.addResultObjects(results.findResults { it })
        if (!task.status?.isCompleted()) {
          task.complete()
        }
      } catch (Exception e) {
        if (e instanceof TimeoutException) {
          task.updateStatus "INIT", "Orchestration timed out."
          task.addResultObjects([[type: "EXCEPTION", cause: e.class.simpleName, message: "Orchestration timed out."]])
          task.fail()
        } else {
          e.printStackTrace()
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
    task
  }

  void autowire(obj) {
    applicationContext.autowireCapableBeanFactory.autowireBean obj
  }
}
