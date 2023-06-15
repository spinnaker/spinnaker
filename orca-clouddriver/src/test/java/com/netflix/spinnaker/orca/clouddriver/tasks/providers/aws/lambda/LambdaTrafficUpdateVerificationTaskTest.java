/*
 * Copyright 2018 Amazon.com, Inc. or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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

import com.amazonaws.services.lambda.model.AliasConfiguration;
import com.amazonaws.services.lambda.model.AliasRoutingConfiguration;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.google.common.collect.ImmutableList;
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus;
import com.netflix.spinnaker.orca.api.pipeline.models.PipelineExecution;
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution;
import com.netflix.spinnaker.orca.clouddriver.config.CloudDriverConfigurationProperties;
import com.netflix.spinnaker.orca.clouddriver.config.LambdaConfigurationProperties;
import com.netflix.spinnaker.orca.clouddriver.tasks.providers.aws.lambda.model.LambdaCloudDriverErrorObject;
import com.netflix.spinnaker.orca.clouddriver.tasks.providers.aws.lambda.model.LambdaCloudDriverResponse;
import com.netflix.spinnaker.orca.clouddriver.tasks.providers.aws.lambda.model.LambdaCloudDriverTaskResults;
import com.netflix.spinnaker.orca.clouddriver.tasks.providers.aws.lambda.model.LambdaDefinition;
import com.netflix.spinnaker.orca.clouddriver.tasks.providers.aws.lambda.model.input.LambdaTrafficUpdateInput;
import com.netflix.spinnaker.orca.clouddriver.tasks.providers.aws.lambda.model.output.LambdaVerificationStatusOutput;
import com.netflix.spinnaker.orca.clouddriver.utils.LambdaCloudDriverUtils;
import java.util.HashMap;
import java.util.List;
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
public class LambdaTrafficUpdateVerificationTaskTest {

  WireMockServer wireMockServer;

  @InjectMocks private LambdaTrafficUpdateVerificationTask lambdaTrafficUpdateVerificationTask;

  @Mock private CloudDriverConfigurationProperties propsMock;

  @Mock private LambdaCloudDriverUtils lambdaCloudDriverUtilsMock;

  @Mock private StageExecution stageExecution;

  @Mock private PipelineExecution pipelineExecution;

  @Mock private LambdaConfigurationProperties config;

  @BeforeEach
  void init(
      @WiremockResolver.Wiremock WireMockServer wireMockServer,
      @WiremockUriResolver.WiremockUri String uri) {
    this.wireMockServer = wireMockServer;
    MockitoAnnotations.openMocks(this);
    Mockito.when(propsMock.getCloudDriverBaseUrl()).thenReturn(uri);
    pipelineExecution.setApplication("lambdaApp");
    Mockito.when(stageExecution.getExecution()).thenReturn(pipelineExecution);
    Mockito.when(stageExecution.getContext()).thenReturn(new HashMap<>());
    Mockito.when(stageExecution.getOutputs()).thenReturn(new HashMap<>());
    stageExecution.getContext().put("url", "test");
    LambdaTrafficUpdateInput ldi =
        LambdaTrafficUpdateInput.builder()
            .functionName("functionName")
            .aliasName("develop")
            .deploymentStrategy("$BLUEGREEN")
            .build();
    Mockito.when(lambdaCloudDriverUtilsMock.postToCloudDriver(Mockito.any(), Mockito.any()))
        .thenReturn(LambdaCloudDriverResponse.builder().build());
    Mockito.when(
            lambdaCloudDriverUtilsMock.getInput(stageExecution, LambdaTrafficUpdateInput.class))
        .thenReturn(ldi);
  }

  @Test
  public void execute_UpdateVerification_verifyStatus_RUNNING() {
    LambdaCloudDriverTaskResults lambdaCloudDriverTaskResults =
        LambdaCloudDriverTaskResults.builder()
            .status(LambdaVerificationStatusOutput.builder().status("RUNNING").build())
            .build();
    Mockito.when(lambdaCloudDriverUtilsMock.verifyStatus(Mockito.any()))
        .thenReturn(lambdaCloudDriverTaskResults);

    assertEquals(
        ExecutionStatus.RUNNING,
        lambdaTrafficUpdateVerificationTask.execute(stageExecution).getStatus());
  }

  @Test
  public void execute_UpdateVerification_verifyStatus_ERROR() {
    String taskVerifyError = "clouddriver task not found error";
    LambdaCloudDriverTaskResults lambdaCloudDriverTaskResults =
        LambdaCloudDriverTaskResults.builder()
            .status(
                LambdaVerificationStatusOutput.builder()
                    .completed(true)
                    .failed(true)
                    .status("TERMINAL")
                    .build())
            .errors(LambdaCloudDriverErrorObject.builder().message(taskVerifyError).build())
            .build();
    Mockito.when(lambdaCloudDriverUtilsMock.verifyStatus(Mockito.any()))
        .thenReturn(lambdaCloudDriverTaskResults);

    assertEquals(
        ExecutionStatus.TERMINAL,
        lambdaTrafficUpdateVerificationTask.execute(stageExecution).getStatus());
    assertEquals(taskVerifyError, stageExecution.getOutputs().get("failureMessage"));
  }

  @Test
  public void execute_UpdateVerification_validateWeights() {
    // RoutingConfig() will be null ending while loop
    List<AliasConfiguration> aliasConfigurationList =
        ImmutableList.of(new AliasConfiguration().withName("develop"));

    LambdaCloudDriverTaskResults lambdaCloudDriverTaskResults =
        LambdaCloudDriverTaskResults.builder()
            .status(
                LambdaVerificationStatusOutput.builder().status("RUNNING").completed(true).build())
            .build();
    Mockito.when(lambdaCloudDriverUtilsMock.verifyStatus(Mockito.any()))
        .thenReturn(lambdaCloudDriverTaskResults);
    LambdaDefinition lambdaDefinition =
        LambdaDefinition.builder().aliasConfigurations(aliasConfigurationList).build();
    Mockito.when(config.getCloudDriverRetrieveNewPublishedLambdaWaitSeconds()).thenReturn(40);
    Mockito.when(config.getCacheRefreshRetryWaitTime()).thenReturn(15);
    Mockito.when(config.getCloudDriverRetrieveMaxValidateWeightsTimeSeconds()).thenReturn(240);
    Mockito.when(lambdaCloudDriverUtilsMock.retrieveLambdaFromCache(stageExecution, false))
        .thenReturn(lambdaDefinition);

    assertEquals(
        ExecutionStatus.SUCCEEDED,
        lambdaTrafficUpdateVerificationTask.execute(stageExecution).getStatus());
  }

  @Test
  public void execute_UpdateVerification_validateWeights_TERMINAL() {
    List<AliasConfiguration> aliasConfigurationList =
        ImmutableList.of(
            new AliasConfiguration()
                .withRoutingConfig(new AliasRoutingConfiguration())
                .withName("develop"));

    LambdaCloudDriverTaskResults lambdaCloudDriverTaskResults =
        LambdaCloudDriverTaskResults.builder()
            .status(
                LambdaVerificationStatusOutput.builder().status("RUNNING").completed(true).build())
            .build();
    Mockito.when(lambdaCloudDriverUtilsMock.verifyStatus(Mockito.any()))
        .thenReturn(lambdaCloudDriverTaskResults);
    LambdaDefinition lambdaDefinition =
        LambdaDefinition.builder().aliasConfigurations(aliasConfigurationList).build();
    Mockito.when(config.getCloudDriverRetrieveNewPublishedLambdaWaitSeconds()).thenReturn(1);
    Mockito.when(config.getCacheRefreshRetryWaitTime()).thenReturn(1);
    Mockito.when(config.getCloudDriverRetrieveMaxValidateWeightsTimeSeconds()).thenReturn(1);
    Mockito.when(lambdaCloudDriverUtilsMock.retrieveLambdaFromCache(stageExecution, false))
        .thenReturn(lambdaDefinition);

    assertEquals(
        ExecutionStatus.TERMINAL,
        lambdaTrafficUpdateVerificationTask.execute(stageExecution).getStatus());
    assertEquals(
        "Could not update weights in time - waited "
            + config.getCloudDriverRetrieveMaxValidateWeightsTimeSeconds()
            + " seconds",
        stageExecution.getOutputs().get("failureMessage"));
  }
}
