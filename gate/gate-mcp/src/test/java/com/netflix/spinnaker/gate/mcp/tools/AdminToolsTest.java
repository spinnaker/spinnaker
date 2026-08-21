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

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.netflix.spinnaker.gate.mcp.config.McpServerProperties;
import com.netflix.spinnaker.gate.mcp.support.McpAccessGuard;
import com.netflix.spinnaker.gate.mcp.support.McpReadOnlyModeException;
import com.netflix.spinnaker.gate.services.internal.OrcaService;
import com.netflix.spinnaker.gate.services.internal.OrcaServiceSelector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import retrofit2.mock.Calls;

@ExtendWith(MockitoExtension.class)
class AdminToolsTest {

  @Mock private OrcaServiceSelector orcaServiceSelector;
  @Mock private OrcaService orcaService;

  private AdminTools adminTools;
  private McpServerProperties properties;

  @BeforeEach
  void setUp() {
    properties = new McpServerProperties();
    properties.setReadOnly(false);
    adminTools = new AdminTools(orcaServiceSelector, new McpAccessGuard(properties));
  }

  @Test
  void cancelZombiePipelineForceCancelsWithDefaultExecutionType() {
    when(orcaServiceSelector.select()).thenReturn(orcaService);
    when(orcaService.forceCancelPipeline(eq("exec-1"), eq("PIPELINE"), any()))
        .thenReturn(Calls.response((Void) null));

    adminTools.cancelZombiePipeline("exec-1", null);

    verify(orcaService).forceCancelPipeline(eq("exec-1"), eq("PIPELINE"), any());
  }

  @Test
  void cancelZombiePipelinePassesThroughExplicitExecutionType() {
    when(orcaServiceSelector.select()).thenReturn(orcaService);
    when(orcaService.forceCancelPipeline(eq("exec-1"), eq("ORCHESTRATION"), any()))
        .thenReturn(Calls.response((Void) null));

    adminTools.cancelZombiePipeline("exec-1", "ORCHESTRATION");

    verify(orcaService).forceCancelPipeline(eq("exec-1"), eq("ORCHESTRATION"), any());
  }

  @Test
  void cancelZombiePipelineRejectedInReadOnlyMode() {
    properties.setReadOnly(true);

    assertThatThrownBy(() -> adminTools.cancelZombiePipeline("exec-1", null))
        .isInstanceOf(McpReadOnlyModeException.class);
  }
}
