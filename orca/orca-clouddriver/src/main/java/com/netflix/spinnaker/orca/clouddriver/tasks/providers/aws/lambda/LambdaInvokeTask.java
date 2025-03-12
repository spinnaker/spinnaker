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
import com.netflix.spinnaker.orca.clouddriver.tasks.providers.aws.lambda.model.LambdaCloudDriverResponse;
import com.netflix.spinnaker.orca.clouddriver.tasks.providers.aws.lambda.model.LambdaDefinition;
import com.netflix.spinnaker.orca.clouddriver.tasks.providers.aws.lambda.model.input.LambdaInvokeStageInput;
import com.netflix.spinnaker.orca.clouddriver.tasks.providers.aws.lambda.model.input.LambdaTrafficUpdateInput;
import com.netflix.spinnaker.orca.clouddriver.utils.LambdaCloudDriverUtils;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import javax.annotation.Nonnull;
import org.pf4j.util.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class LambdaInvokeTask implements LambdaStageBaseTask {
  private static final Logger logger = LoggerFactory.getLogger(LambdaInvokeTask.class);

  @Autowired CloudDriverConfigurationProperties props;

  @Autowired private LambdaCloudDriverUtils utils;

  static String CLOUDDRIVER_INVOKE_LAMBDA_FUNCTION_PATH = "/aws/ops/invokeLambdaFunction";

  @Nonnull
  @Override
  public TaskResult execute(@Nonnull StageExecution stage) {
    logger.debug("Executing LambdaInvokeTask...");
    prepareTask(stage);
    LambdaDefinition lf = utils.retrieveLambdaFromCache(stage, true);
    if (lf == null) {
      logger.error("Could not find lambda");
      return this.formErrorTaskResult(stage, "No such lambda found.");
    }
    LambdaInvokeStageInput ldi = utils.getInput(stage, LambdaInvokeStageInput.class);
    LambdaTrafficUpdateInput tui = utils.getInput(stage, LambdaTrafficUpdateInput.class);
    ldi.setPayloadArtifact(tui.getPayloadArtifact().getArtifact());
    ldi.setQualifier(
        StringUtils.isNullOrEmpty(ldi.getAliasName()) ? "$LATEST" : ldi.getAliasName());
    ldi.setAppName(stage.getExecution().getApplication());
    ldi.setCredentials(ldi.getAccount());
    List<String> urlList = new ArrayList<>();
    for (int i = 0; i < ldi.getExecutionCount(); i++) {
      String url = this.invokeLambdaFunction(ldi);
      urlList.add(url);
    }
    addToTaskContext(stage, "urlList", urlList);
    return taskComplete(stage);
  }

  private String invokeLambdaFunction(LambdaInvokeStageInput ldi) {
    String cloudDriverUrl = props.getCloudDriverBaseUrl();
    String endPoint = cloudDriverUrl + CLOUDDRIVER_INVOKE_LAMBDA_FUNCTION_PATH;
    String rawString = utils.asString(ldi);
    LambdaCloudDriverResponse respObj = utils.postToCloudDriver(endPoint, rawString);
    String url = cloudDriverUrl + respObj.getResourceUri();
    logger.debug("Posted to cloudDriver for lambda invocation: " + url);
    return url;
  }

  @Override
  public Collection<String> aliases() {
    List<String> ss = new ArrayList<>();
    ss.add("lambdaInvokeTask");
    return ss;
  }
}
