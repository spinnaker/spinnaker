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
import com.netflix.spinnaker.orca.clouddriver.tasks.providers.aws.lambda.model.LambdaDefinition;
import com.netflix.spinnaker.orca.clouddriver.utils.LambdaCloudDriverUtils;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class LambdaOutputTask implements LambdaStageBaseTask {
  private static final Logger logger = LoggerFactory.getLogger(LambdaOutputTask.class);

  @Autowired CloudDriverConfigurationProperties props;

  @Autowired private LambdaCloudDriverUtils utils;

  @Nonnull
  @Override
  public TaskResult execute(@Nonnull StageExecution stage) {
    logger.debug("Executing LambdaOutputTask...");
    prepareTask(stage);
    Boolean justCreated =
        (Boolean) stage.getContext().getOrDefault(LambdaStageConstants.lambaCreatedKey, false);
    LambdaDefinition lf = utils.retrieveLambdaFromCache(stage, justCreated);
    if (lf != null) {
      addToOutput(stage, LambdaStageConstants.revisionIdKey, lf.getRevisionId());
      addToOutput(stage, LambdaStageConstants.lambdaObjectKey, lf);
    }
    copyContextToOutput(stage);
    return taskComplete(stage);
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
    ss.add("lambdaOutputTask");
    return ss;
  }
}
