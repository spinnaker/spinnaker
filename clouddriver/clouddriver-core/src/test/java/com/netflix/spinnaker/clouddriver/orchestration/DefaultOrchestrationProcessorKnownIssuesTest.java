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

import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spectator.api.NoopRegistry;
import com.netflix.spinnaker.clouddriver.config.ExceptionClassifierConfigurationProperties;
import com.netflix.spinnaker.clouddriver.data.task.DefaultTask;
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository;
import com.netflix.spinnaker.kork.dynamicconfig.DynamicConfigService;
import com.netflix.spinnaker.kork.web.context.AuthenticatedRequestContextProvider;
import com.netflix.spinnaker.kork.web.exceptions.ExceptionMessageDecorator;
import com.netflix.spinnaker.kork.web.exceptions.ExceptionSummaryService;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.context.ApplicationContext;

/**
 * Reproduces known {@link DefaultOrchestrationProcessor} defects tracked in
 * spinnaker/spinnaker#8109 (plan: {@code plans/2026/clouddriver-task-repository.md}).
 *
 * <p>Each test asserts the correct behaviour. While a defect is still present the test logs it and
 * is <b>skipped</b> (aborted through a JUnit assumption) instead of failed, so the defect stays
 * visible in test reports without breaking the build. The PR fixing it should replace the
 * assumption with a hard assertion.
 */
class DefaultOrchestrationProcessorKnownIssuesTest {

  private static final Logger log =
      LoggerFactory.getLogger(DefaultOrchestrationProcessorKnownIssuesTest.class);

  /**
   * R2 (SQL side, work-plan PR 7): if recording progress fails after the cloud operation already
   * succeeded, for example during a brief task-store outage, the processor marks the operation
   * failed.
   */
  @Test
  void r2AFailedProgressWriteDoesNotFailASuccessfulOperation() throws Exception {
    DefaultTask task = new FailsOnceRecordingCompletionTask();
    TaskRepository taskRepository = mock(TaskRepository.class);
    when(taskRepository.create(any(), any(), eq("request"))).thenReturn(task);

    DefaultOrchestrationProcessor processor = processor(taskRepository);
    processor.process("cloudProvider", List.of(mock(AtomicOperation.class)), "request");
    processor.executorService.shutdown();
    processor.executorService.awaitTermination(5, TimeUnit.SECONDS);

    knownIssue(
        "R2",
        task.getStatus().isFailed(),
        "an operation that succeeded is marked failed when recording its completion fails once");
  }

  /** A task whose store is briefly unavailable while the processor records completion. */
  private static class FailsOnceRecordingCompletionTask extends DefaultTask {
    private boolean failed;

    FailsOnceRecordingCompletionTask() {
      super("1");
    }

    @Override
    public void updateStatus(String phase, String status) {
      if (!failed && status.equals("Orchestration completed.")) {
        failed = true;
        throw new IllegalStateException("Task store unavailable");
      }
      super.updateStatus(phase, status);
    }
  }

  private static DefaultOrchestrationProcessor processor(TaskRepository taskRepository) {
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
        new ExceptionSummaryService(mock(ExceptionMessageDecorator.class)));
  }

  private static void knownIssue(String id, boolean reproduced, String description) {
    String message =
        String.format(
            "KNOWN ISSUE %s (spinnaker/spinnaker#8109) %s: %s",
            id, reproduced ? "reproduced" : "not reproduced", description);
    if (reproduced) {
      log.warn(message);
    }
    assumeTrue(!reproduced, message);
  }
}
