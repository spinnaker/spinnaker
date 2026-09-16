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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.netflix.spinnaker.gate.mcp.config.McpServerProperties;
import com.netflix.spinnaker.gate.mcp.support.McpAccessGuard;
import com.netflix.spinnaker.gate.mcp.support.McpReadOnlyModeException;
import com.netflix.spinnaker.gate.services.internal.KayentaService;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import retrofit2.mock.Calls;

@ExtendWith(MockitoExtension.class)
class KayentaToolsTest {

  @Mock private KayentaService kayentaService;

  private KayentaTools kayentaTools;
  private McpServerProperties properties;

  @BeforeEach
  void setUp() {
    properties = new McpServerProperties();
    properties.setReadOnly(false);
    kayentaTools = new KayentaTools(kayentaService, new McpAccessGuard(properties));
  }

  @Test
  void saveCanaryConfigCreatesWhenIdAbsent() {
    Map<String, Object> config = Map.of("name", "my-canary");
    when(kayentaService.createCanaryConfig(eq(config), eq("acct")))
        .thenReturn(Calls.response(Map.of("id", "new-id")));

    Map<String, Object> result = kayentaTools.saveCanaryConfig(null, config, "acct");

    assertThat(result).containsEntry("id", "new-id");
    verify(kayentaService, never()).updateCanaryConfig(any(), anyMap(), any());
  }

  @Test
  void saveCanaryConfigUpdatesWhenIdPresent() {
    Map<String, Object> config = Map.of("name", "my-canary");
    when(kayentaService.updateCanaryConfig(eq("existing-id"), eq(config), eq("acct")))
        .thenReturn(Calls.response(Map.of("id", "existing-id")));

    Map<String, Object> result = kayentaTools.saveCanaryConfig("existing-id", config, "acct");

    assertThat(result).containsEntry("id", "existing-id");
  }

  @Test
  void saveCanaryConfigRejectedInReadOnlyMode() {
    properties.setReadOnly(true);
    Map<String, Object> config = Map.of("name", "my-canary");

    assertThatThrownBy(() -> kayentaTools.saveCanaryConfig(null, config, null))
        .isInstanceOf(McpReadOnlyModeException.class);
  }

  @Test
  void testCanaryMetricQueryBuildsQueryParameters() {
    when(kayentaService.queryMetrics(eq("prometheus"), anyMap()))
        .thenReturn(Calls.response(Map.of("result", "ok")));

    kayentaTools.testCanaryMetricQuery(
        "prometheus", "cpu", "node_cpu", "metricsAcct", "storageAcct", Map.of("project", "myproj"));

    ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
    verify(kayentaService).queryMetrics(eq("prometheus"), captor.capture());
    assertThat(captor.getValue())
        .containsEntry("metricSetName", "cpu")
        .containsEntry("metricName", "node_cpu")
        .containsEntry("metricsAccountName", "metricsAcct")
        .containsEntry("storageAccountName", "storageAcct")
        .containsEntry("project", "myproj");
  }

  @Test
  void initiateCanaryRejectedInReadOnlyMode() {
    properties.setReadOnly(true);

    assertThatThrownBy(
            () -> kayentaTools.initiateCanary("config-id", Map.of(), null, null, null, null, null))
        .isInstanceOf(McpReadOnlyModeException.class);
  }
}
