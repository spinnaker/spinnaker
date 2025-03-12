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
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution;
import com.netflix.spinnaker.orca.clouddriver.config.CloudDriverConfigurationProperties;
import com.netflix.spinnaker.orca.clouddriver.tasks.providers.aws.lambda.model.LambdaDefinition;
import com.netflix.spinnaker.orca.clouddriver.utils.LambdaCloudDriverUtils;
import java.time.Duration;
import javax.annotation.Nonnull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class LambdaWaitToStabilizeTask implements LambdaStageBaseTask {
  private static final Logger logger = LoggerFactory.getLogger(LambdaWaitToStabilizeTask.class);

  final String PENDING_STATE = "Pending";
  final String ACTIVE_STATE = "Active";
  final String FUNCTION_CREATING = "Creating";

  @Autowired CloudDriverConfigurationProperties props;

  @Autowired private LambdaCloudDriverUtils utils;

  @Nonnull
  @Override
  public TaskResult execute(@Nonnull StageExecution stage) {
    logger.debug("Executing LambdaWaitToStabilizeTask...");
    return waitForStableState(stage);
  }

  private TaskResult waitForStableState(@Nonnull StageExecution stage) {
    LambdaDefinition lf;
    int counter = 0;
    while (true) {
      lf = utils.retrieveLambdaFromCache(stage, true);
      if (lf != null && lf.getState() != null) {
        logger.info(
            String.format(
                "%s lambda state from the cache %s", lf.getFunctionName(), lf.getState()));
        if (lf.getState().equals(PENDING_STATE)
            && lf.getStateReasonCode() != null
            && lf.getStateReasonCode().equals(FUNCTION_CREATING)) {
          utils.await(Duration.ofSeconds(30).toMillis());
          continue;
        }
        if (lf.getState().equals(ACTIVE_STATE)) {
          logger.info(lf.getFunctionName() + " is active");
          return taskComplete(stage);
        }
      } else {
        logger.info(
            "waiting for up to 10 minutes for it to show up in the cache... requires a full cache refresh cycle");
        utils.await(Duration.ofMinutes(1).toMillis());
        if (++counter > 10) break;
      }
    }
    return this.formErrorTaskResult(
        stage,
        String.format(
            "Failed to stabilize function with state: %s and reason: %s",
            lf != null && lf.getState() != null ? lf.getState() : "Unknown",
            lf != null && lf.getStateReason() != null ? lf.getStateReason() : "Unknown reason"));
  }
}
