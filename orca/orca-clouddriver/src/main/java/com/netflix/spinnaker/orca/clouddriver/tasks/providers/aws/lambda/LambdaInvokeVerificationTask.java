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
import com.netflix.spinnaker.orca.clouddriver.tasks.providers.aws.lambda.model.LambdaCloudDriverInvokeOperationResults;
import com.netflix.spinnaker.orca.clouddriver.tasks.providers.aws.lambda.model.LambdaCloudDriverTaskResults;
import com.netflix.spinnaker.orca.clouddriver.tasks.providers.aws.lambda.model.input.LambdaInvokeStageInput;
import com.netflix.spinnaker.orca.clouddriver.utils.LambdaCloudDriverUtils;
import java.util.*;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import org.apache.commons.lang3.tuple.Pair;
import org.pf4j.util.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class LambdaInvokeVerificationTask implements LambdaStageBaseTask {

  private static final Logger logger = LoggerFactory.getLogger(LambdaInvokeVerificationTask.class);

  @Autowired CloudDriverConfigurationProperties props;

  @Autowired private LambdaCloudDriverUtils utils;

  @Nonnull
  @Override
  public TaskResult execute(@Nonnull StageExecution stage) {
    logger.debug("Executing LambdaInvokeVerificationTask...");
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

  public TaskResult doVerify(@Nonnull StageExecution stage) {
    prepareTask(stage);
    Map<String, Object> stageContext = stage.getContext();
    List<String> urlList = (List<String>) stageContext.get("urlList");
    if (urlList == null) {
      return taskComplete(stage);
    }
    List<Pair<String, LambdaCloudDriverTaskResults>> listOfTaskResults =
        urlList.stream()
            .map(url -> Pair.of(url, utils.verifyStatus(url)))
            .collect(Collectors.toList());

    boolean anyRunning =
        listOfTaskResults.stream()
            .anyMatch(taskResult -> !taskResult.getRight().getStatus().isCompleted());

    if (anyRunning) {
      return TaskResult.builder(ExecutionStatus.RUNNING).build();
    }

    boolean anyFailures =
        listOfTaskResults.stream().anyMatch(x -> x.getRight().getStatus().isFailed());

    List<String> allErrors = new ArrayList<>();
    if (anyFailures) {
      listOfTaskResults.forEach(
          op -> {
            if (op.getRight().getStatus().isFailed()) {
              List<String> allMessages = List.of(op.getRight().getErrors().getMessage());
              if ((allMessages != null) && allMessages.size() > 0) {
                allErrors.addAll(allMessages);
              }
            }
          });
    }

    LambdaInvokeStageInput ldi = utils.getInput(stage, LambdaInvokeStageInput.class);
    List<Map<String, Object>> invokeResultsList = new ArrayList<Map<String, Object>>();
    listOfTaskResults.forEach(
        op -> {
          Map<String, Object> invokeResults = null;
          if (op.getRight().getStatus().isFailed()) {
            List<String> allMessages = List.of(op.getRight().getErrors().getMessage());
            if ((allMessages != null) && allMessages.size() > 0) {
              invokeResults = new HashMap<>();
              invokeResults.put("errors", allMessages);
            }
          } else {
            invokeResults = this.verifyInvokeResults(op.getLeft(), ldi.getTimeout());
            if ((invokeResults.containsKey("errors"))
                && StringUtils.isNotNullOrEmpty((String) invokeResults.get("errors"))) {
              allErrors.add((String) invokeResults.get("errors"));
            }
          }
          invokeResultsList.add(invokeResults);
        });

    addToOutput(stage, "invokeResultsList", invokeResultsList);

    if (allErrors.size() > 0) {
      return formErrorListTaskResult(stage, allErrors);
    }
    return taskComplete(stage);
  }

  private Map<String, Object> verifyInvokeResults(String url, int seconds) {
    int timeout = seconds * 1000;
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
        logger.error("Error waiting for lambda invocation to complete");
      }
    }

    Map<String, Object> results = new HashMap<>();

    if (!done) {
      results.put("errors", "Lambda Invocation did not finish on time");
      return results;
    }

    if (taskResult.getStatus().isFailed()) {
      results.put("errors", "Lambda Invocation returned failure");
      return results;
    }

    LambdaCloudDriverInvokeOperationResults invokeResponse = utils.getLambdaInvokeResults(url);
    String actual = invokeResponse.getBody();
    results.put("body", actual);
    results.put("response", invokeResponse.getResponseString());
    results.put("errors", invokeResponse.getErrorMessage());
    results.put("logs", invokeResponse.getInvokeResult().getLogResult());
    return results;
  }

  @Override
  public Collection<String> aliases() {
    List<String> ss = new ArrayList<>();
    ss.add("lambdaInvokeVerificationTask");
    return ss;
  }
}
