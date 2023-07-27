/*
 * Copyright 2020 Google, LLC
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

package com.netflix.spinnaker.clouddriver.kubernetes.op;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.netflix.spinnaker.clouddriver.data.task.DefaultTask;
import com.netflix.spinnaker.clouddriver.data.task.Task;
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository;
import com.netflix.spinnaker.clouddriver.kubernetes.KubernetesCloudProvider;
import com.netflix.spinnaker.clouddriver.kubernetes.artifact.ResourceVersioner;
import com.netflix.spinnaker.clouddriver.kubernetes.caching.view.provider.ArtifactProvider;
import com.netflix.spinnaker.clouddriver.kubernetes.config.KubernetesAccountProperties.ManagedAccount;
import com.netflix.spinnaker.clouddriver.kubernetes.description.GlobalResourcePropertyRegistry;
import com.netflix.spinnaker.clouddriver.kubernetes.description.KubernetesCoordinates;
import com.netflix.spinnaker.clouddriver.kubernetes.description.ResourcePropertyRegistry;
import com.netflix.spinnaker.clouddriver.kubernetes.description.manifest.KubernetesDeployManifestDescription;
import com.netflix.spinnaker.clouddriver.kubernetes.description.manifest.KubernetesKind;
import com.netflix.spinnaker.clouddriver.kubernetes.description.manifest.KubernetesKindProperties;
import com.netflix.spinnaker.clouddriver.kubernetes.description.manifest.KubernetesManifest;
import com.netflix.spinnaker.clouddriver.kubernetes.description.manifest.KubernetesManifestAnnotater;
import com.netflix.spinnaker.clouddriver.kubernetes.description.manifest.KubernetesManifestTraffic;
import com.netflix.spinnaker.clouddriver.kubernetes.names.KubernetesManifestNamer;
import com.netflix.spinnaker.clouddriver.kubernetes.op.handler.*;
import com.netflix.spinnaker.clouddriver.kubernetes.op.manifest.KubernetesDeployManifestOperation;
import com.netflix.spinnaker.clouddriver.kubernetes.security.KubernetesCredentials;
import com.netflix.spinnaker.clouddriver.kubernetes.security.KubernetesNamedAccountCredentials;
import com.netflix.spinnaker.clouddriver.names.NamerRegistry;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import com.netflix.spinnaker.moniker.Moniker;
import com.netflix.spinnaker.moniker.Namer;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

final class KubernetesDeployManifestOperationTest {
  private static final String DEFAULT_NAMESPACE = "default-namespace";
  private static final ResourcePropertyRegistry resourcePropertyRegistry =
      new GlobalResourcePropertyRegistry(
          ImmutableList.of(
              new KubernetesReplicaSetHandler(),
              new KubernetesServiceHandler(),
              new KubernetesConfigMapHandler()),
          new KubernetesUnregisteredCustomResourceHandler());
  private static final Namer<KubernetesManifest> NAMER = new KubernetesManifestNamer();
  private static final String ACCOUNT = "my-account";

  @BeforeEach
  void setTask() {
    TaskRepository.threadLocalTask.set(new DefaultTask("task-id"));
  }

  @Test
  void replicaSetDeployerInvoked() {
    String namespace = "my-namespace";
    KubernetesDeployManifestDescription deployManifestDescription =
        baseDeployDescription("deploy/replicaset.yml");
    OperationResult result = deploy(deployManifestDescription);

    assertThat(result.getManifestNamesByNamespace()).containsOnlyKeys(namespace);
    assertThat(result.getManifestNamesByNamespace().get(namespace))
        .containsExactlyInAnyOrder("replicaSet my-name-v000");
  }

  @Test
  void replicaSetDeployerUsesDefaultNamespace() {
    KubernetesDeployManifestDescription description =
        baseDeployDescription("deploy/replicaset-no-namespace.yml");
    OperationResult result = deploy(description);

    assertThat(result.getManifestNamesByNamespace()).containsOnlyKeys(DEFAULT_NAMESPACE);
    assertThat(result.getManifestNamesByNamespace().get(DEFAULT_NAMESPACE))
        .containsExactlyInAnyOrder("replicaSet my-name-v000");
  }

  @Test
  void sendsTrafficWhenEnabledTrafficTrue() {
    KubernetesDeployManifestDescription description =
        baseDeployDescription("deploy/replicaset.yml")
            .setServices(ImmutableList.of("service my-service"))
            .setEnableTraffic(true);
    OperationResult result = deploy(description);

    KubernetesManifest manifest = Iterables.getOnlyElement(result.getManifests());
    assertThat(manifest.getSpecTemplateLabels().orElse(manifest.getLabels()))
        .contains(entry("selector-key", "selector-value"));

    KubernetesManifestTraffic traffic = KubernetesManifestAnnotater.getTraffic(manifest);
    assertThat(traffic.getLoadBalancers()).containsExactly("service my-service");
  }

  @Test
  void doesNotSendTrafficWhenEnableTrafficFalse() {
    KubernetesDeployManifestDescription description =
        baseDeployDescription("deploy/replicaset.yml")
            .setServices(ImmutableList.of("service my-service"))
            .setEnableTraffic(false);
    OperationResult result = deploy(description);

    KubernetesManifest manifest = Iterables.getOnlyElement(result.getManifests());
    assertThat(manifest.getSpecTemplateLabels().orElse(manifest.getLabels()))
        .doesNotContain(entry("selector-key", "selector-value"));

    KubernetesManifestTraffic traffic = KubernetesManifestAnnotater.getTraffic(manifest);
    assertThat(traffic.getLoadBalancers()).containsExactly("service my-service");
  }

  @Test
  void doesNotSendTrafficWhenEnableTrafficTrueAndCantHandleTraffic() {
    KubernetesDeployManifestDescription description =
        baseDeployDescription("deploy/configmap.yml")
            .setServices(ImmutableList.of("service my-service"))
            .setEnableTraffic(true);
    OperationResult result = deploy(description);

    KubernetesManifest manifest = Iterables.getOnlyElement(result.getManifests());
    assertThat(manifest.getSpecTemplateLabels().orElse(manifest.getLabels()))
        .doesNotContain(entry("selector-key", "selector-value"));

    KubernetesManifestTraffic traffic = KubernetesManifestAnnotater.getTraffic(manifest);
    assertThat(traffic.getLoadBalancers()).doesNotContain("service my-service");
  }

  @Test
  void failsWhenServiceHasNoSelector() {
    KubernetesDeployManifestDescription description =
        baseDeployDescription("deploy/replicaset.yml")
            .setServices(ImmutableList.of("service my-service-no-selector"))
            .setEnableTraffic(true);
    assertThatThrownBy(() -> deploy(description)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void failsWhenServiceSelectorOverlapsWithTargetLabels() {
    KubernetesDeployManifestDescription description =
        baseDeployDescription("deploy/replicaset-overlapping-selector.yml")
            .setServices(ImmutableList.of("service my-service"))
            .setEnableTraffic(true);
    assertThatThrownBy(() -> deploy(description)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void deploysWithArtifactBindingDisabled() {
    KubernetesDeployManifestDescription description =
        baseDeployDescription("deploy/replicaset-configmap.yml");
    description.setEnableArtifactBinding(false);
    description.setRequiredArtifacts(
        ImmutableList.of(
            Artifact.builder()
                .name("index.docker.io/library/nginx")
                .type("docker/image")
                .reference("index.docker.io/library/nginx:required")
                .build()));
    description.setOptionalArtifacts(
        ImmutableList.of(
            Artifact.builder()
                .name("index.docker.io/library/nginx")
                .type("docker/image")
                .reference("index.docker.io/library/nginx:optional")
                .build()));
    OperationResult result = deploy(description);

    assertThat(result.getBoundArtifacts().size()).isEqualTo(1);
    assertThat(result.getBoundArtifacts().stream().map(Artifact::getReference))
        .containsExactlyInAnyOrder("myconfig-v000");
    assertThat(result.getCreatedArtifacts().size()).isEqualTo(2);
    assertThat(result.getCreatedArtifacts().stream().map(Artifact::getReference))
        .containsExactlyInAnyOrder("myconfig-v000", "my-name-v000");
  }

  @Test
  void deploysWithArtifactBindingUnspecified() {
    KubernetesDeployManifestDescription description =
        baseDeployDescription("deploy/replicaset-configmap.yml");
    description.setRequiredArtifacts(
        ImmutableList.of(
            Artifact.builder()
                .name("index.docker.io/library/nginx")
                .type("docker/image")
                .reference("index.docker.io/library/nginx:required")
                .build()));
    description.setOptionalArtifacts(
        ImmutableList.of(
            Artifact.builder()
                .name("index.docker.io/library/nginx")
                .type("docker/image")
                .reference("index.docker.io/library/nginx:optional")
                .build()));
    OperationResult result = deploy(description);

    assertThat(result.getBoundArtifacts().size()).isEqualTo(2);
    assertThat(result.getBoundArtifacts().stream().map(Artifact::getReference))
        .containsExactlyInAnyOrder("myconfig-v000", "index.docker.io/library/nginx:required");
    assertThat(result.getCreatedArtifacts().size()).isEqualTo(2);
    assertThat(result.getCreatedArtifacts().stream().map(Artifact::getReference))
        .containsExactlyInAnyOrder("myconfig-v000", "my-name-v000");
  }

  @Test
  void deploysBindingRequiredArtifact() {
    KubernetesDeployManifestDescription description =
        baseDeployDescription("deploy/replicaset-configmap.yml");
    description.setEnableArtifactBinding(true);
    description.setRequiredArtifacts(
        ImmutableList.of(
            Artifact.builder()
                .name("index.docker.io/library/nginx")
                .type("docker/image")
                .reference("index.docker.io/library/nginx:required")
                .build()));
    description.setOptionalArtifacts(
        ImmutableList.of(
            Artifact.builder()
                .name("index.docker.io/library/nginx")
                .type("docker/image")
                .reference("index.docker.io/library/nginx:optional")
                .build()));
    OperationResult result = deploy(description);

    assertThat(result.getBoundArtifacts().size()).isEqualTo(2);
    assertThat(result.getBoundArtifacts().stream().map(Artifact::getReference))
        .containsExactlyInAnyOrder("myconfig-v000", "index.docker.io/library/nginx:required");
    assertThat(result.getCreatedArtifacts().size()).isEqualTo(2);
    assertThat(result.getCreatedArtifacts().stream().map(Artifact::getReference))
        .containsExactlyInAnyOrder("myconfig-v000", "my-name-v000");
  }

  @Test
  void deploysBindingOptionalArtifact() {
    KubernetesDeployManifestDescription description =
        baseDeployDescription("deploy/replicaset-configmap.yml");
    description.setEnableArtifactBinding(true);
    description.setOptionalArtifacts(
        ImmutableList.of(
            Artifact.builder()
                .name("index.docker.io/library/nginx")
                .type("docker/image")
                .reference("index.docker.io/library/nginx:optional")
                .build()));
    OperationResult result = deploy(description);

    assertThat(result.getBoundArtifacts().size()).isEqualTo(2);
    assertThat(result.getBoundArtifacts().stream().map(Artifact::getReference))
        .containsExactlyInAnyOrder("myconfig-v000", "index.docker.io/library/nginx:optional");
    assertThat(result.getCreatedArtifacts().size()).isEqualTo(2);
    assertThat(result.getCreatedArtifacts().stream().map(Artifact::getReference))
        .containsExactlyInAnyOrder("myconfig-v000", "my-name-v000");
  }

  @Test
  void deploysBindingOptionalArtifactMultiNamespace() {
    KubernetesDeployManifestDescription description =
        baseDeployDescription("deploy/replicaset-volumes.yml");
    description.setEnableArtifactBinding(true);
    description.setOptionalArtifacts(
        ImmutableList.of(
            Artifact.builder()
                .name("myconfig")
                .type("kubernetes/configMap")
                .location("other-namespace")
                .reference("myconfig-v002")
                .build(),
            Artifact.builder()
                .name("myconfig")
                .type("kubernetes/configMap")
                .location("my-namespace")
                .reference("myconfig-v001")
                .build()));
    OperationResult result = deploy(description);

    assertThat(result.getBoundArtifacts().size()).isEqualTo(1);
    assertThat(result.getBoundArtifacts().stream().map(Artifact::getReference))
        .containsExactlyInAnyOrder("myconfig-v001");
    assertThat(result.getCreatedArtifacts().size()).isEqualTo(1);
    assertThat(result.getCreatedArtifacts().stream().map(Artifact::getReference))
        .containsExactlyInAnyOrder("my-name-v000");
  }

  @Test
  void deploysBindingUnmodifiedConfigMap() {
    String manifestFile = "deploy/replicaset-configmap.yml";
    KubernetesDeployManifestDescription description = baseDeployDescription(manifestFile);
    KubernetesManifest existingConfigMap =
        ManifestFetcher.getManifest(KubernetesDeployManifestOperationTest.class, manifestFile)
            .get(1);
    existingConfigMap.setName("myconfig-v001");
    Map<KubernetesKind, Artifact> existingArtifacts =
        ImmutableMap.of(
            KubernetesKind.CONFIG_MAP,
            Artifact.builder()
                .type("kubernetes/configMap")
                .name("myconfig")
                .version("v001")
                .reference("myconfig-v001")
                .metadata(ImmutableMap.of("lastAppliedConfiguration", existingConfigMap))
                .build());
    OperationResult result = deploy(description, existingArtifacts);

    assertThat(result.getBoundArtifacts().size()).isEqualTo(1);
    assertThat(result.getBoundArtifacts().stream().map(Artifact::getReference))
        .containsExactlyInAnyOrder("myconfig-v001");
    assertThat(result.getCreatedArtifacts().size()).isEqualTo(2);
    assertThat(result.getCreatedArtifacts().stream().map(Artifact::getReference))
        .containsExactlyInAnyOrder("my-name-v000", "myconfig-v001");
  }

  @Test
  void deploysCrdWhereSpecIsList() {
    KubernetesDeployManifestDescription deployManifestDescription =
        baseDeployDescription("deploy/crd-manifest-spec-is-list.yml");
    deploy(deployManifestDescription);
  }

  private static KubernetesDeployManifestDescription baseDeployDescription(String manifest) {
    KubernetesDeployManifestDescription deployManifestDescription =
        new KubernetesDeployManifestDescription()
            .setManifests(
                ManifestFetcher.getManifest(KubernetesDeployManifestOperationTest.class, manifest))
            .setMoniker(new Moniker())
            .setSource(KubernetesDeployManifestDescription.Source.text);
    deployManifestDescription.setAccount(ACCOUNT);
    deployManifestDescription.setCredentials(getNamedAccountCredentials());
    return deployManifestDescription;
  }

  private static KubernetesNamedAccountCredentials getNamedAccountCredentials() {
    ManagedAccount managedAccount = new ManagedAccount();
    managedAccount.setName("my-account");

    NamerRegistry.lookup()
        .withProvider(KubernetesCloudProvider.ID)
        .withAccount(managedAccount.getName())
        .setNamer(KubernetesManifest.class, new KubernetesManifestNamer());

    KubernetesCredentials mockCredentials = getMockKubernetesCredentials();
    KubernetesCredentials.Factory credentialFactory = mock(KubernetesCredentials.Factory.class);
    when(credentialFactory.build(managedAccount)).thenReturn(mockCredentials);
    return new KubernetesNamedAccountCredentials(managedAccount, credentialFactory);
  }

  private static KubernetesCredentials getMockKubernetesCredentials() {
    KubernetesCredentials credentialsMock = mock(KubernetesCredentials.class);
    when(credentialsMock.getKindProperties(any(KubernetesKind.class)))
        .thenAnswer(
            invocation ->
                KubernetesKindProperties.withDefaultProperties(
                    invocation.getArgument(0, KubernetesKind.class)));
    when(credentialsMock.getResourcePropertyRegistry()).thenReturn(resourcePropertyRegistry);
    when(credentialsMock.get(
            KubernetesCoordinates.builder()
                .kind(KubernetesKind.SERVICE)
                .namespace("my-namespace")
                .name("my-service")
                .build()))
        .thenReturn(
            ManifestFetcher.getManifest(
                    KubernetesDeployManifestOperationTest.class, "deploy/service.yml")
                .get(0));
    when(credentialsMock.get(
            KubernetesCoordinates.builder()
                .kind(KubernetesKind.SERVICE)
                .namespace("my-namespace")
                .name("my-service-no-selector")
                .build()))
        .thenReturn(
            ManifestFetcher.getManifest(
                    KubernetesDeployManifestOperationTest.class, "deploy/service-no-selector.yml")
                .get(0));
    when(credentialsMock.deploy(any(KubernetesManifest.class), any(Task.class), anyString()))
        .thenAnswer(
            invocation -> {
              // This simulates the fact that the Kubernetes API will add the default namespace if
              // none is supplied on the manifest.
              KubernetesManifest result =
                  invocation.getArgument(0, KubernetesManifest.class).clone();
              if (Strings.isNullOrEmpty(result.getNamespace())) {
                result.setNamespace(DEFAULT_NAMESPACE);
              }
              return result;
            });
    when(credentialsMock.getNamer()).thenReturn(NAMER);
    return credentialsMock;
  }

  private static OperationResult deploy(KubernetesDeployManifestDescription description) {
    ArtifactProvider artifactProvider = mock(ArtifactProvider.class);
    when(artifactProvider.getArtifacts(
            any(KubernetesKind.class),
            any(String.class),
            any(String.class),
            any(KubernetesCredentials.class)))
        .thenReturn(ImmutableList.of());
    ResourceVersioner resourceVersioner = new ResourceVersioner(artifactProvider);
    return new KubernetesDeployManifestOperation(description, resourceVersioner)
        .operate(ImmutableList.of());
  }

  private static OperationResult deploy(
      KubernetesDeployManifestDescription description,
      Map<KubernetesKind, Artifact> artifactsByKind) {
    ArtifactProvider artifactProvider = mock(ArtifactProvider.class);
    when(artifactProvider.getArtifacts(
            any(KubernetesKind.class),
            any(String.class),
            any(String.class),
            any(KubernetesCredentials.class)))
        .thenReturn(ImmutableList.of());
    for (Map.Entry<KubernetesKind, Artifact> entry : artifactsByKind.entrySet()) {
      when(artifactProvider.getArtifacts(
              eq(entry.getKey()),
              any(String.class),
              any(String.class),
              any(KubernetesCredentials.class)))
          .thenReturn(ImmutableList.of(entry.getValue()));
    }
    ResourceVersioner resourceVersioner = new ResourceVersioner(artifactProvider);
    return new KubernetesDeployManifestOperation(description, resourceVersioner)
        .operate(ImmutableList.of());
  }
}
