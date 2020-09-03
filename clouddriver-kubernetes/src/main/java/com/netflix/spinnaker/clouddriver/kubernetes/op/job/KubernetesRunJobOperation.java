/*
 * Copyright 2019 Armory
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

package com.netflix.spinnaker.clouddriver.kubernetes.op.job;

import com.netflix.spinnaker.clouddriver.data.task.Task;
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository;
import com.netflix.spinnaker.clouddriver.kubernetes.artifact.ResourceVersioner;
import com.netflix.spinnaker.clouddriver.kubernetes.description.job.KubernetesRunJobOperationDescription;
import com.netflix.spinnaker.clouddriver.kubernetes.description.manifest.KubernetesDeployManifestDescription;
import com.netflix.spinnaker.clouddriver.kubernetes.description.manifest.KubernetesKind;
import com.netflix.spinnaker.clouddriver.kubernetes.description.manifest.KubernetesManifest;
import com.netflix.spinnaker.clouddriver.kubernetes.description.manifest.KubernetesManifestAnnotater;
import com.netflix.spinnaker.clouddriver.kubernetes.description.manifest.KubernetesManifestStrategy.DeployStrategy;
import com.netflix.spinnaker.clouddriver.kubernetes.op.OperationResult;
import com.netflix.spinnaker.clouddriver.kubernetes.op.manifest.KubernetesDeployManifestOperation;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation;
import com.netflix.spinnaker.moniker.Moniker;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class KubernetesRunJobOperation
    implements AtomicOperation<KubernetesRunJobDeploymentResult> {
  private static final Logger log = LoggerFactory.getLogger(KubernetesRunJobOperation.class);
  private static final String OP_NAME = "RUN_KUBERNETES_JOB";
  private final KubernetesRunJobOperationDescription description;
  private final ResourceVersioner resourceVersioner;
  private final boolean appendSuffix;

  public KubernetesRunJobOperation(
      KubernetesRunJobOperationDescription description,
      ResourceVersioner resourceVersioner,
      boolean appendSuffix) {
    this.description = description;
    this.resourceVersioner = resourceVersioner;
    this.appendSuffix = appendSuffix;
  }

  private static Task getTask() {
    return TaskRepository.threadLocalTask.get();
  }

  @Override
  public KubernetesRunJobDeploymentResult operate(List<KubernetesRunJobDeploymentResult> _unused) {
    getTask().updateStatus(OP_NAME, "Running Kubernetes job...");
    KubernetesManifest jobSpec = this.description.getManifest();
    KubernetesKind kind = jobSpec.getKind();
    if (!kind.equals(KubernetesKind.JOB)) {
      throw new IllegalArgumentException("Only kind of Job is accepted for the Run Job operation.");
    }

    jobSpec.computeIfAbsent("metadata", k -> new HashMap<>());

    if (!this.description.getNamespace().isEmpty()) {
      jobSpec.setNamespace(this.description.getNamespace());
    }

    if (appendSuffix && !jobSpec.hasGenerateName()) {
      log.warn(
          "Appending a random suffix to job with name {} before deploying. In Spinnaker 1.22, this suffix"
              + " will no longer be added. To continue having a random suffix added, please update the job"
              + " to specify a metadata.generateName. To immediately disable this suffix, set"
              + " kubernetes.jobs.addSuffix to false in your clouddriver config.",
          jobSpec.getName());
      String currentName = jobSpec.getName();
      String postfix = Long.toHexString(Double.doubleToLongBits(Math.random()));
      jobSpec.setName(currentName + "-" + postfix);
    } else {
      // We require that the recreate strategy be used; this is because jobs are immutable and
      // trying to re-run a job with apply will either:
      // (1) succeed and leave the job unchanged (but will not trigger a re-run)
      // (2) fail if we try to change anything
      // As the purpose of a run job stage is to ensure that each execution causes a job to run,
      // we'll force a new job to be created each time.
      KubernetesManifestAnnotater.setDeploymentStrategy(jobSpec, DeployStrategy.RECREATE);
    }

    KubernetesDeployManifestDescription deployManifestDescription =
        new KubernetesDeployManifestDescription();
    // setup description
    List<KubernetesManifest> manifests = new ArrayList<>();
    manifests.add(jobSpec);

    Moniker moniker = new Moniker();
    moniker.setApp(description.getApplication());

    deployManifestDescription.setManifests(manifests);
    deployManifestDescription.setRequiredArtifacts(description.getRequiredArtifacts());
    deployManifestDescription.setOptionalArtifacts(description.getOptionalArtifacts());
    deployManifestDescription.setSource(KubernetesDeployManifestDescription.Source.text);
    deployManifestDescription.setCredentials(description.getCredentials());
    deployManifestDescription.setAccount(description.getAccount());
    deployManifestDescription.setMoniker(moniker);

    KubernetesDeployManifestOperation deployManifestOperation =
        new KubernetesDeployManifestOperation(deployManifestDescription, resourceVersioner);
    OperationResult operationResult = deployManifestOperation.operate(new ArrayList<>());
    KubernetesRunJobDeploymentResult deploymentResult =
        new KubernetesRunJobDeploymentResult(operationResult);
    Map<String, List<String>> deployedNames = deploymentResult.getDeployedNamesByLocation();
    for (Map.Entry<String, Set<String>> e :
        operationResult.getManifestNamesByNamespace().entrySet()) {
      deployedNames.put(e.getKey(), new ArrayList<>(e.getValue()));
    }
    deploymentResult.setDeployedNamesByLocation(deployedNames);
    return deploymentResult;
  }
}
