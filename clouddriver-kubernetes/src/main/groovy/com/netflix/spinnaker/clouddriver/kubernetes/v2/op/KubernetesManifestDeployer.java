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

package com.netflix.spinnaker.clouddriver.kubernetes.v2.op;

import com.netflix.spinnaker.clouddriver.data.task.Task;
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository;
import com.netflix.spinnaker.clouddriver.deploy.DeploymentResult;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.KubernetesAugmentedManifest;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.KubernetesKind;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.KubernetesManifestOperationDescription;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.op.deployer.KubernetesDeployer;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.op.deployer.KubernetesIngressDeployer;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.op.deployer.KubernetesReplicaSetDeployer;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.op.deployer.KubernetesServiceDeployer;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.security.KubernetesV2Credentials;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

public class KubernetesManifestDeployer implements AtomicOperation<DeploymentResult> {
  private final KubernetesManifestOperationDescription description;
  private final KubernetesV2Credentials credentials;
  private static final String OP_NAME = "DEPLOY_KUBERNETES_MANIFESTS";

  @Autowired
  private KubernetesReplicaSetDeployer replicaSetDeployer;

  @Autowired
  private KubernetesServiceDeployer serviceDeployer;

  @Autowired
  private KubernetesIngressDeployer ingressDeployer;

  public KubernetesManifestDeployer(KubernetesManifestOperationDescription description) {
    this.description = description;
    this.credentials = (KubernetesV2Credentials) description.getCredentials().getCredentials();
  }

  private static Task getTask() {
    return TaskRepository.threadLocalTask.get();
  }

  @Override
  public DeploymentResult operate(List _unused) {
    getTask().updateStatus(OP_NAME, "Beginning deployment of manifests");

    return description.getManifests()
        .stream()
        .map(this::dispatchDeployer)
        .reduce((l, r) -> {
          r.getServerGroupNameByRegion().putAll(l.getServerGroupNameByRegion());
          r.getDeployedNames().addAll(l.getDeployedNames());
          return r;
        }).orElseThrow(() -> new IllegalArgumentException("Expected at least one manifest to deploy"));
  }

  private KubernetesDeployer findDeployer(KubernetesKind kind) {
    switch (kind) {
      case REPLICA_SET:
        return replicaSetDeployer;
      case SERVICE:
        return serviceDeployer;
      case INGRESS:
        return ingressDeployer;
      default:
        throw new IllegalArgumentException("Kind " + kind + " is not supported yet");
    }
  }

  private DeploymentResult dispatchDeployer(KubernetesAugmentedManifest pair) {
    KubernetesKind kind = pair.getManifest().getKind();
    getTask().updateStatus(OP_NAME, "Finding deployer for " + kind);
    return findDeployer(kind).deployManifestPair(credentials, pair);
  }
}
