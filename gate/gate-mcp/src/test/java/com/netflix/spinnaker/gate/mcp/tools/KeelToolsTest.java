/*
 * Copyright 2026 Netflix, Inc.
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.gate.mcp.config.McpServerProperties;
import com.netflix.spinnaker.gate.mcp.support.McpAccessGuard;
import com.netflix.spinnaker.gate.mcp.support.McpReadOnlyModeException;
import com.netflix.spinnaker.gate.model.manageddelivery.DeliveryConfig;
import com.netflix.spinnaker.gate.model.manageddelivery.EnvironmentArtifactPin;
import com.netflix.spinnaker.gate.model.manageddelivery.EnvironmentArtifactVeto;
import com.netflix.spinnaker.gate.services.internal.KeelService;
import java.util.Map;
import okhttp3.ResponseBody;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import retrofit2.mock.Calls;

@ExtendWith(MockitoExtension.class)
class KeelToolsTest {

  @Mock private KeelService keelService;

  private KeelTools keelTools;
  private McpServerProperties properties;

  @BeforeEach
  void setUp() {
    properties = new McpServerProperties();
    properties.setReadOnly(false);
    keelTools = new KeelTools(keelService, new ObjectMapper(), new McpAccessGuard(properties));
  }

  @Test
  void saveDeliveryConfigConvertsMapAndSubmits() {
    Map<String, Object> manifest = Map.of("name", "my-config", "application", "myapp");
    when(keelService.upsertManifest(any(DeliveryConfig.class)))
        .thenReturn(Calls.response(new DeliveryConfig()));

    keelTools.saveDeliveryConfig(manifest);

    ArgumentCaptor<DeliveryConfig> captor = ArgumentCaptor.forClass(DeliveryConfig.class);
    verify(keelService).upsertManifest(captor.capture());
    assertThat(captor.getValue().getName()).isEqualTo("my-config");
    assertThat(captor.getValue().getApplication()).isEqualTo("myapp");
  }

  @Test
  void saveDeliveryConfigRejectedInReadOnlyMode() {
    properties.setReadOnly(true);

    assertThatThrownBy(() -> keelTools.saveDeliveryConfig(Map.of("name", "x")))
        .isInstanceOf(McpReadOnlyModeException.class);
  }

  @Test
  void validateDeliveryConfigDoesNotRequireWriteAccess() {
    properties.setReadOnly(true);
    when(keelService.validateManifest(any(DeliveryConfig.class)))
        .thenReturn(Calls.response(Map.of("valid", true)));

    Map<String, Object> result = keelTools.validateDeliveryConfig(Map.of("name", "x"));

    assertThat(result).containsEntry("valid", true);
  }

  @Test
  void pinArtifactVersionBuildsPinAndSubmits() {
    when(keelService.pin(eq("myapp"), any(EnvironmentArtifactPin.class)))
        .thenReturn(Calls.response(mock(ResponseBody.class)));

    keelTools.pinArtifactVersion(
        "myapp", "production", "my-artifact", "1.0.0", "someone", "reason");

    ArgumentCaptor<EnvironmentArtifactPin> captor =
        ArgumentCaptor.forClass(EnvironmentArtifactPin.class);
    verify(keelService).pin(eq("myapp"), captor.capture());
    assertThat(captor.getValue().getTargetEnvironment()).isEqualTo("production");
    assertThat(captor.getValue().getReference()).isEqualTo("my-artifact");
    assertThat(captor.getValue().getVersion()).isEqualTo("1.0.0");
    assertThat(captor.getValue().getPinnedBy()).isEqualTo("someone");
  }

  @Test
  void pinArtifactVersionRejectedInReadOnlyMode() {
    properties.setReadOnly(true);

    assertThatThrownBy(
            () -> keelTools.pinArtifactVersion("myapp", "production", "ref", "1.0.0", null, null))
        .isInstanceOf(McpReadOnlyModeException.class);
  }

  @Test
  void markArtifactVersionBadBuildsVetoAndSubmits() {
    when(keelService.markBad(eq("myapp"), any(EnvironmentArtifactVeto.class)))
        .thenReturn(Calls.response(mock(ResponseBody.class)));

    keelTools.markArtifactVersionBad("myapp", "production", "my-artifact", "1.0.0", "broken");

    ArgumentCaptor<EnvironmentArtifactVeto> captor =
        ArgumentCaptor.forClass(EnvironmentArtifactVeto.class);
    verify(keelService).markBad(eq("myapp"), captor.capture());
    assertThat(captor.getValue().getTargetEnvironment()).isEqualTo("production");
    assertThat(captor.getValue().getVersion()).isEqualTo("1.0.0");
    assertThat(captor.getValue().getComment()).isEqualTo("broken");
  }

  @Test
  void getManagedApplicationDefaultsEntitiesToResources() {
    when(keelService.getApplicationDetails(
            eq("myapp"), eq(false), eq(java.util.List.of("resources")), eq(null)))
        .thenReturn(Calls.response(Map.of("application", "myapp")));

    Map result = keelTools.getManagedApplication("myapp", null, null, null);

    assertThat(result).containsEntry("application", "myapp");
  }

  @Test
  void resumeManagedApplicationRejectedInReadOnlyMode() {
    properties.setReadOnly(true);

    assertThatThrownBy(() -> keelTools.resumeManagedApplication("myapp"))
        .isInstanceOf(McpReadOnlyModeException.class);
  }
}
