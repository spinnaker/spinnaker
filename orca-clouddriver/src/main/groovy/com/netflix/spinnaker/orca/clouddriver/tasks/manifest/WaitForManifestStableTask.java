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

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Component
@Slf4j
public class WaitForManifestStableTask implements OverridableTimeoutRetryableTask, CloudProviderAware, ManifestAware {
  public final static String TASK_NAME = "waitForManifestToStabilize";

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

  @Nonnull
  @Override
  public TaskResult execute(@Nonnull Stage stage) {
    String account = getCredentials(stage);
    Map<String, List<String>> deployedManifests = manifestNamesByNamespace(stage);
    List<String> messages = new ArrayList<>();
    List<String> failureMessages = new ArrayList<>();
    List<Map<String, String>> stableManifests = new ArrayList<>();
    List<Map<String, String>> failedManifests = new ArrayList<>();
    List warnings = new ArrayList<>();
    boolean allStable = true;
    boolean anyFailed = false;
    boolean anyUnknown = false;

    for (Map.Entry<String, List<String>> entry : deployedManifests.entrySet()) {
      String location = entry.getKey();
      for (String name : entry.getValue()) {
        String identifier = readableIdentifier(account, location, name);
        Manifest manifest;
        try {
          manifest = oortService.getManifest(account, location, name);
        } catch (RetrofitError e) {
          log.warn("Unable to read manifest {}", identifier, e);
          return new TaskResult(ExecutionStatus.RUNNING, new HashMap<>(), new HashMap<>());
        } catch (Exception e) {
          throw new RuntimeException("Execution '" + stage.getExecution().getId() + "' failed with unexpected reason: " + e.getMessage(), e);
        }

        Status status = manifest.getStatus();
        if (status.getStable() == null || !status.getStable().isState()) {
          allStable = false;
          messages.add(identifier + ": waiting for manifest to stabilize");
        }

        Map<String, String> manifestNameAndLocation = ImmutableMap.<String, String>builder().
          put("manifestName", name).
          put("location", location).
          build();

        if (status.getFailed() != null && status.getFailed().isState()) {
          anyFailed = true;
          failedManifests.add(manifestNameAndLocation);
          String failureMessage = identifier + ": " + status.getFailed().getMessage();
          messages.add(failureMessage);
          failureMessages.add(failureMessage);
        }

        if (status.getStable() == null && status.getFailed() == null) {
          anyUnknown = true;
        }

        if (status.getStable() != null && status.getStable().isState()
          && (status.getFailed() == null || !status.getFailed().isState())) {
          stableManifests.add(manifestNameAndLocation);
        }

        if (manifest.getWarnings() != null && !manifest.getWarnings().isEmpty()) {
          warnings.addAll(manifest.getWarnings());
        }
      }
    }

    ImmutableMap.Builder builder = new ImmutableMap.Builder<String, Object>()
        .put("messages", messages)
        .put("stableManifests", stableManifests)
        .put("failedManifests", failedManifests);

    if (!failureMessages.isEmpty()) {
      builder.put("exception", buildExceptions(failureMessages));
    }
    if (!warnings.isEmpty()) {
      builder.put("warnings", warnings);
    }

    Map<String, Object> context = builder.build();

    if (!anyUnknown && anyFailed) {
      return new TaskResult(ExecutionStatus.TERMINAL, context);
    } else if (allStable) {
      return new TaskResult(ExecutionStatus.SUCCEEDED, context, new HashMap<>());
    } else {
      return new TaskResult(ExecutionStatus.RUNNING, context, new HashMap<>());
    }
  }

  private String readableIdentifier(String account, String location, String name) {
    return String.format("'%s' in '%s' for account %s", name, location, account);
  }

  private static Map<String, Object> buildExceptions(List<String> failureMessages) {
    return new ImmutableMap.Builder<String, Object>()
      .put(
        "details",
        new ImmutableMap.Builder<String, List<String>>()
          .put("errors", failureMessages)
          .build()
      ).build();
  }
}
