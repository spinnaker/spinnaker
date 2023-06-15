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
import com.netflix.spinnaker.orca.clouddriver.pipeline.providers.aws.lambda.LambdaStageConstants;
import com.netflix.spinnaker.orca.clouddriver.tasks.providers.aws.lambda.model.LambdaDefinition;
import com.netflix.spinnaker.orca.clouddriver.utils.LambdaCloudDriverUtils;
import java.time.Duration;
import java.util.Map;
import javax.annotation.Nonnull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class LambdaWaitForCachePublishTask implements LambdaStageBaseTask {
  private static final Logger logger = LoggerFactory.getLogger(LambdaWaitForCachePublishTask.class);

  @Autowired CloudDriverConfigurationProperties props;

  @Autowired private LambdaCloudDriverUtils utils;

  @Nonnull
  @Override
  public TaskResult execute(@Nonnull StageExecution stage) {
    logger.debug("Executing LambdaWaitForCachePublishTask...");
    return waitForCacheUpdate(stage);
  }

  private TaskResult waitForCacheUpdate(@Nonnull StageExecution stage) {
    if (stage.getContext().containsKey(LambdaStageConstants.publishVersionUrlKey)) {
      String publishUrl =
          (String) stage.getContext().get(LambdaStageConstants.publishVersionUrlKey);
      String version = utils.getPublishedVersion(publishUrl);
      for (int i = 0; i < 10; i++) {
        LambdaDefinition lf = utils.retrieveLambdaFromCache(stage, true);
        if (lf != null) {
          Map<String, String> revisions = lf.getRevisions();
          if (revisions.containsValue(version)) {
            return taskComplete(stage);
          }
        }
        utils.await(Duration.ofSeconds(30).toMillis());
      }
      return this.formErrorTaskResult(
          stage,
          "New version failed to appear in the cache after it was determined a publish was necessary");
    }
    return taskComplete(stage);
  }
}
