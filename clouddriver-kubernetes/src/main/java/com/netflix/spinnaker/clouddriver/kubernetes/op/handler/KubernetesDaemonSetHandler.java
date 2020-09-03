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
import io.kubernetes.client.openapi.models.V1beta2DaemonSet;
import io.kubernetes.client.openapi.models.V1beta2DaemonSetStatus;
import java.util.Map;
import java.util.Optional;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.springframework.stereotype.Component;

@Component
public class KubernetesDaemonSetHandler extends KubernetesHandler
    implements CanResize,
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
    return KubernetesKind.DAEMON_SET;
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
    V1beta2DaemonSet v1beta2DaemonSet =
        KubernetesCacheDataConverter.getResource(manifest, V1beta2DaemonSet.class);
    return status(v1beta2DaemonSet);
  }

  @Override
  public Map<String, Object> hydrateSearchResult(InfrastructureCacheKey key) {
    Map<String, Object> result = super.hydrateSearchResult(key);
    result.put("serverGroup", result.get("name"));

    return result;
  }

  private Status status(V1beta2DaemonSet daemonSet) {
    V1beta2DaemonSetStatus status = daemonSet.getStatus();
    if (status == null) {
      return Status.noneReported();
    }

    if (!generationMatches(daemonSet, status)) {
      return Status.defaultStatus().unstable(UnstableReason.OLD_GENERATION.getMessage());
    }

    if (!daemonSet.getSpec().getUpdateStrategy().getType().equalsIgnoreCase("rollingupdate")) {
      return Status.defaultStatus();
    }

    Long observedGeneration = status.getObservedGeneration();
    if (observedGeneration != null
        && !observedGeneration.equals(daemonSet.getMetadata().getGeneration())) {
      return Status.defaultStatus().unstable("Waiting for daemonset spec update to be observed");
    }

    int desiredReplicas = defaultToZero(status.getDesiredNumberScheduled());
    int existing = defaultToZero(status.getCurrentNumberScheduled());
    if (desiredReplicas > existing) {
      return Status.defaultStatus().unstable("Waiting for all replicas to be scheduled");
    }

    existing = defaultToZero(status.getUpdatedNumberScheduled());
    if (desiredReplicas > existing) {
      return Status.defaultStatus().unstable("Waiting for all updated replicas to be scheduled");
    }

    existing = defaultToZero(status.getNumberAvailable());
    if (desiredReplicas > existing) {
      return Status.defaultStatus().unstable("Waiting for all replicas to be available");
    }

    existing = defaultToZero(status.getNumberReady());
    if (desiredReplicas > existing) {
      return Status.defaultStatus().unstable("Waiting for all replicas to be ready");
    }

    return Status.defaultStatus();
  }

  private boolean generationMatches(V1beta2DaemonSet daemonSet, V1beta2DaemonSetStatus status) {
    Optional<Long> metadataGeneration =
        Optional.ofNullable(daemonSet.getMetadata()).map(V1ObjectMeta::getGeneration);
    Optional<Long> statusGeneration = Optional.ofNullable(status.getObservedGeneration());

    return statusGeneration.isPresent() && statusGeneration.equals(metadataGeneration);
  }

  // Unboxes an Integer, returning 0 if the input is null
  private int defaultToZero(@Nullable Integer input) {
    return input == null ? 0 : input;
  }
}
