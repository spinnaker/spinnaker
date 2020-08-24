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
import com.google.common.collect.ImmutableList;
import com.netflix.spinnaker.clouddriver.data.task.Task;
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository;
import com.netflix.spinnaker.clouddriver.kubernetes.caching.view.provider.ArtifactProvider;
import com.netflix.spinnaker.clouddriver.kubernetes.description.KubernetesResourceProperties;
import com.netflix.spinnaker.clouddriver.kubernetes.description.artifact.KubernetesCleanupArtifactsDescription;
import com.netflix.spinnaker.clouddriver.kubernetes.description.manifest.KubernetesKind;
import com.netflix.spinnaker.clouddriver.kubernetes.description.manifest.KubernetesManifest;
import com.netflix.spinnaker.clouddriver.kubernetes.description.manifest.KubernetesManifestAnnotater;
import com.netflix.spinnaker.clouddriver.kubernetes.description.manifest.KubernetesManifestStrategy;
import com.netflix.spinnaker.clouddriver.kubernetes.op.OperationResult;
import com.netflix.spinnaker.clouddriver.kubernetes.security.KubernetesCredentials;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import io.kubernetes.client.openapi.models.V1DeleteOptions;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class KubernetesCleanupArtifactsOperation implements AtomicOperation<OperationResult> {
  private static final Logger log =
      LoggerFactory.getLogger(KubernetesCleanupArtifactsOperation.class);
  private final KubernetesCleanupArtifactsDescription description;
  private final KubernetesCredentials credentials;
  @Nonnull private final String accountName;
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
  public OperationResult operate(List<OperationResult> priorOutputs) {
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
          String name = a.getName();
          if (!Strings.isNullOrEmpty(a.getVersion())) {
            name = String.join("-", name, a.getVersion());
          }
          result.merge(
              properties
                  .getHandler()
                  .delete(credentials, a.getLocation(), name, null, new V1DeleteOptions()));
        });

    result.setManifests(null);
    return result;
  }

  private ImmutableList<Artifact> artifactsToDelete(KubernetesManifest manifest) {
    KubernetesManifestStrategy strategy = KubernetesManifestAnnotater.getStrategy(manifest);
    OptionalInt optionalMaxVersionHistory = strategy.getMaxVersionHistory();
    if (!optionalMaxVersionHistory.isPresent()) {
      return ImmutableList.of();
    }

    int maxVersionHistory = optionalMaxVersionHistory.getAsInt();
    Optional<Artifact> optional = KubernetesManifestAnnotater.getArtifact(manifest, accountName);
    if (!optional.isPresent()) {
      return ImmutableList.of();
    }

    Artifact artifact = optional.get();

    ImmutableList<Artifact> artifacts =
        artifactProvider.getArtifacts(
            manifest.getKind(), artifact.getName(), artifact.getLocation(), accountName);
    if (maxVersionHistory >= artifacts.size()) {
      return ImmutableList.of();
    } else {
      return artifacts.subList(0, artifacts.size() - maxVersionHistory);
    }
  }
}
