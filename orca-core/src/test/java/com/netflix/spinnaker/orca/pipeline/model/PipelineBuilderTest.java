/*
 * Copyright 2023 Salesforce, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.orca.pipeline.model;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.netflix.spinnaker.kork.common.Header;
import com.netflix.spinnaker.orca.api.pipeline.models.PipelineExecution;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

class PipelineBuilderTest {
  @BeforeEach
  public void setup() {
    MDC.clear();
  }

  @Test
  void withStagesChecksForNull() {
    PipelineBuilder pipelineBuilder = new PipelineBuilder("my-application");
    assertThrows(IllegalArgumentException.class, () -> pipelineBuilder.withStages(null));
  }

  @Test
  void withStagesChecksForType() {
    PipelineBuilder pipelineBuilder = new PipelineBuilder("my-application");
    Map<String, Object> stageWithoutType = new HashMap<>();
    stageWithoutType.put("name", "my-pipeline-stage");
    assertThrows(
        IllegalArgumentException.class,
        () -> pipelineBuilder.withStages(List.of(stageWithoutType)));
  }

  @Test
  void buildIncludesAllowedAccountsWhenTrue() {
    // given
    MDC.put(Header.USER.getHeader(), "SpinnakerUser");
    MDC.put(Header.ACCOUNTS.getHeader(), "Account1,Account2");

    // when
    PipelineBuilder pipelineBuilder =
        new PipelineBuilder("my-application").withIncludeAllowedAccounts(true);
    PipelineExecution execution = pipelineBuilder.build();

    // then
    assertThat(execution.getAuthentication().getAllowedAccounts())
        .isEqualTo(Set.of("Account1", "Account2"));
  }

  @Test
  void buildExcludesAllowedAccountsWhenFalse() {
    // given
    MDC.put(Header.USER.getHeader(), "SpinnakerUser");
    MDC.put(Header.ACCOUNTS.getHeader(), "Account1,Account2");

    // when
    PipelineBuilder pipelineBuilder =
        new PipelineBuilder("my-application").withIncludeAllowedAccounts(false);
    PipelineExecution execution = pipelineBuilder.build();

    // then
    assertThat(execution.getAuthentication().getAllowedAccounts()).isEqualTo(Set.of());
  }
}
