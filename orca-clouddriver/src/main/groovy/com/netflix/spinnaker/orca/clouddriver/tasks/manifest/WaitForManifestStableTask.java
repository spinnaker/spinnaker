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
import com.netflix.spinnaker.orca.clouddriver.model.Manifest.Status;
import com.netflix.spinnaker.orca.clouddriver.utils.CloudProviderAware;
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import retrofit.RetrofitError;
import retrofit.client.Response;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Component
@Slf4j
public class WaitForManifestStableTask implements OverridableTimeoutRetryableTask, CloudProviderAware {
  public final static String TASK_NAME = "waitForManifestToStabilize";

  @Autowired
  OortService oortService;

  @Autowired
  ObjectMapper objectMapper;

  @Override
  public long getBackoffPeriod() {
    return TimeUnit.SECONDS.toMillis(1);
  }

  @Override
  public long getTimeout() {
    return TimeUnit.MINUTES.toMillis(30);
  }

  @Nonnull
  @Override
  public TaskResult execute(@Nonnull Stage stage) {
    String account = getCredentials(stage);
    Map<String, List<String>> deployedManifests = (Map<String, List<String>>) stage.getContext().get("deploy.outputs");
    List<String> messages = new ArrayList<>();
    boolean allStable = true;

    for (Map.Entry<String, List<String>> entry : deployedManifests.entrySet()) {
      String location = entry.getKey();
      for (String name : entry.getValue()) {
        String identifier = readableIdentifier(account, location, name);
        Response response;
        try {
          response = oortService.getManifest(account, location, name);
        } catch (RetrofitError e) {
          allStable = false;
          log.warn("Unable to read manifest " + identifier, e);
          continue;
        }

        if (response.getStatus() != 200) {
          allStable = false;
          messages.add(identifier + ": could not retrieve status");
          continue;
        }

        Manifest manifest;
        try {
          manifest = objectMapper.readValue(response.getBody().in(), Manifest.class);
        } catch (IOException e) {
          log.error("Failed to read " + identifier, e);
          throw new RuntimeException(e);
        }

        Status status = manifest.getStatus();
        if (!status.isStable()) {
          allStable = false;
          messages.add(identifier + ": " + status.getMessage());
        }
      }
    }

    if (allStable) {
      return TaskResult.SUCCEEDED;
    } else {
      Map<String, Object> context = new ImmutableMap.Builder<String, Object>()
          .put("stableMessages", messages)
          .build();

      return new TaskResult(ExecutionStatus.RUNNING, context, new HashMap<>());
    }
  }

  private String readableIdentifier(String account, String location, String name) {
    return String.format("'%s' in '%s' for account %s", name, location, account);
  }
}
