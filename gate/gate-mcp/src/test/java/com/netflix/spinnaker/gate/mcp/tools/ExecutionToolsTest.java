/*
 * Copyright 2026 McIntosh.farm
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

package com.netflix.spinnaker.gate.mcp.tools;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.netflix.spinnaker.gate.services.internal.OrcaService;
import com.netflix.spinnaker.gate.services.internal.OrcaServiceSelector;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import retrofit2.mock.Calls;

@ExtendWith(MockitoExtension.class)
class ExecutionToolsTest {

  @Mock private OrcaServiceSelector orcaServiceSelector;
  @Mock private OrcaService orcaService;

  private ExecutionTools executionTools;

  @BeforeEach
  void setUp() {
    executionTools = new ExecutionTools(orcaServiceSelector);
    when(orcaServiceSelector.select()).thenReturn(orcaService);
  }

  @Test
  void searchExecutionsDefaultsIncludeDeletedPipelinesToFalse() {
    List<Map<String, Object>> executions = List.of(Map.of("id", "exec-1"));
    when(orcaService.searchForPipelineExecutionsByTrigger(
            "myapp", null, null, null, null, 0L, Long.MAX_VALUE, null, 0, 10, false, false, false))
        .thenReturn(Calls.response(executions));

    List<Map<String, Object>> result =
        executionTools.searchExecutions("myapp", null, null, null, null, null, null, null, null);

    assertThat(result).isEqualTo(executions);
  }

  @Test
  void searchExecutionsPassesIncludeDeletedPipelinesThrough() {
    List<Map<String, Object>> executions = List.of(Map.of("id", "exec-2"));
    when(orcaService.searchForPipelineExecutionsByTrigger(
            "myapp", null, null, null, null, 0L, Long.MAX_VALUE, null, 0, 10, false, false, true))
        .thenReturn(Calls.response(executions));

    List<Map<String, Object>> result =
        executionTools.searchExecutions("myapp", null, null, null, null, null, null, null, true);

    assertThat(result).isEqualTo(executions);
    verify(orcaService)
        .searchForPipelineExecutionsByTrigger(
            "myapp", null, null, null, null, 0L, Long.MAX_VALUE, null, 0, 10, false, false, true);
  }
}
