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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.netflix.spinnaker.gate.config.controllers.PipelineControllerConfigProperties;
import com.netflix.spinnaker.gate.services.PipelineService;
import com.netflix.spinnaker.gate.services.TaskService;
import com.netflix.spinnaker.gate.services.internal.Front50Service;
import com.netflix.spinnaker.kork.web.exceptions.InvalidRequestException;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import tools.jackson.databind.json.JsonMapper;

/**
 * The echo based trigger returns before Echo or Orca could report a missing required parameter, so
 * Gate checks first. Covers how the controller applies {@code
 * controller.pipeline.check-required-parameters-on-execution}.
 */
class PipelineControllerRequiredParametersTest {

  private final PipelineService pipelineService = mock(PipelineService.class);
  private final PipelineControllerConfigProperties properties =
      new PipelineControllerConfigProperties();
  private final PipelineController controller =
      new PipelineController(
          pipelineService,
          mock(TaskService.class),
          mock(Front50Service.class),
          JsonMapper.builder().build(),
          properties);

  @Test
  void checkIsEnabledByDefault() {
    assertThat(properties.isCheckRequiredParametersOnExecution()).isTrue();
  }

  @Test
  void missingRequiredParameterIsRejectedBeforeEcho() {
    doThrow(new InvalidRequestException("Required parameter(s) missing: param1"))
        .when(pipelineService)
        .validateRequiredParameters(eq("my-app"), eq("my-pipeline"), any());

    assertThatThrownBy(
            () -> controller.invokePipelineConfigViaEcho("my-app", "my-pipeline", Map.of()))
        .isInstanceOf(InvalidRequestException.class);

    verify(pipelineService, never()).triggerViaEcho(any(), any(), any());
  }

  @Test
  void validTriggerIsSentToEcho() {
    when(pipelineService.triggerViaEcho(eq("my-app"), eq("my-pipeline"), any()))
        .thenReturn(Map.of("ref", "/pipelines/01ABC"));

    ResponseEntity<?> response =
        (ResponseEntity<?>)
            controller.invokePipelineConfigViaEcho("my-app", "my-pipeline", Map.of());

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
    verify(pipelineService).validateRequiredParameters(eq("my-app"), eq("my-pipeline"), any());
  }

  @Test
  void pipelineConfigIsNotLookedUpWhenCheckIsDisabled() {
    properties.setCheckRequiredParametersOnExecution(false);
    when(pipelineService.triggerViaEcho(eq("my-app"), eq("my-pipeline"), any()))
        .thenReturn(Map.of("ref", "/pipelines/01ABC"));

    ResponseEntity<?> response =
        (ResponseEntity<?>)
            controller.invokePipelineConfigViaEcho("my-app", "my-pipeline", Map.of());

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
    verify(pipelineService, never()).validateRequiredParameters(any(), any(), any());
  }
}
