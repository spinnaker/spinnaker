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

import static java.util.Comparator.comparing;
import static java.util.Comparator.naturalOrder;
import static java.util.Comparator.nullsFirst;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import com.netflix.spinnaker.kork.retrofit.Retrofit2SyncCall;
import com.netflix.spinnaker.kork.retrofit.exceptions.SpinnakerServerException;
import com.netflix.spinnaker.orca.api.pipeline.OverridableTimeoutRetryableTask;
import com.netflix.spinnaker.orca.api.pipeline.TaskResult;
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus;
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution;
import com.netflix.spinnaker.orca.clouddriver.OortService;
import com.netflix.spinnaker.orca.clouddriver.model.Manifest;
import com.netflix.spinnaker.orca.clouddriver.model.Manifest.Status;
import com.netflix.spinnaker.orca.clouddriver.model.ManifestCoordinates;
import com.netflix.spinnaker.orca.clouddriver.model.ManifestEvents;
import com.netflix.spinnaker.orca.clouddriver.utils.CloudProviderAware;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.Nonnull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class WaitForManifestStableTask
    implements OverridableTimeoutRetryableTask, CloudProviderAware, ManifestAware {
  public static final String TASK_NAME = "waitForManifestToStabilize";

  private static final Pattern BACKTICK_RUN = Pattern.compile("`+");

  private final OortService oortService;
  private final ObjectMapper objectMapper;

  @Override
  public long getBackoffPeriod() {
    return TimeUnit.SECONDS.toMillis(5);
  }

  @Override
  public long getTimeout() {
    return TimeUnit.MINUTES.toMillis(30);
  }

  @Override
  public long getDynamicTimeout(@Nonnull StageExecution stage) {
    return Optional.ofNullable(stage.getContext().get("stableManifestTimeoutMinutes"))
        .map(v -> Long.parseLong(v.toString()))
        .map(TimeUnit.MINUTES::toMillis)
        .orElseGet(this::getTimeout);
  }

  @Nonnull
  @Override
  public TaskResult execute(@Nonnull StageExecution stage) {
    String account = getCredentials(stage);
    Map<String, List<String>> deployedManifests = manifestNamesByNamespace(stage);

    WaitForManifestStableContext context = stage.mapTo(WaitForManifestStableContext.class);

    List<String> messages = context.getMessages();
    List<String> failureMessages = context.getFailureMessages();
    List<Map<String, String>> stableManifests = context.getStableManifests();
    List<Map<String, String>> failedManifests = context.getFailedManifests();
    List warnings = context.getWarnings();
    Map<String, ManifestCoordinates> replicaSetPerDeployment = context.getReplicaSetPerDeployment();
    Map<String, Set<KubernetesManifestMetadata>> manifestsMetadataByNamespace =
        context.getManifestsMetadataByNamespace();
    Map<String, String> outputs = new HashMap<>();
    boolean includeEvents = context.isIncludeEvents();

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
          manifest =
              Retrofit2SyncCall.execute(
                  oortService.getManifest(account, location, name, includeEvents));
        } catch (SpinnakerServerException e) {
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
          String failureMessage = identifier + ": " + asInlineCode(status.getFailed().getMessage());
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

        if (includeEvents) {
          KubernetesManifestMetadata manifestMetadata =
              updateMetadata(manifest, manifestsMetadataByNamespace);
          replicaSetPerDeployment.put(
              name,
              getLatestReplicaSetsInK8sDeployment(
                  manifest, location, manifestMetadata.getEvents()));
        }
      }
    }

    ImmutableMap.Builder<String, Object> builder =
        ImmutableMap.<String, Object>builder()
            .put("messages", messages)
            .put("stableManifests", stableManifests)
            .put("failedManifests", failedManifests);

    if (!warnings.isEmpty()) {
      builder.put("warnings", warnings);
    }
    if (includeEvents) {
      builder.put("replicaSetPerDeployment", replicaSetPerDeployment);
      builder.put("manifestsMetadataByNamespace", manifestsMetadataByNamespace);
    }

    // at this point, some of the manifests have failed. So we want to surface as many relevant
    // manifest metadata details as possible
    if (includeEvents && !failedManifests.isEmpty()) {
      try {
        failureMessages.addAll(
            includeReplicaSetAndPodDetails(
                account, failedManifests, replicaSetPerDeployment, manifestsMetadataByNamespace));
      } catch (Exception e) {
        // this exception isn't the most important one, thus it should not overwrite the main cause
        // of the stage failure. So just adding the exception to the stage outputs so that we can
        // inspect what the problem is without needing to look into orca logs
        outputs.put(
            "includeEventsError",
            "failed to include replica set and pod events and warnings. Error: " + e);
      }
    }

    if (!failureMessages.isEmpty()) {
      builder.put("exception", buildExceptions(failureMessages));
    }

    ExecutionStatus status = ExecutionStatus.SUCCEEDED;

    if (anyIncomplete) {
      status = ExecutionStatus.RUNNING;
    } else if (!failedManifests.isEmpty()) {
      status = ExecutionStatus.TERMINAL;
    }

    return TaskResult.builder(status).context(builder.build()).outputs(outputs).build();
  }

  private String readableIdentifier(String account, String location, String name) {
    return String.format("'%s' in '%s' for account %s", name, location, account);
  }

  /**
   * Status messages can be written by any controller that manages the resource, and Deck renders
   * failure messages as Markdown. Wrapping the message in a CommonMark code span makes it render
   * literally: the fence is one backtick longer than the longest backtick run in the message, line
   * breaks are flattened (a blank line would otherwise end the span), and padding keeps a leading
   * or trailing backtick from joining the fence.
   */
  static String asInlineCode(String message) {
    if (message == null || message.isBlank()) {
      return message;
    }
    String text = message.replaceAll("\\R+", " ");
    int longestRun = 0;
    Matcher runs = BACKTICK_RUN.matcher(text);
    while (runs.find()) {
      longestRun = Math.max(longestRun, runs.group().length());
    }
    String fence = "`".repeat(longestRun + 1);
    String padding = text.startsWith("`") || text.endsWith("`") ? " " : "";
    return fence + padding + text + padding + fence;
  }

  private static Map<String, Object> buildExceptions(List<String> failureMessages) {
    return new ImmutableMap.Builder<String, Object>()
        .put(
            "details",
            new ImmutableMap.Builder<String, List<String>>().put("errors", failureMessages).build())
        .build();
  }

  /**
   * Given a K8s Deployment kind manifest, find the latest replica set in that deployment
   *
   * @param manifest A Kubernetes Manifest
   * @param location Namespace
   * @param manifestEvents list of events contained in the manifest
   * @return {@link ManifestCoordinates}> latest replica set in deployment
   */
  private ManifestCoordinates getLatestReplicaSetsInK8sDeployment(
      Manifest manifest, String location, Set<ManifestEvents> manifestEvents) {
    // for Deployment Kinds, we are attempting to get the events for a replicaSet and
    // the pods controlled by that replicaSet as well. This is because the most meaningful events
    // are those that belong to the pods. The idea is to showcase all the relevant events in the
    // execution context
    if (manifest.getManifest().get("kind").equals("Deployment")) {
      // without having to make another call to clouddriver, this is the simplest way to obtain the
      // name of the replica set. We only care about the replica sets that are being scaled up, as
      // these are the newly created replica sets, and within this, we only care about the most
      // recent
      Optional<ManifestEvents> replicaSet =
          manifestEvents.stream()
              .filter(
                  m ->
                      m.getReason().equals("ScalingReplicaSet")
                          && m.getMessage().contains("Scaled up replica set"))
              .max(
                  nullsFirst(
                      comparing(ManifestEvents::getLastTimestamp, nullsFirst(naturalOrder()))));

      if (replicaSet.isPresent()) {
        return ManifestCoordinates.builder()
            .kind("ReplicaSet")
            .namespace(location)
            .name(
                replicaSet
                    .get()
                    .getMessage()
                    .replace("(combined from similar events): ", "")
                    .replace("Scaled up replica set", "replicaSet")
                    .split(" to")[0])
            .build();
      }
    }
    return null;
  }

  private KubernetesManifestMetadata updateMetadata(
      Manifest manifest,
      Map<String, Set<KubernetesManifestMetadata>> manifestsMetadataByNamespace) {
    Set<KubernetesManifestMetadata> existingResourcesByNamespace =
        manifestsMetadataByNamespace.getOrDefault(manifest.getLocation(), new HashSet<>());

    KubernetesManifestMetadata metadata =
        existingResourcesByNamespace.stream()
            .filter(w -> w.getName().equals(manifest.getName()))
            .findFirst()
            .orElse(new KubernetesManifestMetadata(manifest.getName()));

    // 1. add warnings
    if (!manifest.getWarnings().isEmpty()) {
      metadata.addWarnings(manifest.getWarnings());
    }

    // 2. add statuses
    try {
      KubernetesManifestStatus status =
          objectMapper.convertValue(
              manifest.getManifest().get("status"), KubernetesManifestStatus.class);
      if (status != null) {
        metadata.updateStatusConditions(status);
      }
    } catch (Exception ignored) {
    }

    // 3. add events
    if (!manifest.getEvents().isEmpty()) {
      Set<ManifestEvents> manifestEvents =
          objectMapper.convertValue(
              manifest.getEvents(), new TypeReference<Set<ManifestEvents>>() {});
      metadata.addEvents(manifestEvents);
    }

    existingResourcesByNamespace.add(metadata);
    manifestsMetadataByNamespace.put(manifest.getLocation(), existingResourcesByNamespace);

    return metadata;
  }

  private Set<String> getFailureMessages(
      KubernetesManifestMetadata metadata, String account, String namespace, String resourceName) {
    Set<String> failureDetails = new HashSet<>();
    metadata.getEvents().stream()
        .filter(e -> e.getType().equals("Warning"))
        .forEach(
            e ->
                failureDetails.add(
                    "Resource: "
                        + readableIdentifier(account, namespace, resourceName)
                        + " is not stable. Reason: "
                        + e.getReason()
                        + ". Details: "
                        + e.getMessage()));

    metadata.getConditions().stream()
        .filter(c -> !c.getStatus().equals("True"))
        .forEach(
            c ->
                failureDetails.add(
                    "Resource: "
                        + readableIdentifier(account, namespace, resourceName)
                        + ". Condition: "
                        + c.getReason()
                        + ". Details: "
                        + c.getMessage()));

    return failureDetails;
  }

  /**
   * This method finds all the failed manifests that are in the manifestResourcesMap and attempts to
   * find kube events related to replica sets and pods belonging to that manifest. It saves all
   * events, warnings and statuses from these resources in the execution context
   *
   * @param account spinnaker's K8s account definition for a cluster
   * @param failedManifests a {@link List}<{@link Map}><{@link String}, {@link String}>> containing
   *     a list of namespace to resource name mappings of manifests that did not deploy successfully
   * @param replicaSetPerDeployment a {@link Map}<{@link String}, <{@link ManifestCoordinates}>>
   *     containing a map of K8s Deployment manifest names to latest replica set contained within it
   */
  private Set<String> includeReplicaSetAndPodDetails(
      String account,
      List<Map<String, String>> failedManifests,
      Map<String, ManifestCoordinates> replicaSetPerDeployment,
      Map<String, Set<KubernetesManifestMetadata>> manifestMetadata) {
    Set<String> failureDetails = new HashSet<>();
    // failedManifests looks like this - I have no idea why they made it a map instead of a class,
    // but
    // I am not changing it as it could very well break other things:
    // "failedManifests": [
    //  {
    //    "location": "example-service",
    //    "manifestName": "deployment example-service-web"
    //  }
    // ]
    for (Map<String, String> failedManifest : failedManifests) {
      if (!replicaSetPerDeployment.containsKey(failedManifest.get("manifestName"))) {
        continue;
      }
      ManifestCoordinates replicaSetCoordinates =
          replicaSetPerDeployment.get(failedManifest.get("manifestName"));
      Manifest manifest = null;
      try {
        manifest =
            Retrofit2SyncCall.execute(
                oortService.getManifest(
                    account,
                    replicaSetCoordinates.getNamespace(),
                    replicaSetCoordinates.getName(),
                    true));
      } catch (Exception ignored) { // these exceptions aren't important, this is best effort anyway
      }
      if (manifest == null) {
        continue;
      }
      KubernetesManifestMetadata metadata = updateMetadata(manifest, manifestMetadata);
      failureDetails.addAll(
          getFailureMessages(
              metadata,
              account,
              replicaSetCoordinates.getNamespace(),
              replicaSetCoordinates.getName()));

      // now attempt to get this replica set's pod events.
      // We don't want to get all pod events. A replica set can have potentially 275
      // pods. We just need the latest pod's events for now
      if (!metadata.getEvents().isEmpty()) {
        ManifestEvents podEvent =
            metadata.getEvents().stream()
                .filter(
                    m ->
                        m.getReason().equals("SuccessfulCreate")
                            && m.getMessage().contains("Created pod:"))
                .max(
                    nullsFirst(
                        comparing(ManifestEvents::getLastTimestamp, nullsFirst(naturalOrder()))))
                .orElse(null);

        if (podEvent != null) {
          String podName = podEvent.getMessage().replace("Created pod:", "pod");
          Manifest podManifest = null;
          try {
            podManifest =
                Retrofit2SyncCall.execute(
                    oortService.getManifest(
                        account, replicaSetCoordinates.getNamespace(), podName, true));
          } catch (Exception e) { // these exceptions aren't important, this is best effort anyway
          }
          if (podManifest == null) {
            continue;
          }
          KubernetesManifestMetadata podMetadata = updateMetadata(podManifest, manifestMetadata);
          failureDetails.addAll(
              getFailureMessages(
                  podMetadata, account, replicaSetCoordinates.getNamespace(), podName));
        }
      }
    }
    return failureDetails;
  }

  @Data
  @AllArgsConstructor
  @NoArgsConstructor
  public static class KubernetesManifestMetadata {
    String name;
    @EqualsAndHashCode.Exclude Set<KubernetesManifestStatusCondition> conditions;
    @EqualsAndHashCode.Exclude Set<ManifestEvents> events;
    @EqualsAndHashCode.Exclude Set<String> warnings;

    public KubernetesManifestMetadata(String name) {
      this.name = name;
      conditions = new HashSet<>();
      events = new HashSet<>();
      warnings = new HashSet<>();
    }

    public void addWarnings(Collection<String> warnings) {
      this.warnings.addAll(warnings);
    }

    public void addEvents(Collection<ManifestEvents> events) {
      this.events.addAll(events);
    }

    public void updateStatusConditions(KubernetesManifestStatus status) {
      this.conditions.addAll(status.getConditions());
    }
  }

  @Data
  public static class KubernetesManifestStatus {
    String phase;
    Set<KubernetesManifestStatusCondition> conditions;
  }

  @Data
  public static class KubernetesManifestStatusCondition {
    String lastTransitionTime;
    String message;
    String reason;
    String status;
    String type;
  }

  @Data
  public static class KubernetesManifestWarning {
    String name;
    Set<String> warnings;
  }
}
