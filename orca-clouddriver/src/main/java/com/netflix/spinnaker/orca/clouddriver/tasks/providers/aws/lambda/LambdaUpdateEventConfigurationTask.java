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

import com.amazonaws.services.lambda.model.EventSourceMappingConfiguration;
import com.netflix.spinnaker.orca.api.pipeline.TaskResult;
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus;
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution;
import com.netflix.spinnaker.orca.clouddriver.config.CloudDriverConfigurationProperties;
import com.netflix.spinnaker.orca.clouddriver.pipeline.providers.aws.lambda.LambdaStageConstants;
import com.netflix.spinnaker.orca.clouddriver.tasks.providers.aws.lambda.model.*;
import com.netflix.spinnaker.orca.clouddriver.tasks.providers.aws.lambda.model.input.LambdaDeleteEventTaskInput;
import com.netflix.spinnaker.orca.clouddriver.tasks.providers.aws.lambda.model.input.LambdaUpdateEventConfigurationTaskInput;
import com.netflix.spinnaker.orca.clouddriver.tasks.providers.aws.lambda.model.output.LambdaCloudOperationOutput;
import com.netflix.spinnaker.orca.clouddriver.tasks.providers.aws.lambda.model.output.LambdaUpdateEventConfigurationTaskOutput;
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
public class LambdaUpdateEventConfigurationTask implements LambdaStageBaseTask {
  private static final Logger logger =
      LoggerFactory.getLogger(LambdaUpdateEventConfigurationTask.class);
  private static final String CLOUDDRIVER_UPDATE_EVENT_CONFIGURATION_LAMBDA_PATH =
      "/aws/ops/upsertLambdaFunctionEventMapping";
  private static final String CLOUDDRIVER_DELETE_EVENT_CONFIGURATION_LAMBDA_PATH =
      "/aws/ops/deleteLambdaFunctionEventMapping";
  private static final String DYNAMO_EVENT_PREFIX = "arn:aws:dynamodb:";
  private static final String KINESIS_EVENT_PREFIX = "arn:aws:kinesis";

  String cloudDriverUrl;

  @Autowired CloudDriverConfigurationProperties props;

  @Autowired LambdaCloudDriverUtils utils;

  private static final String DEFAULT_STARTING_POSITION = "LATEST";

  @Nonnull
  @Override
  public TaskResult execute(@Nonnull StageExecution stage) {
    logger.debug("Executing LambdaUpdateEventConfigurationTask");
    cloudDriverUrl = props.getCloudDriverBaseUrl();
    LambdaUpdateEventConfigurationTaskInput taskInput =
        utils.getInput(stage, LambdaUpdateEventConfigurationTaskInput.class);
    taskInput.setAppName(stage.getExecution().getApplication());
    Boolean justCreated =
        (Boolean) stage.getContext().getOrDefault(LambdaStageConstants.lambaCreatedKey, false);
    LambdaDefinition lf = utils.retrieveLambdaFromCache(stage, justCreated);
    if (lf == null) {
      return formErrorTaskResult(stage, "Could not find lambda to update event config for");
    }
    String functionArn = lf.getFunctionArn();
    if (StringUtils.isNotNullOrEmpty(taskInput.getAliasName())) {
      logger.debug("LambdaUpdateEventConfigurationTask for alias");
      functionArn = String.format("%s:%s", lf.getFunctionArn(), taskInput.getAliasName());
      taskInput.setQualifier(taskInput.getAliasName());
    }
    return updateEventsForLambdaFunction(taskInput, lf, functionArn);
  }

  private TaskResult updateEventsForLambdaFunction(
      LambdaUpdateEventConfigurationTaskInput taskInput, LambdaDefinition lf, String functionArn) {
    if (taskInput.getTriggerArns() == null || taskInput.getTriggerArns().size() == 0) {
      deleteAllExistingEvents(taskInput, lf, functionArn);
      Map<String, Object> context = new HashMap<>();
      return TaskResult.builder(ExecutionStatus.SUCCEEDED).context(context).build();
    }
    deleteRemovedAndChangedEvents(taskInput, lf, functionArn);
    LambdaUpdateEventConfigurationTaskOutput ldso =
        updateEventConfiguration(taskInput, lf, functionArn);
    Map<String, Object> context = buildContextOutput(ldso);
    return TaskResult.builder(ExecutionStatus.SUCCEEDED).context(context).build();
  }

