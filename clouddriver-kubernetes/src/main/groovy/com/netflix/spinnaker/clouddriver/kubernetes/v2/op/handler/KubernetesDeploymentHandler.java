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

import com.netflix.spinnaker.clouddriver.kubernetes.v2.artifact.ArtifactReplacerFactory;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.caching.agent.KubernetesCacheDataConverter;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.caching.agent.KubernetesCoreCachingAgent;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.caching.agent.KubernetesV2CachingAgent;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.KubernetesSpinnakerKindMap;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesKind;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesManifest;
import com.netflix.spinnaker.clouddriver.model.Manifest.Status;
import io.kubernetes.client.models.V1beta2Deployment;
import io.kubernetes.client.models.V1beta2DeploymentCondition;
import io.kubernetes.client.models.V1beta2DeploymentStatus;
import org.springframework.stereotype.Component;

import static com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesApiVersion.APPS_V1BETA1;
import static com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesApiVersion.APPS_V1BETA2;
import static com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesApiVersion.EXTENSIONS_V1BETA1;
import static com.netflix.spinnaker.clouddriver.kubernetes.v2.op.handler.KubernetesHandler.DeployPriority.WORKLOAD_CONTROLLER_PRIORITY;

@Component
public class KubernetesDeploymentHandler extends KubernetesHandler implements
    CanResize,
    CanScale,
    CanPauseRollout,
    CanResumeRollout,
    CanUndoRollout,
    ServerGroupManagerHandler {

  public KubernetesDeploymentHandler() {
    registerReplacer(ArtifactReplacerFactory.dockerImageReplacer());
    registerReplacer(ArtifactReplacerFactory.configMapVolumeReplacer());
    registerReplacer(ArtifactReplacerFactory.secretVolumeReplacer());
    registerReplacer(ArtifactReplacerFactory.configMapEnvFromReplacer());
    registerReplacer(ArtifactReplacerFactory.secretEnvFromReplacer());
    registerReplacer(ArtifactReplacerFactory.configMapKeyValueFromReplacer());
    registerReplacer(ArtifactReplacerFactory.secretKeyValueFromReplacer());
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
  public KubernetesSpinnakerKindMap.SpinnakerKind spinnakerKind() {
    return KubernetesSpinnakerKindMap.SpinnakerKind.SERVER_GROUP_MANAGERS;
  }

  @Override
  public Status status(KubernetesManifest manifest) {
    if (manifest.getApiVersion().equals(EXTENSIONS_V1BETA1)
        || manifest.getApiVersion().equals(APPS_V1BETA1)
        || manifest.getApiVersion().equals(APPS_V1BETA2)) {
      if (manifest.isNewerThanObservedGeneration()) {
        return (new Status()).unknown();
      }
      V1beta2Deployment appsV1beta2Deployment = KubernetesCacheDataConverter.getResource(manifest, V1beta2Deployment.class);
      return status(appsV1beta2Deployment);
    } else {
      throw new UnsupportedVersionException(manifest);
    }
  }

  @Override
  public Class<? extends KubernetesV2CachingAgent> cachingAgentClass() {
    return KubernetesCoreCachingAgent.class;
  }

  private Status status(V1beta2Deployment deployment) {
    Status result = new Status();
    V1beta2DeploymentStatus status = deployment.getStatus();
    if (status == null) {
      result.unstable("No status reported yet")
          .unavailable("No availability reported");
      return result;
    }

    V1beta2DeploymentCondition paused = status.getConditions()
        .stream()
        .filter(c -> c.getReason().equalsIgnoreCase("deploymentpaused"))
        .findAny()
        .orElse(null);

    V1beta2DeploymentCondition available = status.getConditions()
        .stream()
        .filter(c -> c.getType().equalsIgnoreCase("available"))
        .findAny()
        .orElse(null);

    if (paused != null) {
      result.paused(paused.getMessage());
    }

    if (available != null && available.getStatus().equalsIgnoreCase("false")) {
      result.unstable(available.getMessage())
        .unavailable(available.getMessage());
    }

    V1beta2DeploymentCondition condition = status.getConditions()
      .stream()
      .filter(c -> c.getType().equalsIgnoreCase("progressing"))
      .findAny()
      .orElse(null);
    if (condition != null && condition.getReason().equalsIgnoreCase("progressdeadlineexceeded")) {
      return result.failed("Deployment exceeded its progress deadline");
    }

    Integer desiredReplicas = deployment.getSpec().getReplicas();
    Integer statusReplicas = status.getReplicas();
    if ((desiredReplicas == null || desiredReplicas == 0) && (statusReplicas == null || statusReplicas == 0)) {
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
