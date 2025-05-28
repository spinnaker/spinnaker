/*
 * Copyright 2018 Joel Wilsson
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
 *
 */

package com.netflix.spinnaker.orca.clouddriver.tasks.artifacts;

import com.netflix.spinnaker.kork.core.RetrySupport;
import com.netflix.spinnaker.kork.retrofit.Retrofit2SyncCall;
import com.netflix.spinnaker.orca.api.pipeline.Task;
import com.netflix.spinnaker.orca.api.pipeline.TaskResult;
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus;
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution;
import com.netflix.spinnaker.orca.clouddriver.OortService;
import com.netflix.spinnaker.orca.clouddriver.model.Manifest;
import com.netflix.spinnaker.orca.clouddriver.utils.CloudProviderAware;
import java.util.*;
import javax.annotation.Nonnull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class FindArtifactsFromResourceTask implements CloudProviderAware, Task {
  public static final String TASK_NAME = "findArtifactsFromResource";

  @Autowired OortService oortService;

  RetrySupport retrySupport = new RetrySupport();

  @Nonnull
  @Override
  public TaskResult execute(@Nonnull StageExecution stage) {
    StageData stageData = stage.mapTo(StageData.class);
    String account = getCredentials(stage);
    Map<String, Object> outputs = new HashMap<>();

    Manifest manifest =
        retrySupport.retry(
            () ->
                Retrofit2SyncCall.execute(
                    oortService.getManifest(
                        account, stageData.location, stageData.manifestName, false)),
            5,
            1000,
            true);

    if (manifest != null) {
      outputs.put("manifest", manifest.getManifest());
      outputs.put("artifacts", manifest.getArtifacts());
    } else {
      throw new IllegalArgumentException(
          "Manifest " + stageData.manifestName + " not found in " + stageData.location);
    }

    return TaskResult.builder(ExecutionStatus.SUCCEEDED).context(outputs).outputs(outputs).build();
  }

  public static class StageData {
    public String location;
    public String manifestName;
  }
}
