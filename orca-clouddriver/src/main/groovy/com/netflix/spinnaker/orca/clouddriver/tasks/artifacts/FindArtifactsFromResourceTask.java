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

import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import com.netflix.spinnaker.kork.core.RetrySupport;
import com.netflix.spinnaker.orca.ExecutionStatus;
import com.netflix.spinnaker.orca.Task;
import com.netflix.spinnaker.orca.TaskResult;
import com.netflix.spinnaker.orca.clouddriver.OortService;
import com.netflix.spinnaker.orca.clouddriver.model.Manifest;
import com.netflix.spinnaker.orca.clouddriver.tasks.AbstractCloudProviderAwareTask;
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import com.netflix.spinnaker.orca.pipeline.util.ArtifactResolver;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.Nonnull;
import java.util.*;

@Slf4j
@Component
public class FindArtifactsFromResourceTask extends AbstractCloudProviderAwareTask implements Task {
  public static final String TASK_NAME = "findArtifactsFromResource";

  @Autowired
  OortService oortService;

  RetrySupport retrySupport = new RetrySupport();

  @Nonnull
  @Override
  public TaskResult execute(@Nonnull Stage stage) {
    StageData stageData = stage.mapTo(StageData.class);
    String account = getCredentials(stage);
    Map<String, Object> outputs = new HashMap<>();

    Manifest manifest = retrySupport.retry(() -> oortService.getManifest(account, stageData.location, stageData.manifestName
      ), 5, 1000, true);

    if (manifest != null) {
      outputs.put("manifest", manifest.getManifest());
      outputs.put("artifacts", manifest.getArtifacts());
    } else {
      throw new IllegalArgumentException("Manifest " + stageData.manifestName + " not found in "
        + stageData.location);
    }

    return TaskResult.builder(ExecutionStatus.SUCCEEDED).context(outputs).outputs(outputs).build();
  }

  public static class StageData {
    public String location;
    public String manifestName;
  }
}
