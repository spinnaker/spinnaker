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

package com.netflix.spinnaker.clouddriver.orchestration;

import static com.netflix.spinnaker.security.AuthenticatedRequest.propagate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.netflix.spectator.api.Id;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.clouddriver.core.ClouddriverHostname;
import com.netflix.spinnaker.clouddriver.data.task.Task;
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository;
import com.netflix.spinnaker.clouddriver.event.exceptions.DuplicateEventAggregateException;
import com.netflix.spinnaker.clouddriver.metrics.TimedCallable;
import com.netflix.spinnaker.clouddriver.orchestration.events.OperationEvent;
import com.netflix.spinnaker.clouddriver.orchestration.events.OperationEventHandler;
import com.netflix.spinnaker.kork.api.exceptions.ExceptionSummary;
import com.netflix.spinnaker.kork.web.context.RequestContextProvider;
import com.netflix.spinnaker.kork.web.exceptions.ExceptionSummaryService;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;

@Slf4j
public class DefaultOrchestrationProcessor implements OrchestrationProcessor {
  private static final String TASK_PHASE = "ORCHESTRATION";

  protected ExecutorService executorService =
      new ThreadPoolExecutor(
          0,
          Integer.MAX_VALUE,
          60L,
          TimeUnit.SECONDS,
          new SynchronousQueue<Runnable>(),
          new ThreadFactoryBuilder()
              .setNameFormat(DefaultOrchestrationProcessor.class.getSimpleName() + "-%d")
              .build()) {
        @Override
        protected void afterExecute(Runnable r, Throwable t) {
          clearRequestContext();
          super.afterExecute(r, t);
        }
      };

  private final TaskRepository taskRepository;
  private final ApplicationContext applicationContext;
  private final Registry registry;
  private final Collection<OperationEventHandler> operationEventHandlers;
  private final ObjectMapper objectMapper;
  private final ExceptionClassifier exceptionClassifier;
  private final RequestContextProvider contextProvider;
  private final ExceptionSummaryService exceptionSummaryService;

  public DefaultOrchestrationProcessor(
      TaskRepository taskRepository,
      ApplicationContext applicationContext,
      Registry registry,
      Optional<Collection<OperationEventHandler>> operationEventHandlers,
      ObjectMapper objectMapper,
      ExceptionClassifier exceptionClassifier,
      RequestContextProvider contextProvider,
      ExceptionSummaryService exceptionSummaryService) {
    this.taskRepository = taskRepository;
    this.applicationContext = applicationContext;
    this.registry = registry;
    this.operationEventHandlers = operationEventHandlers.orElse(Collections.emptyList());
    this.objectMapper = objectMapper;
    this.exceptionClassifier = exceptionClassifier;
    this.contextProvider = contextProvider;
    this.exceptionSummaryService = exceptionSummaryService;
  }

