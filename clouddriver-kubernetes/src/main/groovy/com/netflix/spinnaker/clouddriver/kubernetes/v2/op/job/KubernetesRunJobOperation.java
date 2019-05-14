/*
 * Copyright 2019 Armory
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
 */

package com.netflix.spinnaker.clouddriver.kubernetes.v2.op.job;

import com.netflix.spinnaker.clouddriver.data.task.Task;
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository;
import com.netflix.spinnaker.clouddriver.deploy.DeploymentResult;
import com.netflix.spinnaker.clouddriver.kubernetes.KubernetesCloudProvider;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.KubernetesResourcePropertyRegistry;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.job.KubernetesRunJobOperationDescription;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesDeployManifestDescription;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesKind;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesManifest;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.op.OperationResult;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.op.manifest.KubernetesDeployManifestOperation;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.security.KubernetesV2Credentials;
import com.netflix.spinnaker.clouddriver.model.ArtifactProvider;
import com.netflix.spinnaker.clouddriver.names.NamerRegistry;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation;
import com.netflix.spinnaker.moniker.Moniker;
import com.netflix.spinnaker.moniker.Namer;
import java.util.*;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class KubernetesRunJobOperation implements AtomicOperation<DeploymentResult> {
  private static final String OP_NAME = "RUN_KUBERNETES_JOB";
  private final KubernetesRunJobOperationDescription description;
  private final KubernetesV2Credentials credentials;
  private final KubernetesResourcePropertyRegistry registry;
  private final Namer namer;
  private final ArtifactProvider provider;

  public KubernetesRunJobOperation(
      KubernetesRunJobOperationDescription description,
      KubernetesResourcePropertyRegistry registry,
      ArtifactProvider provider) {
    this.description = description;
    this.credentials = (KubernetesV2Credentials) description.getCredentials().getCredentials();
    this.registry = registry;
    this.provider = provider;
    this.namer =
        NamerRegistry.lookup()
            .withProvider(KubernetesCloudProvider.getID())
            .withAccount(description.getCredentials().getName())
            .withResource(KubernetesManifest.class);
  }

  private static Task getTask() {
    return TaskRepository.threadLocalTask.get();
  }

  public DeploymentResult operate(List _unused) {
    getTask().updateStatus(OP_NAME, "Running Kubernetes job...");
    KubernetesManifest jobSpec = this.description.getManifest();
    KubernetesKind kind = jobSpec.getKind();
    if (kind != KubernetesKind.JOB) {
      throw new IllegalArgumentException(
          "Only kind of Job is accepted for the V2 Run Job operation.");
    }

    if (jobSpec.get("metadata") == null) {
      jobSpec.put("metadata", new HashMap<>());
    }

    if (!this.description.getNamespace().isEmpty()) {
      jobSpec.setNamespace(this.description.getNamespace());
    }
    // append a random string to the job name to avoid collision
    String currentName = jobSpec.getName();
    String postfix = Long.toHexString(Double.doubleToLongBits(Math.random()));
    jobSpec.setName(currentName + "-" + postfix);

    KubernetesDeployManifestDescription deployManifestDescription =
        new KubernetesDeployManifestDescription();
    // setup description
    List<KubernetesManifest> manifests = new ArrayList<>();
    manifests.add(jobSpec);

    Moniker moniker = new Moniker();
    moniker.setApp(description.getApplication());

    deployManifestDescription.setManifests(manifests);
    deployManifestDescription.setSource(KubernetesDeployManifestDescription.Source.text);
    deployManifestDescription.setMoniker(moniker);
    deployManifestDescription.setCredentials(description.getCredentials());

    KubernetesDeployManifestOperation deployManifestOperation =
        new KubernetesDeployManifestOperation(deployManifestDescription, registry, provider);
    OperationResult operationResult = deployManifestOperation.operate(new ArrayList());
    DeploymentResult deploymentResult = new DeploymentResult();
    Map<String, List<String>> deployedNames = deploymentResult.getDeployedNamesByLocation();
    for (Map.Entry<String, Set<String>> e :
        operationResult.getManifestNamesByNamespace().entrySet()) {
      deployedNames.put(e.getKey(), new ArrayList(e.getValue()));
    }
    deploymentResult.setDeployedNamesByLocation(deployedNames);
    return deploymentResult;
  }
}
