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
import com.netflix.spinnaker.orca.clouddriver.tasks.providers.aws.lambda.model.LambdaCloudDriverTaskResults;
import com.netflix.spinnaker.orca.clouddriver.utils.LambdaCloudDriverUtils;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class LambdaVerificationTask implements LambdaStageBaseTask {
  private static final Logger logger = LoggerFactory.getLogger(LambdaVerificationTask.class);

  @Autowired CloudDriverConfigurationProperties props;

  @Autowired private LambdaCloudDriverUtils utils;

  @Nonnull
  @Override
  public TaskResult execute(@Nonnull StageExecution stage) {
    logger.debug("Executing lambdaVerificationTask...");
    prepareTask(stage);
    try {
      return doVerify(stage);
    } catch (Throwable e) {
      logger.error("Exception verifying task", e);
      logException(stage, e);
      addExceptionToOutput(stage, e);
      return formErrorTaskResult(stage, "Exception during task verification");
    }
  }

  private TaskResult doVerify(StageExecution stage) {
    Map<String, Object> stageContext = stage.getContext();
    List<String> urlKeyList = LambdaStageConstants.allUrlKeys;
    List<String> urlList =
        urlKeyList.stream()
            .filter(x -> stageContext.get(x) != null)
            .map(singleUrlKey -> (String) stageContext.get(singleUrlKey))
            .collect(Collectors.toList());

    if (null != stageContext.get(LambdaStageConstants.eventTaskKey))
      urlList.addAll((List<String>) stageContext.get(LambdaStageConstants.eventTaskKey));

    if (null != stageContext.get(LambdaStageConstants.aliasTaskKey))
      urlList.addAll((List<String>) stageContext.get(LambdaStageConstants.aliasTaskKey));

    List<LambdaCloudDriverTaskResults> listOfTaskResults =
        urlList.stream().map(url -> utils.verifyStatus(url)).collect(Collectors.toList());

    boolean anyRunning =
        listOfTaskResults.stream().anyMatch(taskResult -> !taskResult.getStatus().isCompleted());
    if (anyRunning) {
      return TaskResult.builder(ExecutionStatus.RUNNING).build();
    }

    // Clear the keys, now that the tasks are complete.
    urlKeyList.forEach(stageContext::remove);
    stageContext.remove(LambdaStageConstants.eventTaskKey);
    stageContext.remove(LambdaStageConstants.aliasTaskKey);

    boolean anyFailures =
        listOfTaskResults.stream().anyMatch(taskResult -> taskResult.getStatus().isFailed());

    if (!anyFailures) {
      copyContextToOutput(stage);
      listOfTaskResults.forEach(
          taskResult -> {
            if (taskResult.getResults() != null) {
              String arn = taskResult.getResults().getFunctionArn();
              if (arn != null) {
                addToOutput(stage, LambdaStageConstants.functionARNKey, arn);
                addToOutput(stage, LambdaStageConstants.resourceIdKey, arn);
                addToOutput(
                    stage,
                    LambdaStageConstants.functionNameKey,
                    taskResult.getResults().getFunctionName());
              }
              String version = taskResult.getResults().getVersion();
              if (version != null) {
                addToOutput(stage, LambdaStageConstants.versionIdKey, version);
              }
            }
          });
      copyContextToOutput(stage);
      return taskComplete(stage);
    }

    // Process failures:
    List<String> errorMessages =
        listOfTaskResults.stream()
            .map(op -> op.getErrors().getMessage())
            .collect(Collectors.toList());
    return formErrorListTaskResult(stage, errorMessages);
  }

  @Nullable
  @Override
  public TaskResult onTimeout(@Nonnull StageExecution stage) {
    return TaskResult.builder(ExecutionStatus.SKIPPED).build();
  }

  @Override
  public void onCancel(@Nonnull StageExecution stage) {}

  @Override
  public Collection<String> aliases() {
    List<String> ss = new ArrayList<>();
    ss.add("lambdaVerificationTask");
    return ss;
  }
}
