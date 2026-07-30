/*
 * Copyright 2017 Netflix, Inc.
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

package com.netflix.spinnaker.orca.listeners;

import static com.netflix.spinnaker.orca.api.pipeline.models.ExecutionType.ORCHESTRATION;

import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus;
import com.netflix.spinnaker.orca.api.pipeline.models.PipelineExecution;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.concurrent.TimeUnit;

public class MetricsExecutionListener implements ExecutionListener {
  private final MeterRegistry registry;

  public MetricsExecutionListener(MeterRegistry registry) {
    this.registry = registry;
  }

  @Override
  public void beforeExecution(Persister persister, PipelineExecution execution) {
    if (execution.getApplication() == null) {
      return;
    }

    registry
        .counter(
            "executions.started",
            "executionType",
            execution.getType().toString(),
            "application",
            execution.getApplication().toLowerCase())
        .increment();
  }

  @Override
  public void afterExecution(
      Persister persister,
      PipelineExecution execution,
      ExecutionStatus executionStatus,
      boolean wasSuccessful) {
    if (execution.getType() != ORCHESTRATION) {
      // not concerned with pipelines right now (pipelines can have wait stages / manual judgments
      // which skew execution time)
      return;
    }

    if (execution.getApplication() == null
        || execution.getStartTime() == null
        || execution.getEndTime() == null) {
      // should normally have all attributes but a guard just in case
      return;
    }

    registry
        .timer(
            "executions.totalTime",
            "executionType",
            execution.getType().toString(),
            "successful",
            Boolean.valueOf(wasSuccessful).toString(),
            "application",
            execution.getApplication().toLowerCase())
        .record(execution.getEndTime() - execution.getStartTime(), TimeUnit.MILLISECONDS);
  }
}
