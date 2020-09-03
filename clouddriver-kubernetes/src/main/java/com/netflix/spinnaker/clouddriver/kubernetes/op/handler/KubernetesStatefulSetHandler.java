/*
 * Copyright 2017 Google, Inc.
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
 *
 */

package com.netflix.spinnaker.clouddriver.kubernetes.op.handler;

import static com.netflix.spinnaker.clouddriver.kubernetes.op.handler.KubernetesHandler.DeployPriority.WORKLOAD_CONTROLLER_PRIORITY;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.netflix.spinnaker.clouddriver.kubernetes.artifact.Replacer;
import com.netflix.spinnaker.clouddriver.kubernetes.caching.Keys.InfrastructureCacheKey;
import com.netflix.spinnaker.clouddriver.kubernetes.caching.agent.KubernetesCacheDataConverter;
import com.netflix.spinnaker.clouddriver.kubernetes.caching.agent.KubernetesCachingAgentFactory;
import com.netflix.spinnaker.clouddriver.kubernetes.caching.agent.KubernetesCoreCachingAgent;
import com.netflix.spinnaker.clouddriver.kubernetes.description.SpinnakerKind;
import com.netflix.spinnaker.clouddriver.kubernetes.description.manifest.KubernetesKind;
import com.netflix.spinnaker.clouddriver.kubernetes.description.manifest.KubernetesManifest;
import com.netflix.spinnaker.clouddriver.kubernetes.model.Manifest.Status;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1beta2RollingUpdateStatefulSetStrategy;
import io.kubernetes.client.openapi.models.V1beta2StatefulSet;
import io.kubernetes.client.openapi.models.V1beta2StatefulSetStatus;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.springframework.stereotype.Component;

@Component
public class KubernetesStatefulSetHandler extends KubernetesHandler
    implements CanResize,
        CanScale,
        CanPauseRollout,
        CanResumeRollout,
        CanUndoRollout,
        CanRollingRestart,
        ServerGroupHandler {
  @Nonnull
  @Override
  protected ImmutableList<Replacer> artifactReplacers() {
    return ImmutableList.of(
        Replacer.dockerImage(),
        Replacer.configMapVolume(),
        Replacer.secretVolume(),
        Replacer.configMapEnv(),
        Replacer.secretEnv(),
        Replacer.configMapKeyValue(),
        Replacer.secretKeyValue());
  }

  @Override
  public int deployPriority() {
    return WORKLOAD_CONTROLLER_PRIORITY.getValue();
  }

  @Nonnull
  @Override
  public KubernetesKind kind() {
    return KubernetesKind.STATEFUL_SET;
  }

  @Override
  public boolean versioned() {
    return false;
  }

  @Nonnull
  @Override
  public SpinnakerKind spinnakerKind() {
    return SpinnakerKind.SERVER_GROUPS;
  }

  @Override
  protected KubernetesCachingAgentFactory cachingAgentFactory() {
    return KubernetesCoreCachingAgent::new;
  }

  @Override
  public Status status(KubernetesManifest manifest) {
    V1beta2StatefulSet v1beta2StatefulSet =
        KubernetesCacheDataConverter.getResource(manifest, V1beta2StatefulSet.class);
    return status(v1beta2StatefulSet);
  }

  public static String serviceName(KubernetesManifest manifest) {
    // TODO(lwander) perhaps switch on API version if this changes
    Map<String, Object> spec = (Map<String, Object>) manifest.get("spec");
    return (String) spec.get("serviceName");
  }

  @Override
  public Map<String, Object> hydrateSearchResult(InfrastructureCacheKey key) {
    Map<String, Object> result = super.hydrateSearchResult(key);
    result.put("serverGroup", result.get("name"));

    return result;
  }

  private Status status(V1beta2StatefulSet statefulSet) {
    if (statefulSet.getSpec().getUpdateStrategy().getType().equalsIgnoreCase("ondelete")) {
      return Status.defaultStatus();
    }

    V1beta2StatefulSetStatus status = statefulSet.getStatus();
    if (status == null) {
      return Status.noneReported();
    }

    if (!generationMatches(statefulSet, status)) {
      return Status.defaultStatus().unstable(UnstableReason.OLD_GENERATION.getMessage());
    }

    int desiredReplicas = defaultToZero(statefulSet.getSpec().getReplicas());
    int existing = defaultToZero(status.getReplicas());
    if (desiredReplicas > existing) {
      return Status.defaultStatus()
          .unstable("Waiting for at least the desired replica count to be met");
    }

    existing = defaultToZero(status.getReadyReplicas());
    if (desiredReplicas > existing) {
      return Status.defaultStatus().unstable("Waiting for all updated replicas to be ready");
    }

    String updateType = statefulSet.getSpec().getUpdateStrategy().getType();
    V1beta2RollingUpdateStatefulSetStrategy rollingUpdate =
        statefulSet.getSpec().getUpdateStrategy().getRollingUpdate();

    Integer updated = status.getUpdatedReplicas();

    if (updateType.equalsIgnoreCase("rollingupdate") && updated != null && rollingUpdate != null) {
      Integer partition = rollingUpdate.getPartition();
      Integer replicas = status.getReplicas();
      if (replicas != null && partition != null && (updated < (replicas - partition))) {
        return Status.defaultStatus().unstable("Waiting for partitioned roll out to finish");
      }
      return Status.defaultStatus().stable("Partitioned roll out complete");
    }

    existing = defaultToZero(status.getCurrentReplicas());
    if (desiredReplicas > existing) {
      return Status.defaultStatus().unstable("Waiting for all updated replicas to be scheduled");
    }

    if (!status.getCurrentRevision().equals(status.getUpdateRevision())) {
      return Status.defaultStatus()
          .unstable("Waiting for the updated revision to match the current revision");
    }

    return Status.defaultStatus();
  }

  private boolean generationMatches(
      V1beta2StatefulSet statefulSet, V1beta2StatefulSetStatus status) {
    Optional<Long> metadataGeneration =
        Optional.ofNullable(statefulSet.getMetadata()).map(V1ObjectMeta::getGeneration);
    Optional<Long> statusGeneration = Optional.ofNullable(status.getObservedGeneration());

    return statusGeneration.isPresent() && statusGeneration.equals(metadataGeneration);
  }

  // Unboxes an Integer, returning 0 if the input is null
  private static int defaultToZero(@Nullable Integer input) {
    return input == null ? 0 : input;
  }

  @Override
  public void addRelationships(
      Map<KubernetesKind, List<KubernetesManifest>> allResources,
      Map<KubernetesManifest, List<KubernetesManifest>> relationshipMap) {
    BiFunction<String, String, String> manifestName = (namespace, name) -> namespace + ":" + name;

    Map<String, KubernetesManifest> services =
        allResources.getOrDefault(KubernetesKind.SERVICE, new ArrayList<>()).stream()
            .collect(
                Collectors.toMap(
                    (m) -> manifestName.apply(m.getNamespace(), m.getName()), (m) -> m));

    for (KubernetesManifest manifest :
        allResources.getOrDefault(KubernetesKind.STATEFUL_SET, new ArrayList<>())) {
      String serviceName = KubernetesStatefulSetHandler.serviceName(manifest);
      if (Strings.isNullOrEmpty(serviceName)) {
        continue;
      }

      String key = manifestName.apply(manifest.getNamespace(), serviceName);

      if (!services.containsKey(key)) {
        continue;
      }

      KubernetesManifest service = services.get(key);
      relationshipMap.put(manifest, ImmutableList.of(service));
    }
  }
}
