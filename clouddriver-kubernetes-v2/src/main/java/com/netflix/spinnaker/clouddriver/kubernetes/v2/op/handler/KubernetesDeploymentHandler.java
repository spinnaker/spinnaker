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
import io.kubernetes.client.openapi.models.V1Deployment;
import io.kubernetes.client.openapi.models.V1DeploymentCondition;
import io.kubernetes.client.openapi.models.V1DeploymentStatus;
import javax.annotation.Nonnull;
import org.springframework.stereotype.Component;

@Component
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
    return KubernetesKind.DEPLOYMENT;
  }

  @Override
  public boolean versioned() {
    return false;
  }

  @Nonnull
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
      return (new Status()).unknown();
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
    Status result = new Status();
    V1DeploymentStatus status = deployment.getStatus();
    if (status == null) {
      result.unstable("No status reported yet").unavailable("No availability reported");
      return result;
    }

    V1DeploymentCondition paused =
        status.getConditions().stream()
            .filter(c -> c.getReason().equalsIgnoreCase("deploymentpaused"))
            .findAny()
            .orElse(null);

    V1DeploymentCondition available =
        status.getConditions().stream()
            .filter(c -> c.getType().equalsIgnoreCase("available"))
            .findAny()
            .orElse(null);

    if (paused != null) {
      result.paused(paused.getMessage());
    }

    if (available != null && available.getStatus().equalsIgnoreCase("false")) {
      result.unstable(available.getMessage()).unavailable(available.getMessage());
    }

    V1DeploymentCondition condition =
        status.getConditions().stream()
            .filter(c -> c.getType().equalsIgnoreCase("progressing"))
            .findAny()
            .orElse(null);
    if (condition != null && condition.getReason().equalsIgnoreCase("progressdeadlineexceeded")) {
      return result.failed("Deployment exceeded its progress deadline");
    }

    Integer desiredReplicas = deployment.getSpec().getReplicas();
    Integer statusReplicas = status.getReplicas();
    if ((desiredReplicas == null || desiredReplicas == 0)
        && (statusReplicas == null || statusReplicas == 0)) {
      return result;
    }

    Integer updatedReplicas = status.getUpdatedReplicas();
    if (updatedReplicas == null || (desiredReplicas != null && desiredReplicas > updatedReplicas)) {
      return result.unstable("Waiting for all replicas to be updated");
    }

    if (statusReplicas != null && statusReplicas > updatedReplicas) {
      return result.unstable("Waiting for old replicas to finish termination");
    }

    Integer availableReplicas = status.getAvailableReplicas();
    if (availableReplicas == null || availableReplicas < updatedReplicas) {
      return result.unstable("Waiting for all replicas to be available");
    }

    Integer readyReplicas = status.getReadyReplicas();
    if (readyReplicas == null || (desiredReplicas != null && desiredReplicas > readyReplicas)) {
      return result.unstable("Waiting for all replicas to be ready");
    }

    return result;
  }
}
