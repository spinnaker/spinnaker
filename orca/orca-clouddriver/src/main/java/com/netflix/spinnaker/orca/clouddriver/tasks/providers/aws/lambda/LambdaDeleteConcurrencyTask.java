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

import com.netflix.spinnaker.orca.api.pipeline.TaskResult;
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus;
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution;
import com.netflix.spinnaker.orca.clouddriver.config.CloudDriverConfigurationProperties;
import com.netflix.spinnaker.orca.clouddriver.pipeline.providers.aws.lambda.LambdaStageConstants;
import com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.strategies.lambda.LambdaDeploymentStrategyEnum;
import com.netflix.spinnaker.orca.clouddriver.tasks.providers.aws.lambda.model.LambdaCloudDriverResponse;
import com.netflix.spinnaker.orca.clouddriver.tasks.providers.aws.lambda.model.input.LambdaConcurrencyInput;
import com.netflix.spinnaker.orca.clouddriver.tasks.providers.aws.lambda.model.output.LambdaCloudOperationOutput;
import com.netflix.spinnaker.orca.clouddriver.utils.LambdaCloudDriverUtils;
import java.util.Optional;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class LambdaDeleteConcurrencyTask implements LambdaStageBaseTask {
  private static final Logger logger = LoggerFactory.getLogger(LambdaDeleteConcurrencyTask.class);

  private static final String CLOUDDRIVER_DELETE_PROVISIONED_CONCURRENCY_PATH =
      "/aws/ops/deleteLambdaProvisionedConcurrency";
  private static final String CLOUDDRIVER_DELETE_RESERVED_CONCURRENCY_PATH =
      "/aws/ops/deleteLambdaReservedConcurrency";

  @Autowired CloudDriverConfigurationProperties props;

  @Autowired private LambdaCloudDriverUtils utils;
  private String cloudDriverUrl;

  @Nonnull
  @Override
  public TaskResult execute(@Nonnull StageExecution stage) {
    logger.debug("Executing LambdaDeleteConcurrencyTask...");
    cloudDriverUrl = props.getCloudDriverBaseUrl();
    prepareTask(stage);
    LambdaConcurrencyInput inp = utils.getInput(stage, LambdaConcurrencyInput.class);
    inp.setAppName(stage.getExecution().getApplication());

    LambdaCloudOperationOutput output;
    if (stage.getType().equals("Aws.LambdaDeploymentStage")
        && inp.getReservedConcurrentExecutions() == null) {
      output = deleteReservedConcurrency(inp);
    } else if (stage.getType().equals("Aws.LambdaTrafficRoutingStage")
        && Optional.ofNullable(inp.getProvisionedConcurrentExecutions()).orElse(0) == 0
        && !LambdaDeploymentStrategyEnum.$WEIGHTED
            .toString()
            .equals(stage.getContext().get("deploymentStrategy"))) {
      output = deleteProvisionedConcurrency(inp);
    } else {
      addToOutput(
          stage, "LambdaDeleteConcurrencyTask", "Lambda delete concurrency : nothing to delete");
      return taskComplete(stage);
    }
    addCloudOperationToContext(stage, output, LambdaStageConstants.deleteConcurrencyUrlKey);
    return taskComplete(stage);
  }

  private LambdaCloudOperationOutput deleteProvisionedConcurrency(LambdaConcurrencyInput inp) {
    inp.setQualifier(inp.getAliasName());
    String rawString = utils.asString(inp);
    String endPoint = cloudDriverUrl + CLOUDDRIVER_DELETE_PROVISIONED_CONCURRENCY_PATH;
    LambdaCloudDriverResponse respObj = utils.postToCloudDriver(endPoint, rawString);
    String url = cloudDriverUrl + respObj.getResourceUri();
    logger.debug("Posted to cloudDriver for deleteProvisionedConcurrency: " + url);
    return LambdaCloudOperationOutput.builder().resourceId(respObj.getId()).url(url).build();
  }

  private LambdaCloudOperationOutput deleteReservedConcurrency(LambdaConcurrencyInput inp) {
    String rawString = utils.asString(inp);
    String endPoint = cloudDriverUrl + CLOUDDRIVER_DELETE_RESERVED_CONCURRENCY_PATH;
    LambdaCloudDriverResponse respObj = utils.postToCloudDriver(endPoint, rawString);
    String url = cloudDriverUrl + respObj.getResourceUri();
    logger.debug("Posted to cloudDriver for deleteReservedConcurrency: " + url);
    return LambdaCloudOperationOutput.builder().resourceId(respObj.getId()).url(url).build();
  }

  @Nullable
  @Override
  public TaskResult onTimeout(@Nonnull StageExecution stage) {
    return TaskResult.builder(ExecutionStatus.SKIPPED).build();
  }

  @Override
  public void onCancel(@Nonnull StageExecution stage) {}
}
