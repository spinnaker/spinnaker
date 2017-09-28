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
import com.netflix.spinnaker.clouddriver.kubernetes.v2.artifact.KubernetesArtifactConverter;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.KubernetesApiVersion;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.KubernetesAugmentedManifest;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.KubernetesKind;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.KubernetesManifest;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.KubernetesManifestAnnotater;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.KubernetesManifestOperationDescription;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.KubernetesManifestSpinnakerRelationships;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.KubernetesResourceProperties;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.KubernetesResourcePropertyRegistry;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.security.KubernetesV2Credentials;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

public class KubernetesManifestDeployer implements AtomicOperation<DeploymentResult> {
  private final KubernetesManifestOperationDescription description;
  private final KubernetesV2Credentials credentials;
  private static final String OP_NAME = "DEPLOY_KUBERNETES_MANIFESTS";

  @Autowired
  private KubernetesResourcePropertyRegistry registry;

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

  private KubernetesResourceProperties findResourceProperties(KubernetesManifest manifest) {
    KubernetesApiVersion apiVersion = manifest.getApiVersion();
    KubernetesKind kind = manifest.getKind();
    getTask().updateStatus(OP_NAME, "Finding deployer for " + apiVersion + "/" + kind);
    return registry.lookup().withApiVersion(apiVersion).withKind(kind);
  }

  private void ensureMetadata(KubernetesResourceProperties properties, KubernetesAugmentedManifest augmentedManifest) {
    KubernetesManifest manifest = augmentedManifest.getManifest();
    KubernetesAugmentedManifest.Metadata metadata = augmentedManifest.getMetadata();

    if (metadata.getMoniker() == null) {
      throw new IllegalStateException("Every resource must be deployed with a moniker.");
    }

    if (metadata.getRelationships() == null) {
      metadata.setRelationships(new KubernetesManifestSpinnakerRelationships());
    }

    if (metadata.getArtifact() == null) {
      metadata.setArtifact(properties.getConverter().toArtifact(manifest));
    }

    if (StringUtils.isEmpty(manifest.getNamespace())) {
      manifest.setNamespace(credentials.getDefaultNamespace());
    }
  }

  private void annotateUsingMetadata(KubernetesAugmentedManifest augmentedManifest) {
    KubernetesManifestAnnotater.annotateManifest(augmentedManifest.getManifest(), augmentedManifest.getMetadata());
  }

  private void versionResource(KubernetesResourceProperties properties, KubernetesAugmentedManifest augmentedManifest) {
    KubernetesManifest manifest = augmentedManifest.getManifest();
    KubernetesAugmentedManifest.Metadata metadata = augmentedManifest.getMetadata();
    Artifact artifact = metadata.getArtifact();
    KubernetesArtifactConverter converter = properties.getConverter();

    manifest.setName(converter.getDeployedName(artifact));
  }

  private DeploymentResult dispatchDeployer(KubernetesAugmentedManifest augmentedManifest) {
    KubernetesResourceProperties properties = findResourceProperties(augmentedManifest.getManifest());
    KubernetesManifest manifest = augmentedManifest.getManifest();

    ensureMetadata(properties, augmentedManifest);
    annotateUsingMetadata(augmentedManifest);
    versionResource(properties, augmentedManifest);

    return properties.getDeployer().deployAugmentedManifest(credentials, manifest);
  }
}
