/*
 * Copyright 2017 Google, Inc.
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

package com.netflix.spinnaker.clouddriver.kubernetes.op.manifest;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;
import com.netflix.spinnaker.clouddriver.data.task.Task;
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository;
import com.netflix.spinnaker.clouddriver.kubernetes.artifact.ArtifactConverter;
import com.netflix.spinnaker.clouddriver.kubernetes.artifact.ArtifactReplacer.ReplaceResult;
import com.netflix.spinnaker.clouddriver.kubernetes.artifact.ResourceVersioner;
import com.netflix.spinnaker.clouddriver.kubernetes.description.KubernetesCoordinates;
import com.netflix.spinnaker.clouddriver.kubernetes.description.KubernetesResourceProperties;
import com.netflix.spinnaker.clouddriver.kubernetes.description.manifest.*;
import com.netflix.spinnaker.clouddriver.kubernetes.description.manifest.KubernetesManifestStrategy.Versioned;
import com.netflix.spinnaker.clouddriver.kubernetes.op.OperationResult;
import com.netflix.spinnaker.clouddriver.kubernetes.op.handler.*;
import com.netflix.spinnaker.clouddriver.kubernetes.security.KubernetesCredentials;
import com.netflix.spinnaker.clouddriver.kubernetes.security.KubernetesSelectorList;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import com.netflix.spinnaker.moniker.Moniker;
import java.util.*;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class KubernetesDeployManifestOperation implements AtomicOperation<OperationResult> {
  private static final Logger log =
      LoggerFactory.getLogger(KubernetesDeployManifestOperation.class);
  private final KubernetesDeployManifestDescription description;
  private final KubernetesCredentials credentials;
  private final ResourceVersioner resourceVersioner;
  @Nonnull private final String accountName;
  private static final String OP_NAME = "DEPLOY_KUBERNETES_MANIFEST";

  public KubernetesDeployManifestOperation(
      KubernetesDeployManifestDescription description, ResourceVersioner resourceVersioner) {
    this.description = description;
    this.credentials = description.getCredentials().getCredentials();
    this.resourceVersioner = resourceVersioner;
    this.accountName = description.getCredentials().getName();
  }

  private static Task getTask() {
    return TaskRepository.threadLocalTask.get();
  }

  @Override
  public OperationResult operate(List<OperationResult> _unused) {
    getTask()
        .updateStatus(
            OP_NAME, "Beginning deployment of manifests in account " + accountName + " ...");

    final List<KubernetesManifest> inputManifests = getManifestsFromDescription();
    sortManifests(inputManifests);
    Map<String, Artifact> allArtifacts = initializeArtifacts();

    OperationResult result = new OperationResult();
    List<ManifestArtifactHolder> toDeploy =
        inputManifests.stream()
            .map(
                manifest -> {
                  KubernetesManifestAnnotater.validateAnnotationsForRolloutStrategies(
                      manifest, description);

                  // Bind artifacts
                  manifest = bindArtifacts(manifest, allArtifacts.values(), result);

                  KubernetesResourceProperties properties = findResourceProperties(manifest);
                  KubernetesManifestStrategy strategy =
                      KubernetesManifestAnnotater.getStrategy(manifest);

                  OptionalInt version =
                      isVersioned(properties, strategy)
                          ? resourceVersioner.getVersion(manifest, credentials)
                          : OptionalInt.empty();

                  Moniker moniker = cloneMoniker(description.getMoniker());
                  version.ifPresent(moniker::setSequence);
                  if (Strings.isNullOrEmpty(moniker.getCluster())) {
                    moniker.setCluster(manifest.getFullResourceName());
                  }

                  Artifact artifact =
                      ArtifactConverter.toArtifact(manifest, description.getAccount(), version);
                  // Artifacts generated in this stage replace any required or optional artifacts
                  // coming from the request
                  allArtifacts.put(getArtifactKey(artifact), artifact);

                  getTask()
                      .updateStatus(
                          OP_NAME,
                          "Annotating manifest "
                              + manifest.getFullResourceName()
                              + " with artifact, relationships & moniker...");
                  KubernetesManifestAnnotater.annotateManifest(manifest, artifact);

                  KubernetesHandler deployer = properties.getHandler();
                  if (strategy.isUseSourceCapacity() && deployer instanceof CanScale) {
                    OptionalInt latestVersion = latestVersion(manifest, version);
                    Integer replicas =
                        KubernetesSourceCapacity.getSourceCapacity(
                            manifest, credentials, latestVersion);
                    if (replicas != null) {
                      manifest.setReplicas(replicas);
                    }
                  }

                  if (deployer instanceof CanReceiveTraffic) {
                    setTrafficAnnotation(description.getServices(), manifest);

                    if (description.isEnableTraffic()) {
                      KubernetesManifestTraffic traffic =
                          KubernetesManifestAnnotater.getTraffic(manifest);
                      applyTraffic(traffic, manifest, inputManifests);
                    }
                  }

                  credentials.getNamer().applyMoniker(manifest, moniker);
                  manifest.setName(artifact.getReference());

                  return new ManifestArtifactHolder(manifest, artifact, strategy);
                })
            .collect(Collectors.toList());

    checkIfArtifactsBound(result);

    KubernetesSelectorList labelSelectors = this.description.getLabelSelectors();

    // kubectl replace doesn't support selectors, so fail if any manifest uses
    // the replace strategy
    if (labelSelectors.isNotEmpty()
        && toDeploy.stream()
            .map((holder) -> holder.getStrategy().getDeployStrategy())
            .anyMatch(
                (strategy) -> strategy == KubernetesManifestStrategy.DeployStrategy.REPLACE)) {
      throw new IllegalArgumentException(
          "label selectors not supported with replace strategy, not deploying");
    }

    toDeploy.forEach(
        holder -> {
          KubernetesResourceProperties properties = findResourceProperties(holder.manifest);
          KubernetesManifestStrategy strategy = holder.strategy;
          KubernetesHandler deployer = properties.getHandler();
          getTask()
              .updateStatus(
                  OP_NAME,
                  "Submitting manifest "
                      + holder.manifest.getFullResourceName()
                      + " to kubernetes master...");
          result.merge(
              deployer.deploy(
                  credentials,
                  holder.manifest,
                  strategy.getDeployStrategy(),
                  strategy.getServerSideApplyStrategy(),
                  getTask(),
                  OP_NAME,
                  labelSelectors));

          result.getCreatedArtifacts().add(holder.artifact);
          getTask()
              .updateStatus(
                  OP_NAME,
                  "Deploy manifest task completed successfully for manifest "
                      + holder.manifest.getFullResourceName()
                      + " in account "
                      + accountName);
        });

    // If a label selector was specified and nothing has been deployed, throw an
    // exception to fail the task if configured to do so.
    if (!description.isAllowNothingSelected()
        && labelSelectors.isNotEmpty()
        && result.getManifests().isEmpty()) {
      throw new IllegalStateException(
          "nothing deployed to account "
              + accountName
              + " with label selector(s) "
              + labelSelectors.toString());
    }
    result.removeSensitiveKeys(credentials.getResourcePropertyRegistry());

    getTask()
        .updateStatus(
            OP_NAME,
            "Deploy manifest task completed successfully for all manifests in account "
                + accountName);
    return result;
  }

  @NotNull
  private OptionalInt latestVersion(KubernetesManifest manifest, OptionalInt version) {
    if (version.isEmpty()) {
      return OptionalInt.empty();
    }
    OptionalInt latestVersion = resourceVersioner.getLatestVersion(manifest, credentials);
    return latestVersion;
  }

  @NotNull
  private List<KubernetesManifest> getManifestsFromDescription() {
    List<KubernetesManifest> inputManifests = description.getManifests();
    if (inputManifests == null || inputManifests.isEmpty()) {
      // The stage currently only supports using the `manifests` field but we need to continue to
      // check `manifest` for backwards compatibility until all existing stages have been updated.
      @SuppressWarnings("deprecation")
      KubernetesManifest manifest = description.getManifest();
      log.warn(
          "Relying on deprecated single manifest input (account: {}, kind: {}, name: {})",
          accountName,
          manifest.getKind(),
          manifest.getName());
      inputManifests = ImmutableList.of(manifest);
    }
    inputManifests = inputManifests.stream().filter(Objects::nonNull).collect(Collectors.toList());
    return inputManifests;
  }

  @NotNull
  private Map<String, Artifact> initializeArtifacts() {
    Map<String, Artifact> allArtifacts = new HashMap<>();
    if (!description.isEnableArtifactBinding()) {
      return allArtifacts;
    }
    // Required artifacts are explicitly set in stage configuration
    if (description.getRequiredArtifacts() != null) {
      description
          .getRequiredArtifacts()
          .forEach(a -> allArtifacts.putIfAbsent(getArtifactKey(a), a));
    }
    // Optional artifacts are taken from the pipeline trigger or pipeline execution context
    if (description.getOptionalArtifacts() != null) {
      description
          .getOptionalArtifacts()
          .forEach(a -> allArtifacts.putIfAbsent(getArtifactKey(a), a));
    }
    return allArtifacts;
  }

  private KubernetesManifest bindArtifacts(
      KubernetesManifest manifest,
      Collection<Artifact> artifacts,
      OperationResult operationResult) {
    getTask()
        .updateStatus(OP_NAME, "Binding artifacts in " + manifest.getFullResourceName() + "...");

    ReplaceResult replaceResult =
        findResourceProperties(manifest)
            .getHandler()
            .replaceArtifacts(manifest, List.copyOf(artifacts), description.getAccount());

    getTask()
        .updateStatus(OP_NAME, "Bound artifacts: " + replaceResult.getBoundArtifacts() + "...");

    operationResult.getBoundArtifacts().addAll(replaceResult.getBoundArtifacts());
    return replaceResult.getManifest();
  }

  private void checkIfArtifactsBound(OperationResult operationResult) {
    getTask().updateStatus(OP_NAME, "Checking if all requested artifacts were bound...");
    if (description.isEnableArtifactBinding()) {
      Set<ArtifactKey> unboundArtifacts =
          Sets.difference(
              ArtifactKey.fromArtifacts(description.getRequiredArtifacts()),
              ArtifactKey.fromArtifacts(operationResult.getBoundArtifacts()));

      if (!unboundArtifacts.isEmpty()) {
        throw new IllegalArgumentException(
            String.format(
                "The following required artifacts could not be bound: '%s'. "
                    + "Check that the Docker image name above matches the name used in the image field of your manifest. "
                    + "Failing the stage as this is likely a configuration error.",
                unboundArtifacts));
      }
    }
  }

  private void sortManifests(List<KubernetesManifest> manifests) {
    getTask().updateStatus(OP_NAME, "Sorting manifests by priority...");
    manifests.sort(
        Comparator.comparingInt(m -> findResourceProperties(m).getHandler().deployPriority()));
    getTask()
        .updateStatus(
            OP_NAME,
            "Deploy order is: "
                + manifests.stream()
                    .map(KubernetesManifest::getFullResourceName)
                    .collect(Collectors.joining(", ")));
  }

  private String getArtifactKey(Artifact artifact) {
    return String.format(
        "[%s]-[%s]-[%s]",
        artifact.getType(),
        artifact.getName(),
        artifact.getLocation() != null ? artifact.getLocation() : "");
  }

  private void setTrafficAnnotation(List<String> services, KubernetesManifest manifest) {
    if (services == null || services.isEmpty()) {
      return;
    }
    KubernetesManifestTraffic traffic = new KubernetesManifestTraffic(services);
    KubernetesManifestAnnotater.setTraffic(manifest, traffic);
  }

  private void applyTraffic(
      KubernetesManifestTraffic traffic,
      KubernetesManifest target,
      Collection<KubernetesManifest> manifestsFromRequest) {
    traffic.getLoadBalancers().forEach(l -> attachLoadBalancer(l, target, manifestsFromRequest));
  }

  private void attachLoadBalancer(
      String loadBalancerName,
      KubernetesManifest target,
      Collection<KubernetesManifest> manifestsFromRequest) {

    KubernetesCoordinates coords;
    try {
      coords =
          KubernetesCoordinates.builder()
              .namespace(target.getNamespace())
              .fullResourceName(loadBalancerName)
              .build();
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException(
          String.format(
              "Failed to attach load balancer '%s'. Load balancers must be specified in the form '{kind} {name}', e.g. 'service my-service'.",
              loadBalancerName),
          e);
    }

    KubernetesManifest loadBalancer = getLoadBalancer(coords, manifestsFromRequest);

    CanLoadBalance handler =
        CanLoadBalance.lookupProperties(credentials.getResourcePropertyRegistry(), coords);

    getTask()
        .updateStatus(
            OP_NAME,
            "Attaching load balancer "
                + loadBalancer.getFullResourceName()
                + " to "
                + target.getFullResourceName());

    handler.attach(loadBalancer, target);
  }

  private KubernetesManifest getLoadBalancer(
      KubernetesCoordinates coords, Collection<KubernetesManifest> manifestsFromRequest) {
    Optional<KubernetesManifest> loadBalancer =
        manifestsFromRequest.stream()
            .filter(m -> KubernetesCoordinates.fromManifest(m).equals(coords))
            .findFirst();

    return loadBalancer.orElseGet(
        () ->
            Optional.ofNullable(credentials.get(coords))
                .orElseThrow(
                    () ->
                        new IllegalArgumentException(
                            "Load balancer "
                                + coords.getKind().toString()
                                + " "
                                + coords.getName()
                                + " does not exist")));
  }

  private boolean isVersioned(
      KubernetesResourceProperties properties, KubernetesManifestStrategy strategy) {
    if (strategy.getVersioned() != Versioned.DEFAULT) {
      return strategy.getVersioned() == Versioned.TRUE;
    }

    if (description.getVersioned() != null) {
      return description.getVersioned();
    }

    return properties.isVersioned();
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

  @Nonnull
  private KubernetesResourceProperties findResourceProperties(KubernetesManifest manifest) {
    KubernetesKind kind = manifest.getKind();
    getTask().updateStatus(OP_NAME, "Finding deployer for " + kind + "...");
    return credentials.getResourcePropertyRegistry().get(kind);
  }

  @Data
  @RequiredArgsConstructor
  private static class ManifestArtifactHolder {
    @Nonnull private KubernetesManifest manifest;
    @Nonnull private Artifact artifact;
    @Nonnull private KubernetesManifestStrategy strategy;
  }
}