  /**
   * New configuration has zero events. So find all existing events in the lambda cache and delete
   * them all.
   *
   * @param taskInput
   * @param lf
   */
  private void deleteAllExistingEvents(
      LambdaUpdateEventConfigurationTaskInput taskInput, LambdaDefinition lf, String targetArn) {
    List<String> eventArnList = getExistingEvents(lf, targetArn);
    eventArnList.forEach(eventArn -> deleteEvent(eventArn, taskInput, lf, targetArn));
  }

  /**
   * For each eventTriggerArn that already exists at backend: delete from backend if it does not
   * exist in input
   *
   * @param taskInput
   * @param lf
   */
  private LambdaUpdateEventConfigurationTaskOutput deleteRemovedAndChangedEvents(
      LambdaUpdateEventConfigurationTaskInput taskInput, LambdaDefinition lf, String targetArn) {
    LambdaUpdateEventConfigurationTaskOutput ans =
        LambdaUpdateEventConfigurationTaskOutput.builder().build();
    ans.setEventOutputs(new ArrayList<>());
    List<String> eventArnList = getExistingEvents(lf, targetArn);
    // Does not deal with change in batch size(s)
    eventArnList.stream()
        .filter(x -> !taskInput.getTriggerArns().contains(x))
        .forEach(eventArn -> deleteEvent(eventArn, taskInput, lf, targetArn));
    return ans;
  }

  List<String> getExistingEvents(LambdaDefinition lf, String targetArn) {
    List<EventSourceMappingConfiguration> esmList = lf.getEventSourceMappings();
    if (esmList == null) {
      return new ArrayList<>();
    }
    return esmList.stream()
        .filter(y -> y.getFunctionArn().equals(targetArn))
        .map(EventSourceMappingConfiguration::getEventSourceArn)
        .filter(StringUtils::isNotNullOrEmpty)
        .collect(Collectors.toList());
  }

  List<Pair<String, Integer>> getExistingEventsDetails(LambdaDefinition lf) {
    List<EventSourceMappingConfiguration> esmList = lf.getEventSourceMappings();
    if (esmList == null) {
      return new ArrayList<>();
    }
    return esmList.stream()
        .filter(x -> StringUtils.isNotNullOrEmpty(x.getEventSourceArn()))
        .map(x -> Pair.of(x.getEventSourceArn(), x.getBatchSize()))
        .collect(Collectors.toList());
  }

  private void deleteEvent(
      String eventArn,
      LambdaUpdateEventConfigurationTaskInput ti,
      LambdaDefinition lgo,
      String aliasOrFunctionArn) {
    logger.debug("To be deleted: " + eventArn);
    List<EventSourceMappingConfiguration> esmList = lgo.getEventSourceMappings();
    Optional<EventSourceMappingConfiguration> oo =
        esmList.stream()
            .filter(
                x ->
                    x.getEventSourceArn().equals(eventArn)
                        && x.getFunctionArn().equals(aliasOrFunctionArn))
            .findFirst();
    if (oo.isEmpty()) {
      return;
    }
    EventSourceMappingConfiguration toDelete = oo.get();
    LambdaDeleteEventTaskInput inp =
        LambdaDeleteEventTaskInput.builder()
            .account(ti.getAccount())
            .credentials(ti.getCredentials())
            .functionName(ti.getFunctionName())
            .eventSourceArn(toDelete.getEventSourceArn())
            .uuid(toDelete.getUUID())
            .region(ti.getRegion())
            .build();
    if (StringUtils.isNotNullOrEmpty(ti.getAliasName())) {
      ti.setQualifier(ti.getAliasName());
    }
    inp.setUuid(toDelete.getUUID());

    deleteLambdaEventConfig(inp);
  }

  private LambdaUpdateEventConfigurationTaskOutput updateEventConfiguration(
      LambdaUpdateEventConfigurationTaskInput taskInput, LambdaDefinition lf, String targetArn) {
    LambdaUpdateEventConfigurationTaskOutput ans =
        LambdaUpdateEventConfigurationTaskOutput.builder().build();
    ans.setEventOutputs(new ArrayList<>());
    taskInput.setCredentials(taskInput.getAccount());
    String endPoint = cloudDriverUrl + CLOUDDRIVER_UPDATE_EVENT_CONFIGURATION_LAMBDA_PATH;

    taskInput
        .getTriggerArns()
        .forEach(
            curr -> {
              LambdaEventConfigurationDescription singleEvent = formEventObject(curr, taskInput);
              String rawString = utils.asString(singleEvent);
              LambdaCloudDriverResponse respObj = utils.postToCloudDriver(endPoint, rawString);
              String url = cloudDriverUrl + respObj.getResourceUri();
              logger.debug("Posted to cloudDriver for updateEventConfiguration: " + url);
              LambdaCloudOperationOutput z =
                  LambdaCloudOperationOutput.builder()
                      .url(url)
                      .resourceId(respObj.getResourceUri())
                      .build();
              ans.getEventOutputs().add(z);
            });

    return ans;
  }

