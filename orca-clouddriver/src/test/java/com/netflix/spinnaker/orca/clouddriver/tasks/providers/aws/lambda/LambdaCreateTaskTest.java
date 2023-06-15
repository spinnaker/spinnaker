/*
 * Copyright 2021 Armory, LLC
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

import com.github.tomakehurst.wiremock.WireMockServer;
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus;
import com.netflix.spinnaker.orca.api.pipeline.models.PipelineExecution;
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution;
import com.netflix.spinnaker.orca.clouddriver.config.CloudDriverConfigurationProperties;
import com.netflix.spinnaker.orca.clouddriver.tasks.providers.aws.lambda.model.LambdaCloudDriverResponse;
import com.netflix.spinnaker.orca.clouddriver.tasks.providers.aws.lambda.model.LambdaDefinition;
import com.netflix.spinnaker.orca.clouddriver.tasks.providers.aws.lambda.model.input.LambdaDeploymentInput;
import com.netflix.spinnaker.orca.clouddriver.tasks.providers.aws.lambda.model.input.LambdaGetInput;
import com.netflix.spinnaker.orca.clouddriver.utils.LambdaCloudDriverUtils;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import ru.lanwen.wiremock.ext.WiremockResolver;
import ru.lanwen.wiremock.ext.WiremockUriResolver;

@ExtendWith({WiremockResolver.class, WiremockUriResolver.class})
public class LambdaCreateTaskTest {

  WireMockServer wireMockServer;

  @InjectMocks private LambdaCreateTask lambdaCreateTask;

  @Mock private CloudDriverConfigurationProperties propsMock;

  @Mock private LambdaCloudDriverUtils lambdaCloudDriverUtilsMock;

  @Mock private StageExecution stageExecution;

  @Mock private PipelineExecution pipelineExecution;

  @BeforeEach
  void init(
      @WiremockResolver.Wiremock WireMockServer wireMockServer,
      @WiremockUriResolver.WiremockUri String uri) {
    this.wireMockServer = wireMockServer;
    MockitoAnnotations.initMocks(this);
    Mockito.when(propsMock.getCloudDriverBaseUrl()).thenReturn(uri);
    pipelineExecution.setApplication("lambdaApp");
    Mockito.when(stageExecution.getExecution()).thenReturn(pipelineExecution);
    Mockito.when(stageExecution.getContext()).thenReturn(new HashMap<>());
    LambdaDeploymentInput ldi =
        LambdaDeploymentInput.builder().functionName("functionName").build();
    Mockito.when(
            lambdaCloudDriverUtilsMock.validateUpsertLambdaInput(Mockito.any(), Mockito.anyList()))
        .thenReturn(true);
    Mockito.when(lambdaCloudDriverUtilsMock.getInput(stageExecution, LambdaDeploymentInput.class))
        .thenReturn(ldi);
    LambdaGetInput lgi = LambdaGetInput.builder().build();
    Mockito.when(lambdaCloudDriverUtilsMock.getInput(stageExecution, LambdaGetInput.class))
        .thenReturn(lgi);
    Mockito.when(lambdaCloudDriverUtilsMock.asString(ldi)).thenReturn("asString");
  }

  @Test
  public void execute_InsertLambda_SUCCEEDED() {
    Mockito.when(lambdaCloudDriverUtilsMock.retrieveLambdaFromCache(stageExecution, false))
        .thenReturn(null);
    LambdaCloudDriverResponse lambdaCloudDriverResponse =
        LambdaCloudDriverResponse.builder().resourceUri("/resourceUri").build();
    Mockito.when(lambdaCloudDriverUtilsMock.postToCloudDriver(Mockito.any(), Mockito.any()))
        .thenReturn(lambdaCloudDriverResponse);
    assertEquals(ExecutionStatus.SUCCEEDED, lambdaCreateTask.execute(stageExecution).getStatus());
  }

  @Test
  public void execute_UpsertLambda_SUCCEEDED() {
    Map<String, String> revisions = new HashMap<>();
    revisions.put("revision", "1");
    LambdaDefinition ld = LambdaDefinition.builder().revisions(revisions).build();
    Mockito.when(lambdaCloudDriverUtilsMock.retrieveLambdaFromCache(stageExecution, false))
        .thenReturn(ld);
    assertEquals(ExecutionStatus.SUCCEEDED, lambdaCreateTask.execute(stageExecution).getStatus());
  }

  @Test
  public void execute_InvalidUpsertLambdaInput_TERMINAL() {
    Map<String, String> revisions = new HashMap<>();
    revisions.put("revision", "1");
    LambdaDefinition ld = LambdaDefinition.builder().revisions(revisions).build();
    Mockito.when(lambdaCloudDriverUtilsMock.retrieveLambdaFromCache(stageExecution, false))
        .thenReturn(ld);
    Mockito.when(
            lambdaCloudDriverUtilsMock.validateUpsertLambdaInput(Mockito.any(), Mockito.anyList()))
        .thenReturn(false);
    assertEquals(ExecutionStatus.TERMINAL, lambdaCreateTask.execute(stageExecution).getStatus());
  }
}
