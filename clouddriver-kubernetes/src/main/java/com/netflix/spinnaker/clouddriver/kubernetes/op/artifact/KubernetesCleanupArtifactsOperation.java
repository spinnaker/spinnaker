/*
 * Copyright 2018 Google, Inc.
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

package com.netflix.spinnaker.clouddriver.kubernetes.op.artifact;

import com.google.common.base.Strings;
import com.netflix.spinnaker.clouddriver.data.task.Task;
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository;
import com.netflix.spinnaker.clouddriver.kubernetes.description.KubernetesResourceProperties;
import com.netflix.spinnaker.clouddriver.kubernetes.description.artifact.KubernetesCleanupArtifactsDescription;
import com.netflix.spinnaker.clouddriver.kubernetes.description.manifest.KubernetesKind;
import com.netflix.spinnaker.clouddriver.kubernetes.description.manifest.KubernetesManifest;
import com.netflix.spinnaker.clouddriver.kubernetes.description.manifest.KubernetesManifestAnnotater;
import com.netflix.spinnaker.clouddriver.kubernetes.description.manifest.KubernetesManifestStrategy;
import com.netflix.spinnaker.clouddriver.kubernetes.op.OperationResult;
import com.netflix.spinnaker.clouddriver.kubernetes.op.handler.KubernetesHandler;
import com.netflix.spinnaker.clouddriver.kubernetes.security.KubernetesV2Credentials;
import com.netflix.spinnaker.clouddriver.model.ArtifactProvider;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import io.kubernetes.client.openapi.models.V1DeleteOptions;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class KubernetesCleanupArtifactsOperation implements AtomicOperation<OperationResult> {
  private final KubernetesCleanupArtifactsDescription description;
  private final KubernetesV2Credentials credentials;
  private final String accountName;
  private final ArtifactProvider artifactProvider;
  private static final String OP_NAME = "CLEANUP_KUBERNETES_ARTIFACTS";

  public KubernetesCleanupArtifactsOperation(
      KubernetesCleanupArtifactsDescription description, ArtifactProvider artifactProvider) {
    this.description = description;
    this.credentials = description.getCredentials().getCredentials();
    this.accountName = description.getCredentials().getName();
    this.artifactProvider = artifactProvider;
  }

  private static Task getTask() {
    return TaskRepository.threadLocalTask.get();
  }

  @Override
  public OperationResult operate(List priorOutputs) {
    OperationResult result = new OperationResult();

    List<Artifact> artifacts =
        description.getManifests().stream()
            .map(this::artifactsToDelete)
            .flatMap(Collection::stream)
            .collect(Collectors.toList());

    artifacts.forEach(
        a -> {
          String type = a.getType();
          if (!type.startsWith("kubernetes/")) {
            log.warn("Non-kubernetes type deletion requested...");
            return;
          }
          String kind = type.substring("kubernetes/".length());
          KubernetesResourceProperties properties =
              credentials.getResourcePropertyRegistry().get(KubernetesKind.fromString(kind));

          getTask().updateStatus(OP_NAME, "Deleting artifact '" + a + '"');
          KubernetesHandler handler = properties.getHandler();
          String name = a.getName();
          if (!Strings.isNullOrEmpty(a.getVersion())) {
            name = String.join("-", name, a.getVersion());
          }
          result.merge(
              handler.delete(credentials, a.getLocation(), name, null, new V1DeleteOptions()));
        });

    result.setManifests(null);
    return result;
  }

  private List<Artifact> artifactsToDelete(KubernetesManifest manifest) {
    KubernetesManifestStrategy strategy = KubernetesManifestAnnotater.getStrategy(manifest);
    OptionalInt optionalMaxVersionHistory = strategy.getMaxVersionHistory();
    if (!optionalMaxVersionHistory.isPresent()) {
      return new ArrayList<>();
    }

    int maxVersionHistory = optionalMaxVersionHistory.getAsInt();
    Optional<Artifact> optional = KubernetesManifestAnnotater.getArtifact(manifest, accountName);
    if (!optional.isPresent()) {
      return new ArrayList<>();
    }

    Artifact artifact = optional.get();

    List<Artifact> artifacts =
        artifactProvider
            .getArtifacts(artifact.getType(), artifact.getName(), artifact.getLocation()).stream()
            .filter(a -> accountName.equals(a.getMetadata("account")))
            .collect(Collectors.toList());

    if (maxVersionHistory >= artifacts.size()) {
      return new ArrayList<>();
    } else {
      return artifacts.subList(0, artifacts.size() - maxVersionHistory);
    }
  }
}
