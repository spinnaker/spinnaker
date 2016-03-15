/*
 * Copyright 2016 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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

package com.netflix.spinnaker.orca.batch.listeners;

import com.netflix.spinnaker.orca.ExecutionStatus;
import com.netflix.spinnaker.orca.listeners.ExecutionListener;
import com.netflix.spinnaker.orca.listeners.Persister;
import com.netflix.spinnaker.orca.pipeline.model.Execution;
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionNotFoundException;
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.listener.JobExecutionListenerSupport;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.netflix.spinnaker.orca.ExecutionStatus.CANCELED;
import static com.netflix.spinnaker.orca.ExecutionStatus.TERMINAL;

public class SpringBatchExecutionListener extends JobExecutionListenerSupport implements ExecutionListener {
  private final ExecutionRepository executionRepository;
  private final ExecutionListener executionListener;
  private final Persister defaultPersister;

  public SpringBatchExecutionListener(ExecutionRepository executionRepository,
                                      ExecutionListener executionListener) {
    this.executionRepository = executionRepository;
    this.executionListener = executionListener;
    this.defaultPersister = new SpringBatchStageListener.DefaultPersister(executionRepository);
  }

  @Override
  public void beforeJob(JobExecution jobExecution) {
    Execution execution = execution(executionRepository, jobExecution);
    beforeExecution(defaultPersister, execution);

    if (execution != null && execution.getContext() != null) {
      // restore any previous execution context on the underlying JobExecution
      Map<String, Object> context = execution.getContext();
      context.forEach((key, value) -> {
        jobExecution.getExecutionContext().put(key, value);
      });
    }
  }

  @Override
  public void beforeExecution(Persister persister, Execution execution) {
    executionListener.beforeExecution(persister, execution);
  }

  @Override
  public void afterJob(JobExecution jobExecution) {
    ExecutionStatus executionStatus = null;
    if (jobExecution.getFailureExceptions() != null && !jobExecution.getFailureExceptions().isEmpty()) {
      executionStatus = TERMINAL;
    } else {
      List<StepExecution> stepExecutions = new ArrayList<>(jobExecution.getStepExecutions());
      stepExecutions.sort((a, b) -> b.getLastUpdated().compareTo(a.getLastUpdated()));

      StepExecution stepExecution = stepExecutions
        .stream()
        .filter(s -> s.getStatus() == jobExecution.getStatus())
        .findFirst()
        .orElseGet(() -> stepExecutions.get(0));

      if (stepExecution != null && stepExecution.getExecutionContext() != null) {
        executionStatus = (ExecutionStatus) stepExecution.getExecutionContext().get("orcaTaskStatus");
      }
    }

    final Execution execution = execution(executionRepository, jobExecution);
    afterExecution(new Persister() {
      @Override
      public void save(Stage stage) {
        defaultPersister.save(stage);
      }

      @Override
      public boolean isCanceled(String executionId) {
        return defaultPersister.isCanceled(executionId);
      }

      @Override
      public void updateStatus(String executionId, ExecutionStatus executionStatus) {
        defaultPersister.updateStatus(executionId, executionStatus);

        if (execution != null && execution.getStatus() == CANCELED) {
          jobExecution.setExitStatus(ExitStatus.STOPPED);
        }
      }
    }, execution, executionStatus, wasSuccessful(jobExecution, execution));
  }

  @Override
  public void afterExecution(Persister persister,
                             Execution execution,
                             ExecutionStatus executionStatus,
                             boolean wasSuccessful) {
    executionListener.afterExecution(persister, execution, executionStatus, wasSuccessful);
  }

  @Override
  public int getOrder() {
    return executionListener.getOrder();
  }

  private static Execution execution(ExecutionRepository executionRepository, JobExecution jobExecution) {
    try {
      if (jobExecution.getJobParameters().getString("pipeline") != null) {
        return executionRepository.retrievePipeline(jobExecution.getJobParameters().getString("pipeline"));
      }
      return executionRepository.retrieveOrchestration(jobExecution.getJobParameters().getString("orchestration"));
    } catch (ExecutionNotFoundException ignored) {
      return null;
    }
  }

  /**
   * Determines if the step was a success (from an Orca perspective). Note that
   * even if the Orca task failed we'll get a `stepExecution.status` of
   * `COMPLETED` as the error was handled.
   */
  private static boolean wasSuccessful(JobExecution jobExecution, Execution currentExecution) {
    return jobExecution.getExitStatus().getExitCode().equals(ExitStatus.COMPLETED.getExitCode()) || currentExecution.getStatus().isSuccessful();
  }
}
