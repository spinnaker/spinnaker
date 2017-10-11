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

import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.KubernetesSpinnakerKindMap;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesApiVersion;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesKind;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.security.KubernetesV2Credentials;
import com.netflix.spinnaker.clouddriver.model.ServerGroup;
import com.netflix.spinnaker.clouddriver.model.ServerGroup.Capacity;
import io.kubernetes.client.models.AppsV1beta1Deployment;
import org.springframework.stereotype.Component;

@Component
public class KubernetesDeploymentDeployer extends KubernetesDeployer<AppsV1beta1Deployment> implements CanResize {
  @Override
  public Class<AppsV1beta1Deployment> getDeployedClass() {
    return AppsV1beta1Deployment.class;
  }

  @Override
  public KubernetesKind kind() {
    return KubernetesKind.DEPLOYMENT;
  }

  @Override
  public KubernetesApiVersion apiVersion() {
    return KubernetesApiVersion.APPS_V1BETA1;
  }

  @Override
  void deploy(KubernetesV2Credentials credentials, AppsV1beta1Deployment resource) {
    String namespace = resource.getMetadata().getNamespace();
    String name = resource.getMetadata().getName();
    AppsV1beta1Deployment current = credentials.readDeployment(namespace, name);
    if (current != null) {
      credentials.patchDeployment(current, resource);
    } else {
      credentials.createDeployment(resource);
    }
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
  public void resize(KubernetesV2Credentials credentials, String namespace, String name, Capacity capacity) {
    credentials.resizeDeployment(namespace, name, capacity.getDesired());
  }
}
