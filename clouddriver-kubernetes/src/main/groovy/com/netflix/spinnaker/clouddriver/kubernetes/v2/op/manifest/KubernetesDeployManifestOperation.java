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

package com.netflix.spinnaker.clouddriver.kubernetes.v2.op.manifest;

import com.netflix.spinnaker.clouddriver.data.task.Task;
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository;
import com.netflix.spinnaker.clouddriver.kubernetes.KubernetesCloudProvider;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.artifact.ArtifactReplacer.ReplaceResult;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.artifact.KubernetesArtifactConverter;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.KubernetesResourceProperties;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.KubernetesResourcePropertyRegistry;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesDeployManifestDescription;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesKind;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesManifest;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesManifestAnnotater;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesManifestStrategy;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesSourceCapacity;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.op.OperationResult;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.op.handler.CanScale;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.op.handler.KubernetesHandler;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.security.KubernetesV2Credentials;
import com.netflix.spinnaker.clouddriver.model.ArtifactProvider;
import com.netflix.spinnaker.clouddriver.names.NamerRegistry;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import com.netflix.spinnaker.moniker.Moniker;
import com.netflix.spinnaker.moniker.Namer;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
public class KubernetesDeployManifestOperation implements AtomicOperation<OperationResult> {
  private final KubernetesDeployManifestDescription description;
  private final KubernetesV2Credentials credentials;
  private final ArtifactProvider provider;
  private final Namer namer;
  private final KubernetesResourcePropertyRegistry registry;
  private final String accountName;
  private static final String OP_NAME = "DEPLOY_KUBERNETES_MANIFEST";

  public KubernetesDeployManifestOperation(KubernetesDeployManifestDescription description, KubernetesResourcePropertyRegistry registry, ArtifactProvider provider) {
    this.description = description;
    this.credentials = (KubernetesV2Credentials) description.getCredentials().getCredentials();
    this.registry = registry;
    this.provider = provider;
    this.accountName = description.getCredentials().getName();
    this.namer = NamerRegistry.lookup()
        .withProvider(KubernetesCloudProvider.getID())
        .withAccount(accountName)
        .withResource(KubernetesManifest.class);
  }

  private static Task getTask() {
    return TaskRepository.threadLocalTask.get();
  }

