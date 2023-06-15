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
import com.netflix.spinnaker.orca.clouddriver.tasks.providers.aws.lambda.model.LambdaCloudDriverResponse;
import com.netflix.spinnaker.orca.clouddriver.tasks.providers.aws.lambda.model.input.LambdaUpdateAliasesInput;
import com.netflix.spinnaker.orca.clouddriver.tasks.providers.aws.lambda.model.output.LambdaCloudOperationOutput;
import com.netflix.spinnaker.orca.clouddriver.utils.LambdaCloudDriverUtils;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.pf4j.util.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class LambdaUpdateAliasesTask implements LambdaStageBaseTask {
  private static final Logger logger = LoggerFactory.getLogger(LambdaUpdateAliasesTask.class);
  private static final String CLOUDDRIVER_UPDATE_ALIAS_PATH = "/aws/ops/upsertLambdaFunctionAlias";
  private static final String DEFAULT_ALIAS_DESCRIPTION = "Created via Spinnaker";
  private static final String LATEST_VERSION_STRING = "$LATEST";

  @Autowired CloudDriverConfigurationProperties props;
  private String cloudDriverUrl;

  @Autowired private LambdaCloudDriverUtils utils;

  @Nonnull
  @Override
  public TaskResult execute(@Nonnull StageExecution stage) {
    logger.debug("Executing LambdaUpdateAliasesTask...");
    cloudDriverUrl = props.getCloudDriverBaseUrl();
    prepareTask(stage);
    if (!shouldAddAliases(stage)) {
      addToOutput(stage, LambdaStageConstants.lambaAliasesUpdatedKey, Boolean.FALSE);
      return taskComplete(stage);
    }
    List<LambdaCloudOperationOutput> output = updateLambdaAliases(stage);
    buildContextOutput(stage, output);
    addToTaskContext(stage, LambdaStageConstants.lambaAliasesUpdatedKey, Boolean.TRUE);
    addToOutput(stage, LambdaStageConstants.lambaAliasesUpdatedKey, Boolean.TRUE);
    return taskComplete(stage);
  }

  /** Fill up with values required for next task */
  private void buildContextOutput(StageExecution stage, List<LambdaCloudOperationOutput> ldso) {
    List<String> urlList = new ArrayList<>();
    ldso.forEach(x -> urlList.add(x.getUrl()));
    addToTaskContext(stage, LambdaStageConstants.eventTaskKey, urlList);
  }

  private boolean shouldAddAliases(StageExecution stage) {
    return stage.getContext().containsKey("aliases");
  }

  private LambdaCloudOperationOutput updateSingleAlias(LambdaUpdateAliasesInput inp, String alias) {
    inp.setAliasDescription(DEFAULT_ALIAS_DESCRIPTION);
    inp.setAliasName(alias);
    inp.setMajorFunctionVersion(LATEST_VERSION_STRING);
    String endPoint = cloudDriverUrl + CLOUDDRIVER_UPDATE_ALIAS_PATH;
    String rawString = utils.asString(inp);
    LambdaCloudDriverResponse respObj = utils.postToCloudDriver(endPoint, rawString);
    String url = cloudDriverUrl + respObj.getResourceUri();
    logger.debug("Posted to cloudDriver for updateLambdaAliases: " + url);
    return LambdaCloudOperationOutput.builder().resourceId(respObj.getId()).url(url).build();
  }

  private List<LambdaCloudOperationOutput> updateLambdaAliases(StageExecution stage) {
    List<LambdaCloudOperationOutput> result = new ArrayList<>();
    List<String> aliases = (List<String>) stage.getContext().get("aliases");
    LambdaUpdateAliasesInput inp = utils.getInput(stage, LambdaUpdateAliasesInput.class);
    inp.setAppName(stage.getExecution().getApplication());
    inp.setCredentials(inp.getAccount());
    for (String alias : aliases) {
      if (StringUtils.isNullOrEmpty(alias)) continue;
      String formattedAlias = alias.trim();
      if (StringUtils.isNullOrEmpty(formattedAlias)) continue;
      LambdaCloudOperationOutput operationOutput = updateSingleAlias(inp, formattedAlias);
      result.add(operationOutput);
    }
    return result;
  }

  @Nullable
  @Override
  public TaskResult onTimeout(@Nonnull StageExecution stage) {
    return TaskResult.builder(ExecutionStatus.SKIPPED).build();
  }

  @Override
  public void onCancel(@Nonnull StageExecution stage) {}
}
