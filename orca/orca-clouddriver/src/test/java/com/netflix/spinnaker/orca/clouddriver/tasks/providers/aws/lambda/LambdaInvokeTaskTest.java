/*
 * Copyright 2026 Harness, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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

package com.netflix.spinnaker.orca.clouddriver.tasks.providers.aws.lambda;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus;
import com.netflix.spinnaker.orca.api.pipeline.models.PipelineExecution;
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution;
import com.netflix.spinnaker.orca.clouddriver.config.CloudDriverConfigurationProperties;
import com.netflix.spinnaker.orca.clouddriver.pipeline.providers.aws.lambda.LambdaStageConstants;
import com.netflix.spinnaker.orca.clouddriver.tasks.providers.aws.lambda.model.LambdaCloudDriverResponse;
import com.netflix.spinnaker.orca.clouddriver.tasks.providers.aws.lambda.model.LambdaDefinition;
import com.netflix.spinnaker.orca.clouddriver.tasks.providers.aws.lambda.model.LambdaHealthCheckArtifact;
import com.netflix.spinnaker.orca.clouddriver.tasks.providers.aws.lambda.model.LambdaPipelineArtifact;
import com.netflix.spinnaker.orca.clouddriver.tasks.providers.aws.lambda.model.input.LambdaInvokeStageInput;
import com.netflix.spinnaker.orca.clouddriver.tasks.providers.aws.lambda.model.input.LambdaTrafficUpdateInput;
import com.netflix.spinnaker.orca.clouddriver.utils.LambdaCloudDriverUtils;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LambdaInvokeTaskTest {

  @InjectMocks private LambdaInvokeTask lambdaInvokeTask;

  @Mock private CloudDriverConfigurationProperties propsMock;

  @Mock private LambdaCloudDriverUtils lambdaCloudDriverUtilsMock;

  @Mock private StageExecution stageExecution;

  @Mock private PipelineExecution pipelineExecution;

  @BeforeEach
  void init() {
    when(stageExecution.getExecution()).thenReturn(pipelineExecution);
    when(pipelineExecution.getApplication()).thenReturn("lambdaApp");
    when(stageExecution.getContext()).thenReturn(new HashMap<>());
  }

  @Test
  void execute_usesResolvedPayloadWhenPresent() {
    LambdaDefinition lambdaDefinition = LambdaDefinition.builder().build();
    when(lambdaCloudDriverUtilsMock.retrieveLambdaFromCache(stageExecution, true))
        .thenReturn(lambdaDefinition);

    LambdaInvokeStageInput ldi =
        LambdaInvokeStageInput.builder().functionName("fn").executionCount(1).build();
    when(lambdaCloudDriverUtilsMock.getInput(stageExecution, LambdaInvokeStageInput.class))
        .thenReturn(ldi);

    Map<String, Object> context = new HashMap<>();
    context.put(LambdaStageConstants.resolvedPayloadKey, "resolved-payload");
    when(stageExecution.getContext()).thenReturn(context);

    LambdaCloudDriverResponse response =
        LambdaCloudDriverResponse.builder().resourceUri("/resourceUri").build();
    when(lambdaCloudDriverUtilsMock.postToCloudDriver(any(), any())).thenReturn(response);
    when(propsMock.getCloudDriverBaseUrl()).thenReturn("http://clouddriver");

    assertEquals(ExecutionStatus.SUCCEEDED, lambdaInvokeTask.execute(stageExecution).getStatus());

    assertEquals("resolved-payload", ldi.getPayload());
    assertNull(ldi.getPayloadArtifact());
  }

  @Test
  void execute_fallsBackToPayloadArtifactWhenResolvedPayloadAbsent() {
    LambdaDefinition lambdaDefinition = LambdaDefinition.builder().build();
    when(lambdaCloudDriverUtilsMock.retrieveLambdaFromCache(stageExecution, true))
        .thenReturn(lambdaDefinition);

    LambdaInvokeStageInput ldi =
        LambdaInvokeStageInput.builder().functionName("fn").executionCount(1).build();
    when(lambdaCloudDriverUtilsMock.getInput(stageExecution, LambdaInvokeStageInput.class))
        .thenReturn(ldi);

    LambdaPipelineArtifact artifact =
        LambdaPipelineArtifact.builder()
            .type("s3/object")
            .reference("s3://bucket/payload.json")
            .build();
    LambdaTrafficUpdateInput tui =
        LambdaTrafficUpdateInput.builder()
            .payloadArtifact(LambdaHealthCheckArtifact.builder().artifact(artifact).build())
            .build();
    when(lambdaCloudDriverUtilsMock.getInput(stageExecution, LambdaTrafficUpdateInput.class))
        .thenReturn(tui);

    LambdaCloudDriverResponse response =
        LambdaCloudDriverResponse.builder().resourceUri("/resourceUri").build();
    when(lambdaCloudDriverUtilsMock.postToCloudDriver(any(), any())).thenReturn(response);
    when(propsMock.getCloudDriverBaseUrl()).thenReturn("http://clouddriver");

    assertEquals(ExecutionStatus.SUCCEEDED, lambdaInvokeTask.execute(stageExecution).getStatus());

    assertNull(ldi.getPayload());
    assertNotNull(ldi.getPayloadArtifact());
    assertEquals("s3://bucket/payload.json", ldi.getPayloadArtifact().getReference());
  }
}