  @Override
  public OperationResult operate(List _unused) {
    getTask().updateStatus(OP_NAME, "Beginning deployment of manifest...");

    List<KubernetesManifest> inputManifests = description.getManifests();
    List<KubernetesManifest> deployManifests = new ArrayList<>();
    if (inputManifests == null || inputManifests.isEmpty()) {
      log.warn("Relying on deprecated single manifest input: " + description.getManifest());
      inputManifests = Collections.singletonList(description.getManifest());
    }

    inputManifests = inputManifests.stream().filter(Objects::nonNull).collect(Collectors.toList());

    List<Artifact> requiredArtifacts = description.getRequiredArtifacts();
    if (requiredArtifacts == null) {
      requiredArtifacts = new ArrayList<>();
    }

    List<Artifact> optionalArtifacts = description.getOptionalArtifacts();
    if (optionalArtifacts == null) {
      optionalArtifacts = new ArrayList<>();
    }

    List<Artifact> artifacts = new ArrayList<>();
    artifacts.addAll(requiredArtifacts);
    artifacts.addAll(optionalArtifacts);

    Set<Artifact> boundArtifacts = new HashSet<>();

    for (KubernetesManifest manifest : inputManifests) {
      if (StringUtils.isEmpty(manifest.getNamespace()) && manifest.getKind().isNamespaced()) {
        manifest.setNamespace(credentials.getDefaultNamespace());
      }

      KubernetesResourceProperties properties = findResourceProperties(manifest);
      if (properties == null) {
        throw new IllegalArgumentException("Unsupported Kubernetes object kind '" + manifest.getKind().toString() + "', unable to continue.");
      }
      KubernetesHandler deployer = properties.getHandler();
      if (deployer == null) {
        throw new IllegalArgumentException("No deployer available for Kubernetes object kind '" + manifest.getKind().toString() + "', unable to continue.");
      }

      getTask().updateStatus(OP_NAME, "Swapping out artifacts in " + manifest.getFullResourceName() + " from context...");
      ReplaceResult replaceResult = deployer.replaceArtifacts(manifest, artifacts, description.getAccount());
      deployManifests.add(replaceResult.getManifest());
      boundArtifacts.addAll(replaceResult.getBoundArtifacts());
    }

    Set<Artifact> unboundArtifacts = new HashSet<>(requiredArtifacts);
    unboundArtifacts.removeAll(boundArtifacts);

    getTask().updateStatus(OP_NAME, "Checking if all requested artifacts were bound...");
    if (!unboundArtifacts.isEmpty()) {
      throw new IllegalArgumentException("The following artifacts could not be bound: '" + unboundArtifacts + "' . Failing the stage as this is likely a configuration error.");
    }

    getTask().updateStatus(OP_NAME, "Sorting manifests by priority...");
    deployManifests.sort(Comparator.comparingInt(m -> findResourceProperties(m).getHandler().deployPriority()));
    getTask().updateStatus(OP_NAME, "Deploy order is: " + String.join(", ", deployManifests.stream().map(KubernetesManifest::getFullResourceName).collect(Collectors.toList())));

    OperationResult result = new OperationResult();
    for (KubernetesManifest manifest : deployManifests) {
      KubernetesResourceProperties properties = findResourceProperties(manifest);
      KubernetesManifestStrategy strategy = KubernetesManifestAnnotater.getStrategy(manifest);
      boolean versioned = isVersioned(properties, strategy);
      boolean useSourceCapacity = isUseSourceCapacity(strategy);

      KubernetesArtifactConverter converter = versioned ? properties.getVersionedConverter() : properties.getUnversionedConverter();
      KubernetesHandler deployer = properties.getHandler();

      Moniker moniker = cloneMoniker(description.getMoniker());
      if (StringUtils.isEmpty(moniker.getCluster())) {
        moniker.setCluster(manifest.getFullResourceName());
      }

      Artifact artifact = converter.toArtifact(provider, manifest, description.getAccount());

      String version = artifact.getVersion();
      if (StringUtils.isNotEmpty(version) && version.startsWith("v")) {
        try {
          moniker.setSequence(Integer.valueOf(version.substring(1)));
        } catch (NumberFormatException e) {
          log.warn("Malformed moniker version {}", version, e);
        }
      }

      getTask().updateStatus(OP_NAME, "Annotating manifest " + manifest.getFullResourceName() + " with artifact, relationships & moniker...");
      KubernetesManifestAnnotater.annotateManifest(manifest, artifact);

      if (useSourceCapacity && deployer instanceof CanScale) { 
        Double replicas = KubernetesSourceCapacity.getSourceCapacity(manifest, credentials);
        if (replicas != null) {
          manifest.setReplicas(replicas);
        } 
      }
 
      namer.applyMoniker(manifest, moniker);
      manifest.setName(converter.getDeployedName(artifact));

      getTask().updateStatus(OP_NAME, "Swapping out artifacts in " + manifest.getFullResourceName() + " from other deployments...");
      ReplaceResult replaceResult = deployer.replaceArtifacts(manifest, new ArrayList<>(result.getCreatedArtifacts()), description.getAccount());
      boundArtifacts.addAll(replaceResult.getBoundArtifacts());
      manifest = replaceResult.getManifest();

      getTask().updateStatus(OP_NAME, "Submitting manifest " + manifest.getFullResourceName() + " to kubernetes master...");
      result.merge(deployer.deploy(credentials, manifest));

      result.getCreatedArtifacts().add(artifact);
    }

    result.getBoundArtifacts().addAll(boundArtifacts);
    result.removeSensitiveKeys(registry, accountName);
    return result;
  }

  private boolean isVersioned(KubernetesResourceProperties properties, KubernetesManifestStrategy strategy) {
    if (strategy.getVersioned() != null) {
      return strategy.getVersioned();
    }

    if (description.getVersioned() != null) {
      return description.getVersioned();
    }

    return properties.isVersioned();
  }

  private boolean isUseSourceCapacity(KubernetesManifestStrategy strategy) {
    if (strategy.getUseSourceCapacity() != null) {
      return strategy.getUseSourceCapacity();
    }

    return false;
  }

  // todo(lwander): move to kork
  private static Moniker cloneMoniker(Moniker inp) {
    return Moniker.builder()
        .app(inp.getApp())
        .cluster(inp.getCluster())
        .stack(inp.getStack())
        .detail(inp.getDetail())
        .sequence(inp.getSequence())
        .build();
  }

  private KubernetesResourceProperties findResourceProperties(KubernetesManifest manifest) {
    KubernetesKind kind = manifest.getKind();
    getTask().updateStatus(OP_NAME, "Finding deployer for " + kind + "...");
    return registry.get(accountName, kind);
  }
}
