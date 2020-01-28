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

package com.netflix.spinnaker.clouddriver.controllers

import com.fasterxml.jackson.annotation.JsonProperty
import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation
import com.netflix.spinnaker.clouddriver.orchestration.OperationsService
import com.netflix.spinnaker.clouddriver.orchestration.OrchestrationProcessor
import com.netflix.spinnaker.kork.exceptions.ConstraintViolationException
import com.netflix.spinnaker.kork.web.exceptions.NotFoundException
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Value
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

import javax.annotation.Nonnull
import javax.annotation.Nullable
import javax.annotation.PreDestroy
import java.util.concurrent.TimeUnit

import static java.lang.String.format

@Slf4j
@RestController
class OperationsController {

  private final OperationsService operationsService
  private final OrchestrationProcessor orchestrationProcessor
  private final TaskRepository taskRepository
  private final long shutdownWaitSeconds

  OperationsController(
    OperationsService operationsService,
    OrchestrationProcessor orchestrationProcessor,
    TaskRepository taskRepository,
    @Value('${admin.tasks.shutdown-wait-seconds:-1}') long shutdownWaitSeconds) {
    this.operationsService = operationsService
    this.orchestrationProcessor = orchestrationProcessor
    this.taskRepository = taskRepository
    this.shutdownWaitSeconds = shutdownWaitSeconds
  }
/**
   * @deprecated Use /{cloudProvider}/ops instead
   */
  @Deprecated
  @PostMapping("/ops")
  StartOperationResult operations(
    @RequestParam(value = "clientRequestId", required = false) String clientRequestId,
    @RequestBody List<Map<String, Map>> requestBody) {
    List<AtomicOperation> atomicOperations = operationsService.collectAtomicOperations(requestBody)
    return start(atomicOperations, clientRequestId)
  }

  /**
   * @deprecated Use /{cloudProvider}/ops/{name} instead
   */
  @Deprecated
  @PostMapping("/ops/{name}")
  StartOperationResult operation(
    @PathVariable("name") String name,
    @RequestParam(value = "clientRequestId", required = false) String clientRequestId,
    @RequestBody Map requestBody) {
    List<AtomicOperation> atomicOperations = operationsService.collectAtomicOperations([[(name): requestBody]])
    return start(atomicOperations, clientRequestId)
  }

  @PostMapping("/{cloudProvider}/ops")
  StartOperationResult cloudProviderOperations(
    @PathVariable("cloudProvider") String cloudProvider,
    @RequestParam(value = "clientRequestId", required = false) String clientRequestId,
    @RequestBody List<Map<String, Map>> requestBody) {
    List<AtomicOperation> atomicOperations = operationsService.collectAtomicOperations(cloudProvider, requestBody)
    return start(atomicOperations, clientRequestId)
  }

  @PostMapping("/{cloudProvider}/ops/{name}")
  StartOperationResult cloudProviderOperation(
    @PathVariable("cloudProvider") String cloudProvider,
    @PathVariable("name") String name,
    @RequestParam(value = "clientRequestId", required = false) String clientRequestId,
    @RequestBody Map requestBody) {
    List<AtomicOperation> atomicOperations = operationsService.collectAtomicOperations(cloudProvider, [[(name): requestBody]])
    return start(atomicOperations, clientRequestId)
  }

  @GetMapping("/task/{id}")
  Task get(@PathVariable("id") String id) {
    Task t = taskRepository.get(id)
    if (!t) {
      throw new NotFoundException("Task not found (id: ${id})")
    }
    return t
  }

  @GetMapping("/task")
  List<Task> list() {
    taskRepository.list()
  }

  /**
   * Endpoint to allow Orca to resume Tasks, if they're backed by Sagas.
   *
   * @param id
   */
  @PostMapping("/task/{id}:resume")
  StartOperationResult resumeTask(@PathVariable("id") String id) {
    Task t = taskRepository.get(id)
    if (t == null) {
      throw new NotFoundException("Task not found (id: $id)")
    }

    if (!t.status.retryable) {
      throw new ConstraintViolationException("Task is not retryable").with {
        setRetryable(false)
        it
      }
    }

    List<AtomicOperation> atomicOperations = operationsService.collectAtomicOperationsFromSagas(t.getSagaIds())
    if (atomicOperations.isEmpty()) {
      throw new NotFoundException("No saga was found for this task id: $id - can't resume")
    }
    
    return start(atomicOperations, t.requestId)
  }

  /**
   * TODO(rz): Seems like a weird place to put this logic...?
   */
  @PreDestroy
  void destroy() {
    long start = System.currentTimeMillis()
    def tasks = taskRepository.listByThisInstance()
    while (tasks && !tasks.isEmpty() &&
      (System.currentTimeMillis() - start) / TimeUnit.SECONDS.toMillis(1) < shutdownWaitSeconds) {
      log.info("There are {} task(s) still running... sleeping before shutting down", tasks.size())
      sleep(1000)
      tasks = taskRepository.listByThisInstance()
    }

    if (tasks && !tasks.isEmpty()) {
      log.error("Shutting down while tasks '{}' are still in progress!", tasks)
    }
  }

  private StartOperationResult start(@Nonnull List<AtomicOperation> atomicOperations, @Nullable String id) {
    Task task =
      orchestrationProcessor.process(
        atomicOperations, Optional.ofNullable(id).orElse(UUID.randomUUID().toString()));
    return new StartOperationResult(task.getId());
  }

  static class StartOperationResult {
    @JsonProperty private final String id

    StartOperationResult(String id) {
      this.id = id
    }

    @JsonProperty
    String getResourceUri() {
      return format("/task/%s", id)
    }
  }
}
