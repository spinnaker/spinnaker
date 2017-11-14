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

package com.netflix.spinnaker.clouddriver.kubernetes.v2.op.deployer;

import com.netflix.spinnaker.clouddriver.kubernetes.v2.caching.agent.KubernetesCacheDataConverter;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.caching.agent.KubernetesDeploymentCachingAgent;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.caching.agent.KubernetesV2CachingAgent;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.KubernetesSpinnakerKindMap;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesKind;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesManifest;
import com.netflix.spinnaker.clouddriver.model.Manifest.Status;
import io.kubernetes.client.models.AppsV1beta1Deployment;
import io.kubernetes.client.models.AppsV1beta1DeploymentStatus;
import io.kubernetes.client.models.ExtensionsV1beta1Deployment;
import io.kubernetes.client.models.ExtensionsV1beta1DeploymentStatus;
import io.kubernetes.client.models.V1beta2Deployment;
import io.kubernetes.client.models.V1beta2DeploymentStatus;
import org.springframework.stereotype.Component;

@Component
public class KubernetesDeploymentHandler extends KubernetesHandler implements CanResize, CanDelete, CanUndoRollout {
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
    return KubernetesSpinnakerKindMap.SpinnakerKind.SERVER_GROUP_MANAGER;
  }

  @Override
  public Status status(KubernetesManifest manifest) {
    switch (manifest.getApiVersion()) {
      case EXTENSIONS_V1BETA1:
        ExtensionsV1beta1Deployment extensionsV1beta1Deployment = KubernetesCacheDataConverter.getResource(manifest, ExtensionsV1beta1Deployment.class);
        return status(extensionsV1beta1Deployment);
      case APPS_V1BETA1:
        AppsV1beta1Deployment appsV1beta1Deployment = KubernetesCacheDataConverter.getResource(manifest, AppsV1beta1Deployment.class);
        return status(appsV1beta1Deployment);
      case APPS_V1BETA2:
        V1beta2Deployment appsV1beta2Deployment = KubernetesCacheDataConverter.getResource(manifest, V1beta2Deployment.class);
        return status(appsV1beta2Deployment);
      default:
        throw new UnsupportedVersionException(manifest);
    }
  }

  @Override
  public Class<? extends KubernetesV2CachingAgent> cachingAgentClass() {
    return KubernetesDeploymentCachingAgent.class;
  }

  private Status status(ExtensionsV1beta1Deployment deployment) {
    ExtensionsV1beta1DeploymentStatus status = deployment.getStatus();
    int desiredReplicas = deployment.getSpec().getReplicas();
    if (status == null) {
      return Status.unstable("No status reported yet");
    }

    Integer existing = status.getUpdatedReplicas();
    if (existing == null || desiredReplicas > existing) {
      return Status.unstable("Waiting for all replicas to be updated");
    }

    existing = status.getAvailableReplicas();
    if (existing == null || desiredReplicas > existing) {
      return Status.unstable("Waiting for all replicas to be available");
    }

    existing = status.getReadyReplicas();
    if (existing == null || desiredReplicas > existing) {
      return Status.unstable("Waiting for all replicas to be ready");
    }

    return Status.stable();
  }

  private Status status(AppsV1beta1Deployment deployment) {
    AppsV1beta1DeploymentStatus status = deployment.getStatus();
    int desiredReplicas = deployment.getSpec().getReplicas();
    if (status == null) {
      return Status.unstable("No status reported yet");
    }

    Integer existing = status.getUpdatedReplicas();
    if (existing == null || desiredReplicas > existing) {
      return Status.unstable("Waiting for all replicas to be updated");
    }

    existing = status.getAvailableReplicas();
    if (existing == null || desiredReplicas > existing) {
      return Status.unstable("Waiting for all replicas to be available");
    }

    existing = status.getReadyReplicas();
    if (existing == null || desiredReplicas > existing) {
      return Status.unstable("Waiting for all replicas to be ready");
    }

    return Status.stable();
  }

  private Status status(V1beta2Deployment deployment) {
    V1beta2DeploymentStatus status = deployment.getStatus();
    int desiredReplicas = deployment.getSpec().getReplicas();
    if (status == null) {
      return Status.unstable("No status reported yet");
    }

    Integer existing = status.getUpdatedReplicas();
    if (existing == null || desiredReplicas > existing) {
      return Status.unstable("Waiting for all replicas to be updated");
    }

    existing = status.getAvailableReplicas();
    if (existing == null || desiredReplicas > existing) {
      return Status.unstable("Waiting for all replicas to be available");
    }

    existing = status.getReadyReplicas();
    if (existing == null || desiredReplicas > existing) {
      return Status.unstable("Waiting for all replicas to be ready");
    }

    return Status.stable();
  }
}