  private LambdaEventConfigurationDescription formEventObject(
      String curr, LambdaUpdateEventConfigurationTaskInput taskInput) {
    LambdaEventConfigurationDescription singleEvent =
        LambdaEventConfigurationDescription.builder()
            .eventSourceArn(curr)
            .batchsize(taskInput.getBatchsize())
            .enabled(true)
            .maxBatchingWindowSecs(taskInput.getMaxBatchingWindowSecs())
            .account(taskInput.getAccount())
            .credentials(taskInput.getCredentials())
            .appName(taskInput.getAppName())
            .region(taskInput.getRegion())
            .functionName(taskInput.getFunctionName())
            .qualifier(taskInput.getQualifier())
            .build();
    if (curr.startsWith(DYNAMO_EVENT_PREFIX) || curr.startsWith(KINESIS_EVENT_PREFIX)) {
      if (StringUtils.isNullOrEmpty(taskInput.getStartingPosition())) {
        taskInput.setStartingPosition(DEFAULT_STARTING_POSITION);
      }

      if (taskInput.getBisectBatchOnError() != null) {
        singleEvent.setBisectBatchOnError(taskInput.getBisectBatchOnError());
      }

      singleEvent.setDestinationConfig(formDestinationConfig(taskInput));

      if (taskInput.getMaxRecordAgeSecs() != null) {
        singleEvent.setMaxRecordAgeSecs(taskInput.getMaxRecordAgeSecs());
      }

      if (taskInput.getMaxRetryAttempts() != null) {
        singleEvent.setMaxRetryAttempts(taskInput.getMaxRetryAttempts());
      }

      if (taskInput.getParallelizationFactor() != null) {
        singleEvent.setParallelizationFactor(taskInput.getParallelizationFactor());
      }

      singleEvent.setStartingPosition(taskInput.getStartingPosition());

      if (taskInput.getTumblingWindowSecs() != null && taskInput.getTumblingWindowSecs() != -1) {
        singleEvent.setTumblingWindowSecs(taskInput.getTumblingWindowSecs());
      }
    }
    return singleEvent;
  }

  private Map<String, Object> formDestinationConfig(
      LambdaUpdateEventConfigurationTaskInput taskInput) {
    Map<String, Object> destinationConfig = null;

    if (taskInput.getDestinationConfig() != null) {
      String onFailureArn = taskInput.getDestinationConfig().get("onFailureArn");
      String onSuccessArn = taskInput.getDestinationConfig().get("onSuccessArn");

      if (StringUtils.isNotNullOrEmpty(onFailureArn)
          || StringUtils.isNotNullOrEmpty(onSuccessArn)) {
        destinationConfig = new HashMap<>();

        if (StringUtils.isNotNullOrEmpty(onFailureArn)) {
          Map<String, String> onFailure = new HashMap<>();
          onFailure.put("destination", onFailureArn);
          destinationConfig.put("onFailure", onFailure);
        }

        if (StringUtils.isNotNullOrEmpty(onSuccessArn)) {
          Map<String, String> onSuccess = new HashMap<>();
          onSuccess.put("destination", onSuccessArn);
          destinationConfig.put("onSuccess", onSuccess);
        }
      }
    }

    return destinationConfig;
  }

  private LambdaCloudOperationOutput deleteLambdaEventConfig(LambdaDeleteEventTaskInput inp) {
    inp.setCredentials(inp.getAccount());
    String endPoint = cloudDriverUrl + CLOUDDRIVER_DELETE_EVENT_CONFIGURATION_LAMBDA_PATH;
    String rawString = utils.asString(inp);
    LambdaCloudDriverResponse respObj = utils.postToCloudDriver(endPoint, rawString);
    String url = cloudDriverUrl + respObj.getResourceUri();
    logger.debug("Posted to cloudDriver for deleteLambdaEventConfig: " + url);
    return LambdaCloudOperationOutput.builder().url(url).build();
  }

  /** Fill up with values required for next task */
  private Map<String, Object> buildContextOutput(LambdaUpdateEventConfigurationTaskOutput ldso) {
    List<String> urlList = new ArrayList<>();
    ldso.getEventOutputs().forEach(x -> urlList.add(x.getUrl()));
    Map<String, Object> context = new HashMap<>();
    context.put(LambdaStageConstants.eventTaskKey, urlList);
    return context;
  }
}
