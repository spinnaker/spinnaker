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
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.context.ApplicationContext;

class DefaultOrchestrationProcessorTest {

  /**
   * If recording progress fails after the cloud operation already succeeded, for example during a
   * brief task-store outage, the operation must not be reported as failed (spinnaker/spinnaker#8109
   * R2).
   */
  @Test
  void failedProgressWriteDoesNotFailASuccessfulOperation() throws Exception {
    DefaultTask task = new FailsOnceRecordingCompletionTask();
    TaskRepository taskRepository = mock(TaskRepository.class);
    when(taskRepository.create(any(), any(), eq("request"))).thenReturn(task);

    DefaultOrchestrationProcessor processor = processor(taskRepository);
    processor.process("cloudProvider", List.of(mock(AtomicOperation.class)), "request");
    processor.executorService.shutdown();
    processor.executorService.awaitTermination(5, TimeUnit.SECONDS);

    assertThat(task.getStatus().isFailed()).isFalse();
    assertThat(task.getStatus().isCompleted()).isTrue();
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
}
