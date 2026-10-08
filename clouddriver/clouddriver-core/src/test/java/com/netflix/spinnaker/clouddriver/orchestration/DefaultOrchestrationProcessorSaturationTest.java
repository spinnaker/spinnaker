/*
 * Copyright 2026 McIntosh.farm
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.orchestration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spectator.api.NoopRegistry;
import com.netflix.spinnaker.clouddriver.config.ExceptionClassifierConfigurationProperties;
import com.netflix.spinnaker.clouddriver.config.OrchestrationExecutorProperties;
import com.netflix.spinnaker.clouddriver.data.task.DefaultTask;
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository;
import com.netflix.spinnaker.kork.dynamicconfig.DynamicConfigService;
import com.netflix.spinnaker.kork.web.context.AuthenticatedRequestContextProvider;
import com.netflix.spinnaker.kork.web.exceptions.ExceptionMessageDecorator;
import com.netflix.spinnaker.kork.web.exceptions.ExceptionSummaryService;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.context.ApplicationContext;

/** The processor runs a bounded number of operations (spinnaker/spinnaker#8109 P2). */
class DefaultOrchestrationProcessorSaturationTest {

  private final CountDownLatch release = new CountDownLatch(1);
  private final CountDownLatch started = new CountDownLatch(1);
  private DefaultOrchestrationProcessor processor;

  @AfterEach
  void tearDown() throws Exception {
    release.countDown();
    processor.executorService.shutdown();
    processor.executorService.awaitTermination(5, TimeUnit.SECONDS);
  }

  @Test
  void rejectsNewOperationsOnceTheLimitIsReachedWithoutCreatingATask() throws Exception {
    TaskRepository taskRepository = repositoryCreating("first");
    processor = processor(taskRepository, 1, 0);
    processor.process("cloudProvider", List.of(blockingOperation()), "first");
    assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();

    assertThatThrownBy(
            () ->
                processor.process("cloudProvider", List.of(mock(AtomicOperation.class)), "second"))
        .isInstanceOfSatisfying(
            OperationsSaturatedException.class,
            e -> assertThat(e.getRetryAfterSeconds()).isEqualTo(5));

    verify(taskRepository, never()).create(any(), any(), eq("second"));
  }

  @Test
  void aRepeatedRequestForARunningTaskIsStillAnsweredWhenSaturated() throws Exception {
    TaskRepository taskRepository = repositoryCreating("first");
    processor = processor(taskRepository, 1, 0);
    var task = processor.process("cloudProvider", List.of(blockingOperation()), "first");
    assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
    when(taskRepository.getByClientRequestId("first")).thenReturn(task);

    var again = processor.process("cloudProvider", List.of(mock(AtomicOperation.class)), "first");

    assertThat(again).isSameAs(task);
  }

  @Test
  void aRejectedSubmitLeavesTheTaskRetryable() {
    DefaultTask task = new DefaultTask("1");
    TaskRepository taskRepository = mock(TaskRepository.class);
    when(taskRepository.create(any(), any(), eq("request"))).thenReturn(task);
    processor = processor(taskRepository, 1, 0);
    processor.executorService.shutdown();
    processor.executorService =
        new ThreadPoolExecutor(1, 1, 1, TimeUnit.SECONDS, new SynchronousQueue<>()) {
          @Override
          public void execute(Runnable command) {
            throw new RejectedExecutionException("full");
          }
        };

    assertThatThrownBy(
            () ->
                processor.process("cloudProvider", List.of(mock(AtomicOperation.class)), "request"))
        .isInstanceOf(OperationsSaturatedException.class);

    // The caller retries with the same request ID, which re-runs a retryable task.
    assertThat(task.isRetryable()).isTrue();
  }

  private AtomicOperation blockingOperation() throws Exception {
    AtomicOperation operation = mock(AtomicOperation.class);
    when(operation.operate(any()))
        .thenAnswer(
            invocation -> {
              started.countDown();
              release.await(30, TimeUnit.SECONDS);
              return null;
            });
    return operation;
  }

  private static TaskRepository repositoryCreating(String clientRequestId) {
    TaskRepository taskRepository = mock(TaskRepository.class);
    when(taskRepository.create(any(), any(), eq(clientRequestId))).thenReturn(new DefaultTask("1"));
    return taskRepository;
  }

  private static DefaultOrchestrationProcessor processor(
      TaskRepository taskRepository, int maxThreads, int queueCapacity) {
    OrchestrationExecutorProperties properties = new OrchestrationExecutorProperties();
    properties.setMaxThreads(maxThreads);
    properties.setQueueCapacity(queueCapacity);
    ApplicationContext applicationContext = mock(ApplicationContext.class);
    when(applicationContext.getAutowireCapableBeanFactory())
        .thenReturn(mock(AutowireCapableBeanFactory.class));
    return new DefaultOrchestrationProcessor(
        taskRepository,
        applicationContext,
        new NoopRegistry(),
        Optional.empty(),
        new ObjectMapper(),
        new ExceptionClassifier(
            new ExceptionClassifierConfigurationProperties(), mock(DynamicConfigService.class)),
        new AuthenticatedRequestContextProvider(),
        new ExceptionSummaryService(mock(ExceptionMessageDecorator.class)),
        properties);
  }
}
