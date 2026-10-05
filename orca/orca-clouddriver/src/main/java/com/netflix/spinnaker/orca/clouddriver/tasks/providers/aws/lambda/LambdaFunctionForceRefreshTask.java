/*
 * Copyright 2019 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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

import com.netflix.spinnaker.kork.retrofit.Retrofit2SyncCall;
import com.netflix.spinnaker.orca.api.pipeline.Task;
import com.netflix.spinnaker.orca.api.pipeline.TaskResult;
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus;
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution;
import com.netflix.spinnaker.orca.clouddriver.CloudDriverCacheService;
import com.netflix.spinnaker.orca.clouddriver.utils.CloudProviderAware;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nonnull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class LambdaFunctionForceRefreshTask implements CloudProviderAware, Task {

  private static final Logger logger =
      LoggerFactory.getLogger(LambdaFunctionForceRefreshTask.class);

  static final String REFRESH_TYPE = "Function";

  public static final String TASK_NAME = "forceCacheRefresh";

  @Autowired private CloudDriverCacheService cacheService;

  @Nonnull
  @Override
  public TaskResult execute(@Nonnull StageExecution stage) {
    String cloudProvider = getCloudProvider(stage);

    Map<String, Object> task = new HashMap<>(stage.getContext());
    task.put("appName", stage.getExecution().getApplication());

    try {
      Retrofit2SyncCall.executeCall(
          cacheService.forceCacheUpdate(cloudProvider, REFRESH_TYPE, task));
    } catch (Exception e) {
      logger.warn(
          "Failed to force cache refresh (cloudProvider: {}, type: {}, account: {}, region: {}, functionName: {})",
          cloudProvider,
          REFRESH_TYPE,
          getCredentials(stage),
          task.get("region"),
          task.get("functionName"),
          e);
    }

    return TaskResult.ofStatus(ExecutionStatus.SUCCEEDED);
  }
}
