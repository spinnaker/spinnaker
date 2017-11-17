/*
 * Copyright 2017 Google, Inc.
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

package com.netflix.spinnaker.orca.clouddriver.tasks.manifest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import com.netflix.spinnaker.orca.ExecutionStatus;
import com.netflix.spinnaker.orca.OverridableTimeoutRetryableTask;
import com.netflix.spinnaker.orca.TaskResult;
import com.netflix.spinnaker.orca.clouddriver.OortService;
import com.netflix.spinnaker.orca.clouddriver.model.Manifest;
import com.netflix.spinnaker.orca.clouddriver.utils.CloudProviderAware;
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import retrofit.RetrofitError;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Component
@Slf4j
public class UpdateManifestWaitForStableTask implements OverridableTimeoutRetryableTask, CloudProviderAware {
  public final static String TASK_NAME = "waitForStable";

  @Autowired
  OortService oortService;

  @Autowired
  ObjectMapper objectMapper;

  @Override
  public long getBackoffPeriod() {
    return TimeUnit.SECONDS.toMillis(5);
  }

  @Override
  public long getTimeout() {
    return TimeUnit.MINUTES.toMillis(30);
  }


  @Override
  public TaskResult execute(Stage stage) {
    String account = getCredentials(stage);
    String name = (String) stage.getContext().get("manifest.name");
    String location = (String) stage.getContext().get("manifest.location");
    String id = String.format("'%s' in '%s' for account %s", name, location, account);
    List<String> messages = new ArrayList<>();

    Manifest manifest;
    try {
      manifest = oortService.getManifest(account, location, name);
    } catch (RetrofitError e) {
      log.warn("Unable to read manifest {}", id, e);
      return new TaskResult(ExecutionStatus.RUNNING, new HashMap<>(), new HashMap<>());
    } catch (Exception e) {
      throw new RuntimeException("Execution '" + stage.getExecution().getId() + "' failed with unexpected reason: " + e.getMessage(), e);
    }

    Manifest.Status status = manifest.getStatus();
    if (status.getStable() == null || !status.getStable().isState()) {
      messages.add(status.getStable().getMessage());
      Map<String, Object> context = new ImmutableMap.Builder<String, Object>()
          .put("stableMessages", messages)
          .build();

      return new TaskResult(ExecutionStatus.RUNNING, context, new HashMap<>());
    }

    return new TaskResult(ExecutionStatus.SUCCEEDED);
  }
}
