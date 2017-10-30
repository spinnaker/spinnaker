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
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.KubernetesSpinnakerKindMap;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesKind;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesManifest;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.security.KubernetesV2Credentials;
import com.netflix.spinnaker.clouddriver.model.Manifest.Status;
import com.netflix.spinnaker.clouddriver.model.ServerGroup.Capacity;
import io.kubernetes.client.models.AppsV1beta1Deployment;
import io.kubernetes.client.models.AppsV1beta1DeploymentStatus;
import io.kubernetes.client.models.V1DeleteOptions;
import io.kubernetes.client.models.V1beta2Deployment;
import io.kubernetes.client.models.V1beta2DeploymentStatus;
import org.springframework.stereotype.Component;

@Component
public class KubernetesDeploymentDeployer extends KubernetesDeployer implements CanResize, CanDelete<V1DeleteOptions> {
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
    return KubernetesSpinnakerKindMap.SpinnakerKind.UNCLASSIFIED;
  }

  @Override
  public Status status(KubernetesManifest manifest) {
    switch (manifest.getApiVersion()) {
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

  @Override
  public void resize(KubernetesV2Credentials credentials, String namespace, String name, Capacity capacity) {
    credentials.resizeDeployment(namespace, name, capacity.getDesired());
  }

  @Override
  public Class<V1DeleteOptions> getDeleteOptionsClass() {
    return V1DeleteOptions.class;
  }

  @Override
  public void delete(KubernetesV2Credentials credentials, String namespace, String name, V1DeleteOptions deleteOptions) {
    credentials.deleteDeployment(namespace, name, deleteOptions);
  }
}
