/*
 * Copyright 2020 Netflix, Inc.
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

package com.netflix.spinnaker.orca.clouddriver.tasks.job;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.collect.ImmutableMap;
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionType;
import com.netflix.spinnaker.orca.pipeline.model.PipelineExecutionImpl;
import com.netflix.spinnaker.orca.pipeline.model.StageExecutionImpl;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;

@RunWith(JUnitPlatform.class)
public final class WaitOnJobCompletionTest {
  @Test
  void jobTimeoutSpecifiedByRunJobTask() {
    Duration duration = Duration.ofMinutes(10);

    WaitOnJobCompletion task = new WaitOnJobCompletion();
    StageExecutionImpl myStage =
        createStageWithContext(ImmutableMap.of("jobRuntimeLimit", duration.toString()));
    assertThat(task.getDynamicTimeout(myStage))
        .isEqualTo((duration.plus(WaitOnJobCompletion.getPROVIDER_PADDING())).toMillis());

    StageExecutionImpl myStageInvalid =
        createStageWithContext(ImmutableMap.of("jobRuntimeLimit", "garbage"));
    assertThat(task.getDynamicTimeout(myStageInvalid)).isEqualTo(task.getTimeout());
  }

  private StageExecutionImpl createStageWithContext(Map<String, ?> context) {
    return new StageExecutionImpl(
        new PipelineExecutionImpl(ExecutionType.PIPELINE, "test"), "test", new HashMap<>(context));
  }
}
