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

import com.amazonaws.services.lambda.model.AliasConfiguration;
import com.amazonaws.services.lambda.model.AliasRoutingConfiguration;
import com.netflix.spinnaker.orca.api.pipeline.TaskResult;
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus;
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution;
import com.netflix.spinnaker.orca.clouddriver.config.CloudDriverConfigurationProperties;
import com.netflix.spinnaker.orca.clouddriver.config.LambdaConfigurationProperties;
import com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.strategies.lambda.LambdaDeploymentStrategyEnum;
import com.netflix.spinnaker.orca.clouddriver.tasks.providers.aws.lambda.model.LambdaCloudDriverTaskResults;
import com.netflix.spinnaker.orca.clouddriver.tasks.providers.aws.lambda.model.LambdaDefinition;
import com.netflix.spinnaker.orca.clouddriver.tasks.providers.aws.lambda.model.input.LambdaTrafficUpdateInput;
import com.netflix.spinnaker.orca.clouddriver.utils.LambdaCloudDriverUtils;
import java.util.*;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nonnull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class LambdaTrafficUpdateVerificationTask implements LambdaStageBaseTask {

  private static final Logger logger =
      LoggerFactory.getLogger(LambdaTrafficUpdateVerificationTask.class);

  @Autowired CloudDriverConfigurationProperties props;

  @Autowired private LambdaCloudDriverUtils utils;

  @Autowired LambdaConfigurationProperties config;

  @Nonnull
  @Override
  public TaskResult execute(@Nonnull StageExecution stage) {
    prepareTask(stage);
    Map<String, Object> stageContext = stage.getContext();
    String url = (String) stageContext.get("url");
    if (url == null) {
      return formErrorTaskResult(stage, "No task url to verify");
    }

    LambdaCloudDriverTaskResults op = utils.verifyStatus(url);

    if (!op.getStatus().isCompleted()) {
      return TaskResult.builder(ExecutionStatus.RUNNING).build();
    }

    if (op.getStatus().isFailed()) {
      return formErrorTaskResult(stage, op.getErrors().getMessage());
    }

    if (!LambdaDeploymentStrategyEnum.$WEIGHTED
        .toString()
        .equals(stage.getContext().get("deploymentStrategy"))) {
      boolean invalid = validateWeights(stage);
      if (invalid) {
        formErrorTaskResult(
            stage,
            "Could not update weights in time - waited "
                + config.getCloudDriverRetrieveMaxValidateWeightsTimeSeconds()
                + " seconds");
        return TaskResult.builder(ExecutionStatus.TERMINAL).outputs(stage.getOutputs()).build();
      }
    }

    copyContextToOutput(stage);
    return taskComplete(stage);
  }

  /**
   * Waits for the weights of your lambda to be moved from 0% to 100% to the new version so alias -
   * provisioned concurrency can be updated
   *
   * @param stage The runtime execution state of a stage.
   * @return The boolean value if your alias-weights failed to be moved before max time specified in
   *     lambdaPluginConfig.cloudDriverRetrieveMaxValidateWeightsTime
   * @see <a
   *     href="https://github.com/spinnaker-plugins/aws-lambda-deployment-plugin-spinnaker/pull/119">PR-119</a>
   * @since 1.0.11
   */
  private boolean validateWeights(StageExecution stage) {
    utils.await(
        TimeUnit.SECONDS.toMillis(config.getCloudDriverRetrieveNewPublishedLambdaWaitSeconds()));
    AliasRoutingConfiguration weights = null;
    long startTime = System.currentTimeMillis();
    LambdaTrafficUpdateInput inp = utils.getInput(stage, LambdaTrafficUpdateInput.class);
    boolean status = false;
    do {
      utils.await(TimeUnit.SECONDS.toMillis(config.getCacheRefreshRetryWaitTime()));
      LambdaDefinition lf = utils.retrieveLambdaFromCache(stage, false);
      Optional<AliasConfiguration> aliasConfiguration =
          lf.getAliasConfigurations().stream()
              .filter(al -> al.getName().equals(inp.getAliasName()))
              .findFirst();

      if (aliasConfiguration.isPresent()) {
        Optional<AliasRoutingConfiguration> opt =
            Optional.ofNullable(aliasConfiguration.get().getRoutingConfig());
        weights = opt.orElse(null);
      }
      if ((System.currentTimeMillis() - startTime)
          > TimeUnit.SECONDS.toMillis(
              config.getCloudDriverRetrieveMaxValidateWeightsTimeSeconds())) {
        logger.warn(
            "alias weights did not update in {} seconds. waited {} seconds",
            config.getCloudDriverRetrieveMaxValidateWeightsTimeSeconds(),
            TimeUnit.MILLISECONDS.toSeconds((System.currentTimeMillis() - startTime)));
        status = true;
      }
    } while (null != weights && !status);
    return status;
  }
}
