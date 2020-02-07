/*
 * Copyright 2017 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.netflix.spinnaker.clouddriver.kubernetes.v2.op.handler;

import static com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesApiVersion.APPS_V1;
import static com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesApiVersion.APPS_V1BETA1;
import static com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesApiVersion.APPS_V1BETA2;
import static com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesApiVersion.EXTENSIONS_V1BETA1;
import static com.netflix.spinnaker.clouddriver.kubernetes.v2.op.handler.KubernetesHandler.DeployPriority.WORKLOAD_CONTROLLER_PRIORITY;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.netflix.spinnaker.clouddriver.kubernetes.description.SpinnakerKind;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.artifact.Replacer;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.caching.agent.KubernetesCacheDataConverter;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.caching.agent.KubernetesCoreCachingAgent;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.caching.agent.KubernetesV2CachingAgentFactory;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesApiVersion;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesKind;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesManifest;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.model.Manifest.Status;
import com.netflix.spinnaker.kork.annotations.NonnullByDefault;
import io.kubernetes.client.openapi.models.V1Deployment;
import io.kubernetes.client.openapi.models.V1DeploymentCondition;
import io.kubernetes.client.openapi.models.V1DeploymentStatus;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nullable;
import org.springframework.stereotype.Component;

@Component
@NonnullByDefault
public class KubernetesDeploymentHandler extends KubernetesHandler
    implements CanResize,
        CanScale,
        CanPauseRollout,
        CanResumeRollout,
        CanUndoRollout,
        CanRollingRestart,
        ServerGroupManagerHandler {

  private static final ImmutableSet<KubernetesApiVersion> SUPPORTED_API_VERSIONS =
      ImmutableSet.of(EXTENSIONS_V1BETA1, APPS_V1BETA1, APPS_V1BETA2, APPS_V1);

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

  @Override
  public KubernetesKind kind() {
    return KubernetesKind.DEPLOYMENT;
  }

  @Override
  public boolean versioned() {
    return false;
  }

  @Override
  public SpinnakerKind spinnakerKind() {
    return SpinnakerKind.SERVER_GROUP_MANAGERS;
  }

  @Override
  public Status status(KubernetesManifest manifest) {
    if (!SUPPORTED_API_VERSIONS.contains(manifest.getApiVersion())) {
      throw new UnsupportedVersionException(manifest);
    }
    if (manifest.isNewerThanObservedGeneration()) {
      return Status.defaultStatus().unknown();
    }
    V1Deployment appsV1Deployment =
        KubernetesCacheDataConverter.getResource(manifest, V1Deployment.class);
    return status(appsV1Deployment);
  }

  @Override
  protected KubernetesV2CachingAgentFactory cachingAgentFactory() {
    return KubernetesCoreCachingAgent::new;
  }

  private Status status(V1Deployment deployment) {
    V1DeploymentStatus status = deployment.getStatus();
    if (status == null) {
      return Status.defaultStatus()
          .unstable("No status reported yet")
          .unavailable("No availability reported");
    }

    List<V1DeploymentCondition> conditions =
        Optional.ofNullable(status.getConditions()).orElse(ImmutableList.of());

    Status result = Status.defaultStatus();
    getPausedReason(conditions).ifPresent(result::paused);
    getUnavailableReason(conditions)
        .ifPresent(reason -> result.unstable(reason).unavailable(reason));
    getFailedReason(conditions).ifPresent(result::failed);
    checkReplicaCounts(deployment, status)
        .ifPresent(reason -> result.unstable(reason.getMessage()));
    return result;
  }

  private static Optional<String> getUnavailableReason(
      Collection<V1DeploymentCondition> conditions) {
    return conditions.stream()
        .filter(c -> c.getType().equalsIgnoreCase("available"))
        .filter(c -> c.getStatus().equalsIgnoreCase("false"))
        .map(V1DeploymentCondition::getMessage)
        .findAny();
  }

  private static Optional<String> getPausedReason(Collection<V1DeploymentCondition> conditions) {
    return conditions.stream()
        .filter(c -> c.getReason() != null)
        .filter(c -> c.getReason().equalsIgnoreCase("deploymentpaused"))
        .map(V1DeploymentCondition::getMessage)
        .findAny();
  }

  private static Optional<String> getFailedReason(Collection<V1DeploymentCondition> conditions) {
    return conditions.stream()
        .filter(c -> c.getType().equalsIgnoreCase("progressing"))
        .filter(c -> c.getReason() != null)
        .filter(c -> c.getReason().equalsIgnoreCase("progressdeadlineexceeded"))
        .map(c -> "Deployment exceeded its progress deadline")
        .findAny();
  }

  // Unboxes an Integer, returning 0 if the input is null
  private static int defaultToZero(@Nullable Integer input) {
    return input == null ? 0 : input;
  }

  private static Optional<UnstableReason> checkReplicaCounts(
      V1Deployment deployment, V1DeploymentStatus status) {
    int desiredReplicas = defaultToZero(deployment.getSpec().getReplicas());
    int updatedReplicas = defaultToZero(status.getUpdatedReplicas());
    if (updatedReplicas < desiredReplicas) {
      return Optional.of(UnstableReason.UPDATED_REPLICAS);
    }

    int statusReplicas = defaultToZero(status.getReplicas());
    if (statusReplicas > updatedReplicas) {
      return Optional.of(UnstableReason.OLD_REPLICAS);
    }

    int availableReplicas = defaultToZero(status.getAvailableReplicas());
    if (availableReplicas < desiredReplicas) {
      return Optional.of(UnstableReason.AVAILABLE_REPLICAS);
    }

    int readyReplicas = defaultToZero(status.getReadyReplicas());
    if (readyReplicas < desiredReplicas) {
      return Optional.of(UnstableReason.READY_REPLICAS);
    }

    return Optional.empty();
  }
}
