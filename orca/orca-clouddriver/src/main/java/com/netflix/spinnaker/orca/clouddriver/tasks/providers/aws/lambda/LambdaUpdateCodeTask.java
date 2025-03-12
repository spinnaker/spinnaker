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
import com.netflix.spinnaker.orca.api.pipeline.models.TaskExecution;
import com.netflix.spinnaker.orca.clouddriver.config.CloudDriverConfigurationProperties;
import com.netflix.spinnaker.orca.clouddriver.pipeline.providers.aws.lambda.LambdaStageConstants;
import com.netflix.spinnaker.orca.clouddriver.tasks.providers.aws.lambda.model.LambdaCloudDriverResponse;
import com.netflix.spinnaker.orca.clouddriver.tasks.providers.aws.lambda.model.input.LambdaUpdateCodeInput;
import com.netflix.spinnaker.orca.clouddriver.tasks.providers.aws.lambda.model.output.LambdaCloudOperationOutput;
import com.netflix.spinnaker.orca.clouddriver.utils.LambdaCloudDriverUtils;
import com.netflix.spinnaker.orca.pipeline.model.StageContext;
import java.util.List;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class LambdaUpdateCodeTask implements LambdaStageBaseTask {
  private static final Logger logger = LoggerFactory.getLogger(LambdaUpdateCodeTask.class);
  private static final String CLOUDDRIVER_UPDATE_CODE_PATH = "/aws/ops/updateLambdaFunctionCode";

  @Autowired CloudDriverConfigurationProperties props;
  private String cloudDriverUrl;

  @Autowired private LambdaCloudDriverUtils utils;

  @Nonnull
  @Override
  public TaskResult execute(@Nonnull StageExecution stage) {
    logger.debug("Executing LambdaUpdateCodeTask...");
    cloudDriverUrl = props.getCloudDriverBaseUrl();
    prepareTask(stage);

    StageContext stageContext = (StageContext) stage.getContext();
    boolean justCreated =
        stageContext.containsKey(LambdaStageConstants.lambaCreatedKey)
            && (Boolean)
                stageContext.getOrDefault(LambdaStageConstants.lambaCreatedKey, Boolean.FALSE);
    logger.debug("justCreated GetCurrent:" + justCreated);
    if (justCreated) {
      return taskComplete(stage);
    }
    LambdaCloudOperationOutput output = updateLambdaCode(stage);
    addCloudOperationToContext(stage, output, LambdaStageConstants.updateCodeUrlKey);
    addToTaskContext(stage, LambdaStageConstants.lambaCodeUpdatedKey, Boolean.TRUE);
    return taskComplete(stage);
  }

  private LambdaCloudOperationOutput updateLambdaCode(StageExecution stage) {
    LambdaUpdateCodeInput inp = utils.getInput(stage, LambdaUpdateCodeInput.class);
    List<TaskExecution> taskExecutions = stage.getTasks();
    boolean deferPublish =
        taskExecutions.stream()
            .anyMatch(
                t ->
                    t.getName().equals("lambdaPublishVersionTask")
                        && t.getStatus().equals(ExecutionStatus.NOT_STARTED));

    inp.setPublish(inp.isPublish() && !deferPublish);
    logger.debug("Publish flag for UpdateCodeTask is set to " + (inp.isPublish() && !deferPublish));

    inp.setAppName(stage.getExecution().getApplication());
    inp.setCredentials(inp.getAccount());
    String endPoint = cloudDriverUrl + CLOUDDRIVER_UPDATE_CODE_PATH;
    String rawString = utils.asString(inp);
    LambdaCloudDriverResponse respObj = utils.postToCloudDriver(endPoint, rawString);
    String url = cloudDriverUrl + respObj.getResourceUri();
    logger.debug("Posted to cloudDriver for updateLambdaCode: " + url);
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