  @Override
  public Task process(
      @Nullable String cloudProvider,
      @Nonnull List<AtomicOperation> atomicOperations,
      @Nonnull String clientRequestId) {
    Id orchestrationsId =
        registry
            .createId("orchestrations")
            .withTag("cloudProvider", cloudProvider != null ? cloudProvider : "unknown");
    Id atomicOperationId =
        registry
            .createId("operations")
            .withTag("cloudProvider", cloudProvider != null ? cloudProvider : "unknown");
    Id tasksId =
        registry
            .createId("tasks")
            .withTag("cloudProvider", cloudProvider != null ? cloudProvider : "unknown");

    // Get the task (either an existing one, or a new one). If the task already exists,
    // `shouldExecute` will be false if the task is in a failed state and the failure is not
    // retryable.
    GetTaskResult result = getTask(clientRequestId);
    Task task = result.task;
    if (!result.shouldExecute) {
      log.debug(
          "task with id {} has the shouldExecute flag set to false - not executing the task",
          task.getId());
      return task;
    }

    Callable<Void> operationClosure =
        () -> {
          try {
            // Autowire the atomic operations
            for (AtomicOperation op : atomicOperations) {
              autowire(op);
            }
            TaskRepository.threadLocalTask.set(task);
            List<Object> results = new ArrayList<>();
            for (AtomicOperation atomicOperation : atomicOperations) {
              Id thisOp =
                  atomicOperationId.withTag(
                      "OperationType", atomicOperation.getClass().getSimpleName());
              task.updateStatus(
                  TASK_PHASE, "Processing op: " + atomicOperation.getClass().getSimpleName());
              try {
                TimedCallable.forCallable(
                        registry,
                        thisOp,
                        (Callable<Void>)
                            () -> {
                              results.add(atomicOperation.operate(results));

                              @SuppressWarnings("unchecked")
                              Collection<OperationEvent> events = atomicOperation.getEvents();
                              if (events != null) {
                                for (OperationEvent event : events) {
                                  for (OperationEventHandler handler : operationEventHandlers) {
                                    try {
                                      handler.handle(event);
                                    } catch (Exception e) {
                                      log.warn(
                                          "Error handling event ("
                                              + event
                                              + "): "
                                              + atomicOperation.getClass().getSimpleName(),
                                          e);
                                      task.updateStatus(
                                          TASK_PHASE,
                                          "Error handling event ("
                                              + event
                                              + "): "
                                              + atomicOperation.getClass().getSimpleName()
                                              + " | "
                                              + e.getClass().getSimpleName()
                                              + ": ["
                                              + e.getMessage()
                                              + "]");
                                    }
                                  }
                                }
                              }

                              if (task.getStatus() != null && task.getStatus().isFailed()) {
                                task.updateStatus(
                                    TASK_PHASE,
                                    "Orchestration completed with errors, see prior task logs.");
                              } else {
                                task.updateStatus(TASK_PHASE, "Orchestration completed.");
                              }
                              return null;
                            })
                    .call();
              } catch (AtomicOperationException e) {
                task.updateStatus(
                    TASK_PHASE,
                    "Orchestration failed: "
                        + atomicOperation.getClass().getSimpleName()
                        + " | "
                        + e.getClass().getSimpleName()
                        + ": ["
                        + String.join(", ", e.getErrors())
                        + "]");
                task.addResultObjects(
                    List.of(
                        extractExceptionSummary(
                            e,
                            String.join(", ", e.getErrors()),
                            Map.of("operation", atomicOperation.getClass().getSimpleName()))));
                failTask(task, e);
              } catch (DuplicateEventAggregateException e) {
                // In this case, we can safely assume that the atomic operation is being run
                // elsewhere and can just return the existing task.
                log.warn(
                    "Received duplicate event aggregate: Indicative of receiving the same operation twice. Noop'ing and returning the task pointer",
                    e);
                return null;
              } catch (Exception e) {
                String message = e.getMessage();
                StringWriter stringWriter = new StringWriter();
                PrintWriter printWriter = new PrintWriter(stringWriter);
                e.printStackTrace(printWriter);
                String stackTrace = stringWriter.toString();
                if (message == null || message.isEmpty()) {
                  message = stackTrace;
                }
                task.updateStatus(
                    TASK_PHASE,
                    "Orchestration failed: "
                        + atomicOperation.getClass().getSimpleName()
                        + " | "
                        + e.getClass().getSimpleName()
                        + ": ["
                        + message
                        + "]");
                task.addResultObjects(
                    List.of(
                        extractExceptionSummary(
                            e,
                            message,
                            Map.of("operation", atomicOperation.getClass().getSimpleName()))));

                log.error(stackTrace);
                failTask(task, e);
              }
            }
            task.addResultObjects(
                results.stream().filter(Objects::nonNull).collect(Collectors.toList()));
            if (task.getStatus() == null || !task.getStatus().isCompleted()) {
              task.complete();
            }
            registry.counter(tasksId.withTag("success", "true")).increment();
          } catch (Exception e) {
            registry
                .counter(
                    tasksId
                        .withTag("success", "false")
                        .withTag("cause", e.getClass().getSimpleName()))
                .increment();
            if (e instanceof TimeoutException) {
              task.updateStatus("INIT", "Orchestration timed out.");
              task.addResultObjects(
                  List.of(extractExceptionSummary(e, "Orchestration timed out.")));
              failTask(task, e);
            } else {
              StringWriter stringWriter = new StringWriter();
              PrintWriter printWriter = new PrintWriter(stringWriter);
              e.printStackTrace(printWriter);
              task.updateStatus("INIT", "Unknown failure -- " + stringWriter.toString());
              task.addResultObjects(
                  List.of(extractExceptionSummary(e, "Failed for unknown reason.")));
              failTask(task, e);
            }
          } finally {
            if (task.getStatus() == null || !task.getStatus().isCompleted()) {
              task.complete();
            }
          }
          return null;
        };

    TimedCallable<Void> timedCallable =
        TimedCallable.forCallable(registry, orchestrationsId, propagate(operationClosure, true));
    executorService.submit(timedCallable);

    return task;
  }

  void autowire(Object obj) {
    applicationContext.getAutowireCapableBeanFactory().autowireBean(obj);
  }

  /**
   * Ensure that the Spinnaker-related context values are cleared.
   *
   * <p>This is particularly important for the inheritable values that are used to transmit the auth
   * context.
   */
  void clearRequestContext() {
    try {
      var context = contextProvider.get();
      context.setUser(null);
      context.setAccounts((String) null);
      context.setExecutionId(null);
    } catch (Exception e) {
      log.error("Unable to clear request context", e);
    }
  }

  /**
   * For backwards compatibility.
   *
   * <p>TODO(rz): Not 100% sure we should keep these two methods.
   */
  Map<String, Object> extractExceptionSummary(Throwable e, String userMessage) {
    ExceptionSummary summary = exceptionSummaryService.summary(e);
    Map<String, Object> map = objectMapper.convertValue(summary, Map.class);
    map.put("message", userMessage);
    map.put("type", "EXCEPTION");
    return map;
  }

  /**
   * For backwards compatibility.
   *
   * <p>TODO(rz): Add "additionalFields" to ExceptionSummary?
   */
  Map<String, Object> extractExceptionSummary(
      Throwable e, String userMessage, Map<String, Object> additionalFields) {
    Map<String, Object> summary = extractExceptionSummary(e, userMessage);
    summary.putAll(additionalFields);
    return summary;
  }

  @Nonnull
  private GetTaskResult getTask(String clientRequestId) {
    Task existingTask = taskRepository.getByClientRequestId(clientRequestId);
    if (existingTask != null) {
      if (!existingTask.isRetryable()) {
        return new GetTaskResult(existingTask, false);
      }
      existingTask.updateStatus(
          TASK_PHASE, "Re-initializing Orchestration Task (failure is retryable)");
      existingTask.retry();
      existingTask.updateOwnerId(ClouddriverHostname.ID, TASK_PHASE);
      return new GetTaskResult(existingTask, true);
    }
    return new GetTaskResult(
        taskRepository.create(TASK_PHASE, "Initializing Orchestration Task", clientRequestId),
        true);
  }

  private void failTask(@Nonnull Task task, @Nonnull Exception e) {
    if (task.hasSagaIds()) {
      task.fail(exceptionClassifier.isRetryable(e));
    } else {
      // Tasks that are not Saga-backed are automatically assumed to not be retryable.
      task.fail(false);
    }
  }

  @Data
  @AllArgsConstructor
  private static class GetTaskResult {
    private Task task;
    private boolean shouldExecute;
  }
}
