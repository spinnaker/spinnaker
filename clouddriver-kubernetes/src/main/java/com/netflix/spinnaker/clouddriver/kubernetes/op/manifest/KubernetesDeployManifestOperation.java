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
import com.netflix.spinnaker.clouddriver.kubernetes.op.handler.CanLoadBalance;
import com.netflix.spinnaker.clouddriver.kubernetes.op.handler.CanScale;
import com.netflix.spinnaker.clouddriver.kubernetes.op.handler.KubernetesHandler;
import com.netflix.spinnaker.clouddriver.kubernetes.security.KubernetesCredentials;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import com.netflix.spinnaker.moniker.Moniker;
import java.util.*;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
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
    getTask().updateStatus(OP_NAME, "Beginning deployment of manifest...");

    List<KubernetesManifest> inputManifests = description.getManifests();
    List<KubernetesManifest> deployManifests = new ArrayList<>();
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

    List<Artifact> requiredArtifacts = description.getRequiredArtifacts();
    if (requiredArtifacts == null) {
      requiredArtifacts = new ArrayList<>();
    }

    List<Artifact> optionalArtifacts = description.getOptionalArtifacts();
    if (optionalArtifacts == null) {
      optionalArtifacts = new ArrayList<>();
    }

    List<Artifact> artifacts = new ArrayList<>();
    // Optional artifacts are intentionally added before required artifacts. This is to ensure that
    // when artifact replacement occurs the required artifacts are not overwritten by optional
    // artifacts.
    artifacts.addAll(optionalArtifacts);
    artifacts.addAll(requiredArtifacts);

    Set<Artifact> boundArtifacts = new HashSet<>();

    for (KubernetesManifest manifest : inputManifests) {
      KubernetesManifestAnnotater.validateAnnotationsForRolloutStrategies(
          manifest, description.getStrategy());

      getTask()
          .updateStatus(
              OP_NAME,
              "Swapping out artifacts in " + manifest.getFullResourceName() + " from context...");
      ReplaceResult replaceResult =
          findResourceProperties(manifest)
              .getHandler()
              .replaceArtifacts(manifest, artifacts, description.getAccount());
      deployManifests.add(replaceResult.getManifest());
      boundArtifacts.addAll(replaceResult.getBoundArtifacts());
    }

    Set<ArtifactKey> unboundArtifacts =
        Sets.difference(
            ArtifactKey.fromArtifacts(description.getRequiredArtifacts()),
            ArtifactKey.fromArtifacts(boundArtifacts));

    getTask().updateStatus(OP_NAME, "Checking if all requested artifacts were bound...");
    if (!unboundArtifacts.isEmpty()) {
      throw new IllegalArgumentException(
          String.format(
              "The following required artifacts could not be bound: '%s'. "
                  + "Check that the Docker image name above matches the name used in the image field of your manifest. "
                  + "Failing the stage as this is likely a configuration error.",
              unboundArtifacts));
    }

    getTask().updateStatus(OP_NAME, "Sorting manifests by priority...");
    deployManifests.sort(
        Comparator.comparingInt(m -> findResourceProperties(m).getHandler().deployPriority()));
    getTask()
        .updateStatus(
            OP_NAME,
            "Deploy order is: "
                + deployManifests.stream()
                    .map(KubernetesManifest::getFullResourceName)
                    .collect(Collectors.joining(", ")));

    OperationResult result = new OperationResult();
    for (KubernetesManifest manifest : deployManifests) {
      KubernetesResourceProperties properties = findResourceProperties(manifest);
      KubernetesManifestStrategy strategy = KubernetesManifestAnnotater.getStrategy(manifest);

      OptionalInt version =
          isVersioned(properties, strategy)
              ? resourceVersioner.getVersion(manifest, credentials)
              : OptionalInt.empty();

      Moniker moniker = cloneMoniker(description.getMoniker());
      version.ifPresent(moniker::setSequence);
      if (Strings.isNullOrEmpty(moniker.getCluster())) {
        moniker.setCluster(manifest.getFullResourceName());
      }

      Artifact artifact = ArtifactConverter.toArtifact(manifest, description.getAccount(), version);

      getTask()
          .updateStatus(
              OP_NAME,
              "Annotating manifest "
                  + manifest.getFullResourceName()
                  + " with artifact, relationships & moniker...");
      KubernetesManifestAnnotater.annotateManifest(manifest, artifact);

      KubernetesHandler deployer = properties.getHandler();
      if (strategy.isUseSourceCapacity() && deployer instanceof CanScale) {
        Double replicas = KubernetesSourceCapacity.getSourceCapacity(manifest, credentials);
        if (replicas != null) {
          manifest.setReplicas(replicas);
        }
      }

      setTrafficAnnotation(description.getServices(), manifest);
      if (description.isEnableTraffic()) {
        KubernetesManifestTraffic traffic = KubernetesManifestAnnotater.getTraffic(manifest);
        applyTraffic(traffic, manifest, deployManifests);
      }

      credentials.getNamer().applyMoniker(manifest, moniker);
      manifest.setName(artifact.getReference());

      getTask()
          .updateStatus(
              OP_NAME,
              "Swapping out artifacts in "
                  + manifest.getFullResourceName()
                  + " from other deployments...");
      ReplaceResult replaceResult =
          deployer.replaceArtifacts(
              manifest, new ArrayList<>(result.getCreatedArtifacts()), description.getAccount());
      boundArtifacts.addAll(replaceResult.getBoundArtifacts());
      manifest = replaceResult.getManifest();

      getTask()
          .updateStatus(
              OP_NAME,
              "Submitting manifest " + manifest.getFullResourceName() + " to kubernetes master...");
      log.debug("Manifest in {} to be deployed: {}", accountName, manifest);
      result.merge(deployer.deploy(credentials, manifest, strategy.getDeployStrategy()));

      result.getCreatedArtifacts().add(artifact);
    }

    result.getBoundArtifacts().addAll(boundArtifacts);
    result.removeSensitiveKeys(credentials.getResourcePropertyRegistry());

    getTask().updateStatus(OP_NAME, "Deploy manifest task completed successfully.");
    return result;
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
}
