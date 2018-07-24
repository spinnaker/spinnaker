/*
 * Copyright 2018 Google, Inc.
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

package com.netflix.spinnaker.clouddriver.kubernetes.v2.op.manifest;

import com.google.common.collect.Sets;
import com.netflix.spinnaker.clouddriver.data.task.Task;
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.artifact.ArtifactReplacer.ReplaceResult;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.KubernetesCoordinates;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.KubernetesResourceProperties;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.KubernetesResourcePropertyRegistry;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesPatchManifestDescription;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.op.OperationResult;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.op.handler.KubernetesHandler;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.security.KubernetesV2Credentials;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Slf4j
public class KubernetesPatchManifestOperation implements AtomicOperation<OperationResult> {
  private final KubernetesPatchManifestDescription description;
  private final KubernetesV2Credentials credentials;
  private final KubernetesResourcePropertyRegistry registry;
  private final String accountName;
  private static final String OP_NAME = "PATCH_KUBERNETES_MANIFEST";

  public KubernetesPatchManifestOperation(KubernetesPatchManifestDescription description,
    KubernetesResourcePropertyRegistry registry) {
    this.description = description;
    this.credentials = (KubernetesV2Credentials) description.getCredentials().getCredentials();
    this.registry = registry;
    this.accountName = description.getCredentials().getName();
  }

  private static Task getTask() {
    return TaskRepository.threadLocalTask.get();
  }

  @Override
  public OperationResult operate(List _unused) {
    updateStatus("Beginning patching of manifest");
    KubernetesCoordinates objToPatch = description.getPointCoordinates();

    updateStatus("Finding patch handler for " + objToPatch + "...");
    KubernetesHandler patchHandler = findPatchHandler(objToPatch);

    updateStatus("Swapping out artifacts in " + objToPatch + " from context...");
    ReplaceResult replaceResult = replaceArtifacts(objToPatch, patchHandler);

    updateStatus("Submitting manifest " + description.getManifestName() + " to Kubernetes master...");
    OperationResult result = new OperationResult();
    result.merge(patchHandler.patch(credentials, objToPatch.getNamespace(), objToPatch.getName(),
      description.getOptions(), replaceResult.getManifest()));

    result.getBoundArtifacts().addAll(replaceResult.getBoundArtifacts());
    result.removeSensitiveKeys(registry, accountName);
    return result;
  }

  private void updateStatus(String status) {
    getTask().updateStatus(OP_NAME, status);
  }

  private ReplaceResult replaceArtifacts(KubernetesCoordinates objToPatch,
    KubernetesHandler patchHandler) {
    List<Artifact> allArtifacts = description.getAllArtifacts() == null ? new ArrayList<>() :
      description.getAllArtifacts();

    ReplaceResult replaceResult = patchHandler.replaceArtifacts(description.getPatchBody(),
      allArtifacts, objToPatch.getNamespace(), description.getAccount());

    if (description.getRequiredArtifacts() != null) {
      Set<Artifact> unboundArtifacts = Sets.difference(new HashSet<>(description.getRequiredArtifacts()),
        replaceResult.getBoundArtifacts());
      if (!unboundArtifacts.isEmpty()) {
        throw new IllegalArgumentException("The following required artifacts could not be bound: '" +
          unboundArtifacts + "' . Failing the stage as this is likely a configuration error.");
      }
    }
    return replaceResult;
  }

  private KubernetesHandler findPatchHandler(KubernetesCoordinates objToPatch) {
    KubernetesResourceProperties properties = registry.get(accountName, objToPatch.getKind());
    if (properties == null) {
      throw new IllegalArgumentException("Unsupported Kubernetes object kind '" +
        objToPatch.getKind() + "', unable to continue");
    }
    KubernetesHandler patchHandler = properties.getHandler();
    if (patchHandler == null) {
      throw new IllegalArgumentException("No patch handler available for Kubernetes object kind ' "
        + objToPatch.getKind() + "', unable to continue");
    }
    return patchHandler;
  }
}
