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

package com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.strategies.lambda;

import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution;
import com.netflix.spinnaker.orca.clouddriver.config.CloudDriverConfigurationProperties;
import com.netflix.spinnaker.orca.clouddriver.tasks.providers.aws.lambda.model.*;
import com.netflix.spinnaker.orca.clouddriver.tasks.providers.aws.lambda.model.input.LambdaBlueGreenStrategyInput;
import com.netflix.spinnaker.orca.clouddriver.tasks.providers.aws.lambda.model.input.LambdaTrafficUpdateInput;
import com.netflix.spinnaker.orca.clouddriver.tasks.providers.aws.lambda.model.output.LambdaCloudOperationOutput;
import com.netflix.spinnaker.orca.clouddriver.tasks.providers.aws.lambda.model.output.LambdaDeploymentStrategyOutput;
import com.netflix.spinnaker.orca.clouddriver.tasks.providers.aws.lambda.model.output.LambdaInvokeFunctionOutput;
import com.netflix.spinnaker.orca.clouddriver.utils.LambdaCloudDriverUtils;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class LambdaBlueGreenDeploymentStrategy
    extends BaseLambdaDeploymentStrategy<LambdaBlueGreenStrategyInput> {
  private static final Logger logger =
      LoggerFactory.getLogger(LambdaBlueGreenDeploymentStrategy.class);

  @Autowired private LambdaCloudDriverUtils utils;

  @Autowired CloudDriverConfigurationProperties props;

  static String CLOUDDRIVER_INVOKE_LAMBDA_FUNCTION_PATH = "/aws/ops/invokeLambdaFunction";

  @Override
  public LambdaDeploymentStrategyOutput deploy(LambdaBlueGreenStrategyInput inp) {
    LambdaInvokeFunctionOutput invokeOutput = invokeLambdaFunction(inp);
    Pair<Boolean, String> results = verifyResults(inp, invokeOutput);
    if (results.getLeft()) {
      return updateLambdaToLatest(inp);
    } else {
      LambdaCloudOperationOutput cloudOperationOutput =
          LambdaCloudOperationOutput.builder().build();
      LambdaDeploymentStrategyOutput deploymentStrategyOutput =
          LambdaDeploymentStrategyOutput.builder().build();
      deploymentStrategyOutput.setSucceeded(false);
      deploymentStrategyOutput.setErrorMessage(results.getRight());
      deploymentStrategyOutput.setOutput(cloudOperationOutput);
      logger.error("BlueGreen Deployment failed: " + results.getRight());
      return deploymentStrategyOutput;
    }
  }

  private Pair<Boolean, String> verifyResults(
      LambdaBlueGreenStrategyInput inp, LambdaInvokeFunctionOutput output) {
    int timeout = inp.getTimeout() * 1000;
    String url = output.getUrl();
    int sleepTime = 10000;

    LambdaCloudDriverTaskResults taskResult = null;
    boolean done = false;
    while (timeout > 0) {
      taskResult = utils.verifyStatus(url);
      if (taskResult.getStatus().isCompleted()) {
        done = true;
        break;
      }
      try {
        utils.await();
        timeout -= sleepTime;
      } catch (Throwable e) {
        logger.error("Error waiting for blue green test to complete");
        continue;
      }
    }

    if (!done) return Pair.of(Boolean.FALSE, "Lambda Invocation did not finish on time");

    if (taskResult.getStatus().isFailed()) {
      return Pair.of(Boolean.FALSE, "Lambda Invocation returned failure");
    }

    LambdaCloudDriverInvokeOperationResults invokeResponse = utils.getLambdaInvokeResults(url);
    String expected =
        utils.getPipelinesArtifactContent(inp.getOutputArtifact()).replaceAll("[\\n\\t ]", "");
    String actual = null;
    if (invokeResponse != null) {
      if (invokeResponse.getBody() != null) {
        actual = invokeResponse.getBody().replaceAll("[\\n\\t ]", "");
      } else if (invokeResponse.getResponseString() != null) {
        actual = invokeResponse.getResponseString().replaceAll("[\\n\\t ]", "");
      }
    }
    boolean comparison = ObjectUtils.defaultIfNull(expected, "").equals(actual);
    if (!comparison) {
      String err =
          String.format(
              "BlueGreenDeployment failed: Comparison failed. expected : [%s], actual : [%s]",
              expected, actual);
      logger.error("Response string: " + invokeResponse.getResponseString());
      String errMsg = String.format("%s \n %s", err, invokeResponse.getErrorMessage());
      logger.error("Log results: " + invokeResponse.getInvokeResult().getLogResult());
      logger.error(err);
      return Pair.of(Boolean.FALSE, errMsg);
    }
    return Pair.of(Boolean.TRUE, "");
  }

  private LambdaDeploymentStrategyOutput updateLambdaToLatest(LambdaBlueGreenStrategyInput inp) {
    inp.setWeightToMinorFunctionVersion(0.0);
    inp.setMajorFunctionVersion(inp.getLatestVersionQualifier());
    inp.setMinorFunctionVersion(null);
    String cloudDriverUrl = props.getCloudDriverBaseUrl();
    Map<String, Object> outputMap = new HashMap<String, Object>();
    outputMap.put("deployment:majorVersionDeployed", inp.getMajorFunctionVersion());
    outputMap.put("deployment:aliasDeployed", inp.getAliasName());
    outputMap.put("deployment:strategyUsed", "BlueGreenDeploymentStrategy");
    LambdaCloudOperationOutput out = postToCloudDriver(inp, cloudDriverUrl, utils);
    out.setOutputMap(outputMap);
    LambdaDeploymentStrategyOutput deployOutput = LambdaDeploymentStrategyOutput.builder().build();
    deployOutput.setSucceeded(true);
    deployOutput.setOutput(out);
    return deployOutput;
  }

  @Override
  public LambdaBlueGreenStrategyInput setupInput(StageExecution stage) {
    LambdaTrafficUpdateInput aliasInp = utils.getInput(stage, LambdaTrafficUpdateInput.class);
    LambdaBlueGreenStrategyInput blueGreenInput =
        utils.getInput(stage, LambdaBlueGreenStrategyInput.class);
    aliasInp.setAppName(stage.getExecution().getApplication());

    blueGreenInput.setCredentials(aliasInp.getAccount());
    blueGreenInput.setAppName(stage.getExecution().getApplication());

    blueGreenInput.setPayloadArtifact(aliasInp.getPayloadArtifact().getArtifact());
    blueGreenInput.setOutputArtifact(aliasInp.getOutputArtifact().getArtifact());

    LambdaDefinition lf = null;
    lf = utils.retrieveLambdaFromCache(stage, true);

    String qual = utils.getCanonicalVersion(lf, "$LATEST", "", 1);
    blueGreenInput.setQualifier(qual);
    String latestVersion = this.getVersion(stage, "$LATEST", "");
    blueGreenInput.setLatestVersionQualifier(latestVersion);

    return blueGreenInput;
  }

  private LambdaInvokeFunctionOutput invokeLambdaFunction(LambdaBlueGreenStrategyInput ldi) {
    // ldi.setPayload(utils.getPipelinesArtifactContent(ldi.getPayloadArtifact()));
    String cloudDriverUrl = props.getCloudDriverBaseUrl();
    String endPoint = cloudDriverUrl + CLOUDDRIVER_INVOKE_LAMBDA_FUNCTION_PATH;
    String rawString = utils.asString(ldi);
    LambdaCloudDriverResponse respObj = utils.postToCloudDriver(endPoint, rawString);
    String url = cloudDriverUrl + respObj.getResourceUri();
    logger.debug("Posted to cloudDriver for blueGreenDeployment: " + url);
    LambdaInvokeFunctionOutput ldso = LambdaInvokeFunctionOutput.builder().url(url).build();
    return ldso;
  }

  @Override
  public LambdaCloudDriverUtils getUtils() {
    return utils;
  }
}
