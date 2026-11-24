/*
 * Copyright 2026 Harness, Inc.
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

package com.netflix.spinnaker.gate.controllers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.netflix.spinnaker.fiat.shared.FiatPermissionEvaluator;
import com.netflix.spinnaker.gate.GateBootAuthIntegrationTest;
import com.netflix.spinnaker.gate.Main;
import com.netflix.spinnaker.gate.services.PipelineService;
import java.net.http.HttpResponse;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.Authentication;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Runs requests through Spring method security to verify that triggering a pipeline requires
 * EXECUTE on the application, not just READ.
 */
@SpringBootTest(
    classes = {Main.class, PipelineController.class},
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PipelineControllerAuthorizationTest extends GateBootAuthIntegrationTest {

  @MockitoBean FiatPermissionEvaluator fiatPermissionEvaluator;

  /** Mocked by GateBootAuthIntegrationTest */
  @Autowired PipelineService pipelineService;

  @Test
  void invokePipelineConfigIsRejectedWithoutExecute() throws Exception {
    when(fiatPermissionEvaluator.hasPermission(
            any(Authentication.class), eq("my-app"), eq("APPLICATION"), eq("READ")))
        .thenReturn(true);
    when(fiatPermissionEvaluator.hasPermission(
            any(Authentication.class), eq("my-app"), eq("APPLICATION"), eq("EXECUTE")))
        .thenReturn(false);

    HttpResponse<String> response = callGateWithPath("/pipelines/my-app/my-pipeline", "POST");

    assertThat(response.statusCode()).isEqualTo(403);
    verify(pipelineService, never()).trigger(any(), any(), any());
  }

  @Test
  void invokePipelineConfigIsAllowedWithExecute() throws Exception {
    when(fiatPermissionEvaluator.hasPermission(
            any(Authentication.class), eq("my-app"), eq("APPLICATION"), eq("EXECUTE")))
        .thenReturn(true);
    when(pipelineService.trigger(eq("my-app"), eq("my-pipeline"), any()))
        .thenReturn(Map.of("ref", "/pipelines/01ABC"));

    HttpResponse<String> response = callGateWithPath("/pipelines/my-app/my-pipeline", "POST");

    assertThat(response.statusCode()).isEqualTo(202);
  }

  @Test
  @SuppressWarnings("unchecked")
  void invokePipelineConfigIgnoresCallerSuppliedUser() throws Exception {
    when(fiatPermissionEvaluator.hasPermission(
            any(Authentication.class), eq("my-app"), eq("APPLICATION"), eq("EXECUTE")))
        .thenReturn(true);
    when(pipelineService.trigger(eq("my-app"), eq("my-pipeline"), any()))
        .thenReturn(Map.of("ref", "/pipelines/01ABC"));

    HttpResponse<String> response =
        callGateWithPath("/pipelines/my-app/my-pipeline", "POST", "{\"user\": \"some-admin\"}");

    assertThat(response.statusCode()).isEqualTo(202);
    ArgumentCaptor<Map<String, Object>> trigger = ArgumentCaptor.forClass(Map.class);
    verify(pipelineService).trigger(eq("my-app"), eq("my-pipeline"), trigger.capture());
    assertThat(trigger.getValue()).containsEntry("user", "testuser");
  }

  @Test
  void invokePipelineConfigViaEchoIsRejectedWithoutExecute() throws Exception {
    when(fiatPermissionEvaluator.hasPermission(
            any(Authentication.class), eq("my-app"), eq("APPLICATION"), eq("READ")))
        .thenReturn(true);
    when(fiatPermissionEvaluator.hasPermission(
            any(Authentication.class), eq("my-app"), eq("APPLICATION"), eq("EXECUTE")))
        .thenReturn(false);

    HttpResponse<String> response = callGateWithPath("/pipelines/v2/my-app/my-pipeline", "POST");

    assertThat(response.statusCode()).isEqualTo(403);
    verify(pipelineService, never()).triggerViaEcho(any(), any(), any());
  }

  @Test
  void invokePipelineConfigViaEchoIsAllowedWithExecute() throws Exception {
    when(fiatPermissionEvaluator.hasPermission(
            any(Authentication.class), eq("my-app"), eq("APPLICATION"), eq("EXECUTE")))
        .thenReturn(true);
    when(pipelineService.triggerViaEcho(eq("my-app"), eq("my-pipeline"), any()))
        .thenReturn(Map.of("ref", "/pipelines/01ABC"));

    HttpResponse<String> response = callGateWithPath("/pipelines/v2/my-app/my-pipeline", "POST");

    assertThat(response.statusCode()).isEqualTo(202);
    assertThat(response.body()).contains("/pipelines/01ABC");
  }
}
