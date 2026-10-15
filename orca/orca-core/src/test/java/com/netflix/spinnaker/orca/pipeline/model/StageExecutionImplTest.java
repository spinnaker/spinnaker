/*
 * Copyright 2026 DoorDash, Inc.
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

package com.netflix.spinnaker.orca.pipeline.model;

import static org.assertj.core.api.Assertions.assertThat;

import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionType;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class StageExecutionImplTest {

  @Test
  void constructorExtractsAdditionalMetricTagsFromContext() {
    // given
    PipelineExecutionImpl execution =
        new PipelineExecutionImpl(ExecutionType.PIPELINE, "test-application");
    Map<String, Object> context = new HashMap<>();
    context.put("refId", "1");
    context.put("additionalMetricTags", Map.of("tag1", "value1", "tag2", "value2"));
    context.put("someOtherField", "someValue");

    // when
    StageExecutionImpl stage = new StageExecutionImpl(execution, "testStage", context);

    // then
    assertThat(stage.getAdditionalMetricTags())
        .isNotNull()
        .containsEntry("tag1", "value1")
        .containsEntry("tag2", "value2");

    // additionalMetricTags should be removed from context
    assertThat(stage.getContext()).doesNotContainKey("additionalMetricTags");

    // other fields should remain in context
    assertThat(stage.getContext()).containsEntry("someOtherField", "someValue");

    // refId should also be extracted
    assertThat(stage.getRefId()).isEqualTo("1");
    assertThat(stage.getContext()).doesNotContainKey("refId");
  }

  @Test
  void constructorHandlesNullAdditionalMetricTags() {
    // given
    PipelineExecutionImpl execution =
        new PipelineExecutionImpl(ExecutionType.PIPELINE, "test-application");
    Map<String, Object> context = new HashMap<>();
    context.put("refId", "1");
    context.put("someOtherField", "someValue");

    // when
    StageExecutionImpl stage = new StageExecutionImpl(execution, "testStage", context);

    // then
    assertThat(stage.getAdditionalMetricTags()).isNull();
    assertThat(stage.getContext()).containsEntry("someOtherField", "someValue");
  }

  @Test
  void constructorHandlesEmptyAdditionalMetricTags() {
    // given
    PipelineExecutionImpl execution =
        new PipelineExecutionImpl(ExecutionType.PIPELINE, "test-application");
    Map<String, Object> context = new HashMap<>();
    context.put("refId", "1");
    context.put("additionalMetricTags", Map.of());
    context.put("someOtherField", "someValue");

    // when
    StageExecutionImpl stage = new StageExecutionImpl(execution, "testStage", context);

    // then
    assertThat(stage.getAdditionalMetricTags()).isNotNull().isEmpty();
    assertThat(stage.getContext()).doesNotContainKey("additionalMetricTags");
    assertThat(stage.getContext()).containsEntry("someOtherField", "someValue");
  }
}
