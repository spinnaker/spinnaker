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

import com.google.common.collect.ImmutableMap;
import com.netflix.spinnaker.orca.ExecutionStatus;
import com.netflix.spinnaker.orca.OverridableTimeoutRetryableTask;
import com.netflix.spinnaker.orca.TaskResult;
import com.netflix.spinnaker.orca.clouddriver.OortService;
import com.netflix.spinnaker.orca.clouddriver.model.Manifest;
import com.netflix.spinnaker.orca.clouddriver.model.Manifest.Status;
import com.netflix.spinnaker.orca.clouddriver.utils.CloudProviderAware;
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nonnull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import retrofit.RetrofitError;

@Component
@RequiredArgsConstructor
@Slf4j
public class WaitForManifestStableTask
    implements OverridableTimeoutRetryableTask, CloudProviderAware, ManifestAware {
  public static final String TASK_NAME = "waitForManifestToStabilize";

  private final OortService oortService;

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

    WaitForManifestStableContext context = stage.mapTo(WaitForManifestStableContext.class);

    List<String> messages = context.getMessages();
    List<String> failureMessages = context.getFailureMessages();
    List<Map<String, String>> stableManifests = context.getStableManifests();
    List<Map<String, String>> failedManifests = context.getFailedManifests();
    List warnings = context.getWarnings();

    boolean anyIncomplete = false;
    for (Map.Entry<String, List<String>> entry : deployedManifests.entrySet()) {
      String location = entry.getKey();
      for (String name : entry.getValue()) {

        String identifier = readableIdentifier(account, location, name);

        if (context.getCompletedManifests().stream()
            .anyMatch(
                completedManifest ->
                    location.equals(completedManifest.get("location"))
                        && name.equals(completedManifest.get("manifestName")))) {
          continue;
        }

        Manifest manifest;
        try {
          manifest = oortService.getManifest(account, location, name, false);
        } catch (RetrofitError e) {
          log.warn("Unable to read manifest {}", identifier, e);
          return TaskResult.builder(ExecutionStatus.RUNNING)
              .context(new HashMap<>())
              .outputs(new HashMap<>())
              .build();
        } catch (Exception e) {
          throw new RuntimeException(
              "Execution '"
                  + stage.getExecution().getId()
                  + "' failed with unexpected reason: "
                  + e.getMessage(),
              e);
        }

        Map<String, String> manifestNameAndLocation =
            ImmutableMap.<String, String>builder()
                .put("manifestName", name)
                .put("location", location)
                .build();

        Status status = manifest.getStatus();
        if (status.getFailed().isState()) {
          failedManifests.add(manifestNameAndLocation);
          String failureMessage = identifier + ": " + status.getFailed().getMessage();
          messages.add(failureMessage);
          failureMessages.add(failureMessage);
        } else if (status.getStable().isState()) {
          stableManifests.add(manifestNameAndLocation);
        } else {
          anyIncomplete = true;
          messages.add(identifier + ": waiting for manifest to stabilize");
        }

        if (!manifest.getWarnings().isEmpty()) {
          warnings.addAll(manifest.getWarnings());
        }
      }
    }

    ImmutableMap.Builder<String, Object> builder =
        ImmutableMap.<String, Object>builder()
            .put("messages", messages)
            .put("stableManifests", stableManifests)
            .put("failedManifests", failedManifests);

    if (!failureMessages.isEmpty()) {
      builder.put("exception", buildExceptions(failureMessages));
    }
    if (!warnings.isEmpty()) {
      builder.put("warnings", warnings);
    }

    Map<String, Object> newContext = builder.build();

    if (anyIncomplete) {
      return TaskResult.builder(ExecutionStatus.RUNNING)
          .context(newContext)
          .outputs(new HashMap<>())
          .build();
    }

    if (failedManifests.isEmpty()) {
      return TaskResult.builder(ExecutionStatus.SUCCEEDED)
          .context(newContext)
          .outputs(new HashMap<>())
          .build();
    } else {
      return TaskResult.builder(ExecutionStatus.TERMINAL).context(newContext).build();
    }
  }

  private String readableIdentifier(String account, String location, String name) {
    return String.format("'%s' in '%s' for account %s", name, location, account);
  }

  private static Map<String, Object> buildExceptions(List<String> failureMessages) {
    return new ImmutableMap.Builder<String, Object>()
        .put(
            "details",
            new ImmutableMap.Builder<String, List<String>>().put("errors", failureMessages).build())
        .build();
  }
}
