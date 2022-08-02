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

package com.netflix.spinnaker.clouddriver.kubernetes.artifact;

import static com.google.common.collect.ImmutableMap.toImmutableMap;
import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.gson.Gson;
import com.netflix.spinnaker.clouddriver.kubernetes.artifact.ArtifactReplacer.ReplaceResult;
import com.netflix.spinnaker.clouddriver.kubernetes.caching.agent.KubernetesCacheDataConverter;
import com.netflix.spinnaker.clouddriver.kubernetes.description.manifest.KubernetesManifest;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import io.kubernetes.client.openapi.JSON;
import io.kubernetes.client.openapi.models.*;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;

/**
 * The goal of this class is to do a test on each of the statically-defined replacers in {@link
 * Replacer}. Given {@link Replacer} has only package-private functions and users would only be
 * consuming these wrapped in an {@link ArtifactReplacer} we will do the same here; for each {@link
 * Replacer}, we wrap it in an {@link ArtifactReplacer} and check that it can find and replace the
 * expected artifacts on a Kubernetes object.
 *
 * <p>While {@link ArtifactReplacerTest} is focused more on the logic of {@link ArtifactReplacer}
 * (ex: do we properly filter artifacts by namespace/account) this class focuses on ensuring that
 * each static replacer works as expected.
 */
@RunWith(JUnitPlatform.class)
final class ReplacerTest {
  // We serialized generated Kubernetes metadata objects with JSON io.kubernetes.client.openapi.JSON
  // so that they match what we get back from kubectl.  We'll just gson from converting to a
  // KubernetesManifest because that's what we currently use to parse the result from kubectl and
  // we want this test to be realistic.
  private static final JSON json = new JSON();
  private static final Gson gson = new Gson();

  private static final String NAMESPACE = "ns";
  private static final String ACCOUNT = "my-account";
  private static final String DEFAULT_DOCKER_IMAGE_BINDING = "match-name-and-tag";

  @Test
  void findReplicaSetDockerImages() {
    ArtifactReplacer artifactReplacer =
        new ArtifactReplacer(ImmutableList.of(Replacer.dockerImage()));
    KubernetesManifest replicaSet = getReplicaSetWithContainers();

    Set<Artifact> artifacts = artifactReplacer.findAll(replicaSet);
    assertThat(artifacts).hasSize(2);

    Map<String, Artifact> byReference =
        artifacts.stream().collect(toImmutableMap(Artifact::getReference, a -> a));

    assertThat(byReference.get("gcr.io/my-repository/my-image:my-tag"))
        .satisfies(
            artifact -> {
              assertThat(artifact).isNotNull();
              assertThat(artifact.getType()).isEqualTo("docker/image");
              assertThat(artifact.getName()).isEqualTo("gcr.io/my-repository/my-image");
              assertThat(artifact.getReference()).isEqualTo("gcr.io/my-repository/my-image:my-tag");
            });

    assertThat(byReference.get("gcr.io/my-other-repository/some-image"))
        .satisfies(
            artifact -> {
              assertThat(artifact).isNotNull();
              assertThat(artifact.getType()).isEqualTo("docker/image");
              assertThat(artifact.getName()).isEqualTo("gcr.io/my-other-repository/some-image");
              assertThat(artifact.getReference())
                  .isEqualTo("gcr.io/my-other-repository/some-image");
            });
  }

  @Test
  void replaceReplicaSetDockerImages() {
    ArtifactReplacer artifactReplacer =
        new ArtifactReplacer(ImmutableList.of(Replacer.dockerImage()));
    KubernetesManifest replicaSet = getReplicaSetWithContainers();

    Artifact inputArtifact =
        Artifact.builder()
            .type("docker/image")
            .name("gcr.io/my-other-repository/some-image")
            .reference("gcr.io/my-other-repository/some-image:some-tag")
            .build();
    ReplaceResult replaceResult =
        artifactReplacer.replaceAll(
            DEFAULT_DOCKER_IMAGE_BINDING,
            replicaSet,
            ImmutableList.of(inputArtifact),
            NAMESPACE,
            ACCOUNT);

    V1ReplicaSet replacedReplicaSet =
        KubernetesCacheDataConverter.getResource(replaceResult.getManifest(), V1ReplicaSet.class);
    assertThat(replacedReplicaSet.getSpec().getTemplate().getSpec().getContainers())
        .extracting(V1Container::getImage)
        .containsExactly(
            // Only the second image should have been replaced.
            "gcr.io/my-repository/my-image:my-tag",
            "gcr.io/my-other-repository/some-image:some-tag");

    Set<Artifact> artifacts = replaceResult.getBoundArtifacts();
    assertThat(artifacts).hasSize(1);
    assertThat(Iterables.getOnlyElement(artifacts)).isEqualTo(inputArtifact);
  }

  private KubernetesManifest getReplicaSetWithContainers() {
    String replicaSet =
        json.serialize(
            new V1ReplicaSetBuilder()
                .withNewSpec()
                .withNewTemplate()
                .withNewSpec()
                .addToContainers(
                    new V1ContainerBuilder()
                        .withName("my-image-with-tag")
                        .withImage("gcr.io/my-repository/my-image:my-tag")
                        .build())
                .addToContainers(
                    new V1ContainerBuilder()
                        .withName("my-image-without-tag")
                        .withImage("gcr.io/my-other-repository/some-image")
                        .build())
                .endSpec()
                .endTemplate()
                .endSpec()
                .build());
    return gson.fromJson(replicaSet, KubernetesManifest.class);
  }

  @Test
  void findPodDockerImages() {
    ArtifactReplacer artifactReplacer =
        new ArtifactReplacer(ImmutableList.of(Replacer.podDockerImage()));
    KubernetesManifest pod = getPod();

    Set<Artifact> artifacts = artifactReplacer.findAll(pod);
    assertThat(artifacts).hasSize(2);

    Map<String, Artifact> byReference =
        artifacts.stream().collect(toImmutableMap(Artifact::getReference, a -> a));

    assertThat(byReference.get("gcr.io/my-repository/my-image:my-tag"))
        .satisfies(
            artifact -> {
              assertThat(artifact).isNotNull();
              assertThat(artifact.getType()).isEqualTo("docker/image");
              assertThat(artifact.getName()).isEqualTo("gcr.io/my-repository/my-image:my-tag");
              assertThat(artifact.getReference()).isEqualTo("gcr.io/my-repository/my-image:my-tag");
            });

    assertThat(byReference.get("gcr.io/my-other-repository/some-image"))
        .satisfies(
            artifact -> {
              assertThat(artifact).isNotNull();
              assertThat(artifact.getType()).isEqualTo("docker/image");
              assertThat(artifact.getName()).isEqualTo("gcr.io/my-other-repository/some-image");
              assertThat(artifact.getReference())
                  .isEqualTo("gcr.io/my-other-repository/some-image");
            });
  }

  @Test
  void replacePodDockerImages() {
    ArtifactReplacer artifactReplacer =
        new ArtifactReplacer(ImmutableList.of(Replacer.podDockerImage()));
    KubernetesManifest pod = getPod();

    Artifact inputArtifact =
        Artifact.builder()
            .type("docker/image")
            .name("gcr.io/my-other-repository/some-image")
            .reference("gcr.io/my-other-repository/some-image:some-tag")
            .build();
    ReplaceResult replaceResult =
        artifactReplacer.replaceAll(
            DEFAULT_DOCKER_IMAGE_BINDING, pod, ImmutableList.of(inputArtifact), NAMESPACE, ACCOUNT);

    V1Pod replacedPod =
        KubernetesCacheDataConverter.getResource(replaceResult.getManifest(), V1Pod.class);
    assertThat(replacedPod.getSpec().getContainers())
        .extracting(V1Container::getImage)
        .containsExactly(
            // Only the second image should have been replaced.
            "gcr.io/my-repository/my-image:my-tag",
            "gcr.io/my-other-repository/some-image:some-tag");

    Set<Artifact> artifacts = replaceResult.getBoundArtifacts();
    assertThat(artifacts).hasSize(1);
    assertThat(Iterables.getOnlyElement(artifacts)).isEqualTo(inputArtifact);
  }

  private KubernetesManifest getPod() {
    String pod =
        json.serialize(
            new V1PodBuilder()
                .withNewSpec()
                .addNewContainer()
                .withName("my-image-with-tag")
                .withImage("gcr.io/my-repository/my-image:my-tag")
                .endContainer()
                .addNewContainer()
                .withName("my-image-without-tag")
                .withImage("gcr.io/my-other-repository/some-image")
                .endContainer()
                .endSpec()
                .build());
    return gson.fromJson(pod, KubernetesManifest.class);
  }

  @Test
  void findConfigMapVolume() {
    ArtifactReplacer artifactReplacer =
        new ArtifactReplacer(ImmutableList.of(Replacer.configMapVolume()));
    KubernetesManifest replicaSet = getReplicaSetWithVolumes();

    Set<Artifact> artifacts = artifactReplacer.findAll(replicaSet);
    assertThat(artifacts).hasSize(2);

    Map<String, Artifact> byReference =
        artifacts.stream().collect(toImmutableMap(Artifact::getReference, a -> a));

    assertThat(byReference.get("first-config-map"))
        .satisfies(
            artifact -> {
              assertThat(artifact).isNotNull();
              assertThat(artifact.getType()).isEqualTo("kubernetes/configMap");
              assertThat(artifact.getName()).isEqualTo("first-config-map");
              assertThat(artifact.getReference()).isEqualTo("first-config-map");
            });

    assertThat(byReference.get("second-config-map"))
        .satisfies(
            artifact -> {
              assertThat(artifact).isNotNull();
              assertThat(artifact.getType()).isEqualTo("kubernetes/configMap");
              assertThat(artifact.getName()).isEqualTo("second-config-map");
              assertThat(artifact.getReference()).isEqualTo("second-config-map");
            });
  }

  @Test
  void replaceConfigMapVolume() {
    ArtifactReplacer artifactReplacer =
        new ArtifactReplacer(ImmutableList.of(Replacer.configMapVolume()));
    KubernetesManifest replicaSet = getReplicaSetWithVolumes();

    Artifact inputArtifact =
        Artifact.builder()
            .type("kubernetes/configMap")
            .name("second-config-map")
            .location(NAMESPACE)
            .version("v003")
            .reference("second-config-map-v003")
            .putMetadata("account", ACCOUNT)
            .build();
    ReplaceResult replaceResult =
        artifactReplacer.replaceAll(
            DEFAULT_DOCKER_IMAGE_BINDING,
            replicaSet,
            ImmutableList.of(inputArtifact),
            NAMESPACE,
            ACCOUNT);

    V1ReplicaSet replacedReplicaSet =
        KubernetesCacheDataConverter.getResource(replaceResult.getManifest(), V1ReplicaSet.class);
    assertThat(replacedReplicaSet.getSpec().getTemplate().getSpec().getVolumes())
        .extracting(V1Volume::getConfigMap)
        .filteredOn(Objects::nonNull)
        .extracting(V1ConfigMapVolumeSource::getName)
        .containsExactly(
            // Only the second config map should have been replaced.
            "first-config-map", "second-config-map-v003");

    assertThat(replacedReplicaSet.getSpec().getTemplate().getSpec().getVolumes())
        .extracting(V1Volume::getSecret)
        .filteredOn(Objects::nonNull)
        .extracting(V1SecretVolumeSource::getSecretName)
        .containsExactly(
            // No secrets should have been replaced.
            "first-secret", "second-secret");

    Set<Artifact> artifacts = replaceResult.getBoundArtifacts();
    assertThat(artifacts).hasSize(1);
    assertThat(Iterables.getOnlyElement(artifacts)).isEqualTo(inputArtifact);
  }

  @Test
  void findSecretVolume() {
    ArtifactReplacer artifactReplacer =
        new ArtifactReplacer(ImmutableList.of(Replacer.secretVolume()));
    KubernetesManifest replicaSet = getReplicaSetWithVolumes();

    Set<Artifact> artifacts = artifactReplacer.findAll(replicaSet);
    assertThat(artifacts).hasSize(2);

    Map<String, Artifact> byReference =
        artifacts.stream().collect(toImmutableMap(Artifact::getReference, a -> a));

    assertThat(byReference.get("first-secret"))
        .satisfies(
            artifact -> {
              assertThat(artifact).isNotNull();
              assertThat(artifact.getType()).isEqualTo("kubernetes/secret");
              assertThat(artifact.getName()).isEqualTo("first-secret");
              assertThat(artifact.getReference()).isEqualTo("first-secret");
            });

    assertThat(byReference.get("second-secret"))
        .satisfies(
            artifact -> {
              assertThat(artifact).isNotNull();
              assertThat(artifact.getType()).isEqualTo("kubernetes/secret");
              assertThat(artifact.getName()).isEqualTo("second-secret");
              assertThat(artifact.getReference()).isEqualTo("second-secret");
            });
  }

  @Test
  void replaceSecretVolume() {
    ArtifactReplacer artifactReplacer =
        new ArtifactReplacer(ImmutableList.of(Replacer.secretVolume()));
    KubernetesManifest replicaSet = getReplicaSetWithVolumes();

    Artifact inputArtifact =
        Artifact.builder()
            .type("kubernetes/secret")
            .name("first-secret")
            .location(NAMESPACE)
            .version("v007")
            .reference("first-secret-v007")
            .putMetadata("account", ACCOUNT)
            .build();
    ReplaceResult replaceResult =
        artifactReplacer.replaceAll(
            DEFAULT_DOCKER_IMAGE_BINDING,
            replicaSet,
            ImmutableList.of(inputArtifact),
            NAMESPACE,
            ACCOUNT);

    V1ReplicaSet replacedReplicaSet =
        KubernetesCacheDataConverter.getResource(replaceResult.getManifest(), V1ReplicaSet.class);
    assertThat(replacedReplicaSet.getSpec().getTemplate().getSpec().getVolumes())
        .extracting(V1Volume::getConfigMap)
        .filteredOn(Objects::nonNull)
        .extracting(V1ConfigMapVolumeSource::getName)
        .containsExactly(
            // No config maps should have been replaced.
            "first-config-map", "second-config-map");

    assertThat(replacedReplicaSet.getSpec().getTemplate().getSpec().getVolumes())
        .extracting(V1Volume::getSecret)
        .filteredOn(Objects::nonNull)
        .extracting(V1SecretVolumeSource::getSecretName)
        .containsExactly(
            // Only the first secret should have been replaced.
            "first-secret-v007", "second-secret");

    Set<Artifact> artifacts = replaceResult.getBoundArtifacts();
    assertThat(artifacts).hasSize(1);
    assertThat(Iterables.getOnlyElement(artifacts)).isEqualTo(inputArtifact);
  }

  private KubernetesManifest getReplicaSetWithVolumes() {
    String replicaSet =
        json.serialize(
            new V1ReplicaSetBuilder()
                .withNewSpec()
                .withNewTemplate()
                .withNewSpec()
                .addToVolumes(
                    new V1VolumeBuilder()
                        .withConfigMap(
                            new V1ConfigMapVolumeSourceBuilder()
                                .withName("first-config-map")
                                .build())
                        .build())
                .addToVolumes(
                    new V1VolumeBuilder()
                        .withConfigMap(
                            new V1ConfigMapVolumeSourceBuilder()
                                .withName("second-config-map")
                                .build())
                        .build())
                .addToVolumes(
                    new V1VolumeBuilder()
                        .withSecret(
                            new V1SecretVolumeSourceBuilder()
                                .withSecretName("first-secret")
                                .build())
                        .build())
                .addToVolumes(
                    new V1VolumeBuilder()
                        .withSecret(
                            new V1SecretVolumeSourceBuilder()
                                .withSecretName("second-secret")
                                .build())
                        .build())
                .endSpec()
                .endTemplate()
                .endSpec()
                .build());
    return gson.fromJson(replicaSet, KubernetesManifest.class);
  }

  @Test
  void findProjectedConfigMapVolume() {
    ArtifactReplacer artifactReplacer =
        new ArtifactReplacer(ImmutableList.of(Replacer.configMapProjectedVolume()));
    KubernetesManifest replicaSet = getReplicaSetWithProjectedVolumes();

    Set<Artifact> artifacts = artifactReplacer.findAll(replicaSet);
    assertThat(artifacts).hasSize(2);

    Map<String, Artifact> byReference =
        artifacts.stream().collect(toImmutableMap(Artifact::getReference, a -> a));

    assertThat(byReference.get("first-config-map"))
        .satisfies(
            artifact -> {
              assertThat(artifact).isNotNull();
              assertThat(artifact.getType()).isEqualTo("kubernetes/configMap");
              assertThat(artifact.getName()).isEqualTo("first-config-map");
              assertThat(artifact.getReference()).isEqualTo("first-config-map");
            });

    assertThat(byReference.get("second-config-map"))
        .satisfies(
            artifact -> {
              assertThat(artifact).isNotNull();
              assertThat(artifact.getType()).isEqualTo("kubernetes/configMap");
              assertThat(artifact.getName()).isEqualTo("second-config-map");
              assertThat(artifact.getReference()).isEqualTo("second-config-map");
            });
  }

  @Test
  void replaceProjectedConfigMapVolume() {
    ArtifactReplacer artifactReplacer =
        new ArtifactReplacer(ImmutableList.of(Replacer.configMapProjectedVolume()));
    KubernetesManifest replicaSet = getReplicaSetWithProjectedVolumes();

    Artifact inputArtifact =
        Artifact.builder()
            .type("kubernetes/configMap")
            .name("second-config-map")
            .location(NAMESPACE)
            .version("v003")
            .reference("second-config-map-v003")
            .putMetadata("account", ACCOUNT)
            .build();
    ReplaceResult replaceResult =
        artifactReplacer.replaceAll(
            DEFAULT_DOCKER_IMAGE_BINDING,
            replicaSet,
            ImmutableList.of(inputArtifact),
            NAMESPACE,
            ACCOUNT);

    V1ReplicaSet replacedReplicaSet =
        KubernetesCacheDataConverter.getResource(replaceResult.getManifest(), V1ReplicaSet.class);

    assertThat(replacedReplicaSet.getSpec().getTemplate().getSpec().getVolumes())
        .extracting(V1Volume::getProjected)
        .filteredOn(Objects::nonNull)
        .extracting(V1ProjectedVolumeSource::getSources)
        .flatExtracting(list -> list)
        .extracting("configMap")
        .filteredOn(Objects::nonNull)
        .extracting("name")
        .containsExactly(
            // Only the second config map should have been replaced.
            "first-config-map", "second-config-map-v003");

    assertThat(replacedReplicaSet.getSpec().getTemplate().getSpec().getVolumes())
        .extracting(V1Volume::getProjected)
        .filteredOn(Objects::nonNull)
        .extracting(V1ProjectedVolumeSource::getSources)
        .flatExtracting(list -> list)
        .extracting("secret")
        .filteredOn(Objects::nonNull)
        .extracting("name")
        .containsExactly(
            // No secrets should have been replaced.
            "first-secret", "second-secret");

    Set<Artifact> artifacts = replaceResult.getBoundArtifacts();
    assertThat(artifacts).hasSize(1);
    assertThat(Iterables.getOnlyElement(artifacts)).isEqualTo(inputArtifact);
  }

  @Test
  void findProjectedSecretVolume() {
    ArtifactReplacer artifactReplacer =
        new ArtifactReplacer(ImmutableList.of(Replacer.secretProjectedVolume()));
    KubernetesManifest replicaSet = getReplicaSetWithProjectedVolumes();

    Set<Artifact> artifacts = artifactReplacer.findAll(replicaSet);
    assertThat(artifacts).hasSize(2);

    Map<String, Artifact> byReference =
        artifacts.stream().collect(toImmutableMap(Artifact::getReference, a -> a));

    assertThat(byReference.get("first-secret"))
        .satisfies(
            artifact -> {
              assertThat(artifact).isNotNull();
              assertThat(artifact.getType()).isEqualTo("kubernetes/secret");
              assertThat(artifact.getName()).isEqualTo("first-secret");
              assertThat(artifact.getReference()).isEqualTo("first-secret");
            });

    assertThat(byReference.get("second-secret"))
        .satisfies(
            artifact -> {
              assertThat(artifact).isNotNull();
              assertThat(artifact.getType()).isEqualTo("kubernetes/secret");
              assertThat(artifact.getName()).isEqualTo("second-secret");
              assertThat(artifact.getReference()).isEqualTo("second-secret");
            });
  }

  @Test
  void replaceProjectedSecretVolume() {
    ArtifactReplacer artifactReplacer =
        new ArtifactReplacer(ImmutableList.of(Replacer.secretProjectedVolume()));
    KubernetesManifest replicaSet = getReplicaSetWithProjectedVolumes();

    Artifact inputArtifact =
        Artifact.builder()
            .type("kubernetes/secret")
            .name("first-secret")
            .location(NAMESPACE)
            .version("v007")
            .reference("first-secret-v007")
            .putMetadata("account", ACCOUNT)
            .build();
    ReplaceResult replaceResult =
        artifactReplacer.replaceAll(
            DEFAULT_DOCKER_IMAGE_BINDING,
            replicaSet,
            ImmutableList.of(inputArtifact),
            NAMESPACE,
            ACCOUNT);

    V1ReplicaSet replacedReplicaSet =
        KubernetesCacheDataConverter.getResource(replaceResult.getManifest(), V1ReplicaSet.class);
    assertThat(replacedReplicaSet.getSpec().getTemplate().getSpec().getVolumes())
        .extracting(V1Volume::getProjected)
        .filteredOn(Objects::nonNull)
        .extracting(V1ProjectedVolumeSource::getSources)
        .flatExtracting(list -> list)
        .extracting("configMap")
        .filteredOn(Objects::nonNull)
        .extracting("name")
        .containsExactly(
            // No config maps should have been replaced.
            "first-config-map", "second-config-map");

    assertThat(replacedReplicaSet.getSpec().getTemplate().getSpec().getVolumes())
        .extracting(V1Volume::getProjected)
        .filteredOn(Objects::nonNull)
        .extracting(V1ProjectedVolumeSource::getSources)
        .flatExtracting(list -> list)
        .extracting("secret")
        .filteredOn(Objects::nonNull)
        .extracting("name")
        .containsExactly(
            // Only the first secret should have been replaced.
            "first-secret-v007", "second-secret");

    Set<Artifact> artifacts = replaceResult.getBoundArtifacts();
    assertThat(artifacts).hasSize(1);
    assertThat(Iterables.getOnlyElement(artifacts)).isEqualTo(inputArtifact);
  }

  private KubernetesManifest getReplicaSetWithProjectedVolumes() {
    String replicaSet =
        json.serialize(
            new V1ReplicaSetBuilder()
                .withNewSpec()
                .withNewTemplate()
                .withNewSpec()
                .addToVolumes(
                    new V1VolumeBuilder()
                        .withName("first-projected-volume")
                        .withProjected(
                            new V1ProjectedVolumeSourceBuilder()
                                .build()
                                .addSourcesItem(
                                    new V1VolumeProjectionBuilder()
                                        .withConfigMap(
                                            new V1ConfigMapProjectionBuilder()
                                                .withName("first-config-map")
                                                .build())
                                        .build())
                                .addSourcesItem(
                                    new V1VolumeProjectionBuilder()
                                        .withConfigMap(
                                            new V1ConfigMapProjectionBuilder()
                                                .withName("second-config-map")
                                                .build())
                                        .build())
                                .addSourcesItem(
                                    new V1VolumeProjectionBuilder()
                                        .withSecret(
                                            new V1SecretProjectionBuilder()
                                                .withName("first-secret")
                                                .build())
                                        .build())
                                .addSourcesItem(
                                    new V1VolumeProjectionBuilder()
                                        .withSecret(
                                            new V1SecretProjectionBuilder()
                                                .withName("second-secret")
                                                .build())
                                        .build()))
                        .build())
                .endSpec()
                .endTemplate()
                .endSpec()
                .build());
    KubernetesManifest kubernetesManifest = gson.fromJson(replicaSet, KubernetesManifest.class);
    return kubernetesManifest;
  }

  @Test
  void findConfigMapKeyValue() {
    ArtifactReplacer artifactReplacer =
        new ArtifactReplacer(ImmutableList.of(Replacer.configMapKeyValue()));
    KubernetesManifest replicaSet = getReplicaSetWithKeyRefs();

    Set<Artifact> artifacts = artifactReplacer.findAll(replicaSet);
    assertThat(artifacts).hasSize(2);

    Map<String, Artifact> byReference =
        artifacts.stream().collect(toImmutableMap(Artifact::getReference, a -> a));

    assertThat(byReference.get("first-name"))
        .satisfies(
            artifact -> {
              assertThat(artifact).isNotNull();
              assertThat(artifact.getType()).isEqualTo("kubernetes/configMap");
              assertThat(artifact.getName()).isEqualTo("first-name");
              assertThat(artifact.getReference()).isEqualTo("first-name");
            });

    assertThat(byReference.get("second-name"))
        .satisfies(
            artifact -> {
              assertThat(artifact).isNotNull();
              assertThat(artifact.getType()).isEqualTo("kubernetes/configMap");
              assertThat(artifact.getName()).isEqualTo("second-name");
              assertThat(artifact.getReference()).isEqualTo("second-name");
            });
  }

  @Test
  void replaceConfigMapKeyValue() {
    ArtifactReplacer artifactReplacer =
        new ArtifactReplacer(ImmutableList.of(Replacer.configMapKeyValue()));
    KubernetesManifest replicaSet = getReplicaSetWithKeyRefs();

    Artifact inputArtifact =
        Artifact.builder()
            .type("kubernetes/configMap")
            .name("first-name")
            .location(NAMESPACE)
            .version("v006")
            .reference("first-name-v006")
            .putMetadata("account", ACCOUNT)
            .build();
    ReplaceResult replaceResult =
        artifactReplacer.replaceAll(
            DEFAULT_DOCKER_IMAGE_BINDING,
            replicaSet,
            ImmutableList.of(inputArtifact),
            NAMESPACE,
            ACCOUNT);

    V1ReplicaSet replacedReplicaSet =
        KubernetesCacheDataConverter.getResource(replaceResult.getManifest(), V1ReplicaSet.class);
    assertThat(replacedReplicaSet.getSpec().getTemplate().getSpec().getContainers())
        .flatExtracting(V1Container::getEnv)
        .extracting(V1EnvVar::getValueFrom)
        .extracting(V1EnvVarSource::getConfigMapKeyRef)
        .filteredOn(Objects::nonNull)
        .extracting(V1ConfigMapKeySelector::getName)
        .containsExactly(
            // We should have replaced both references to the first name.
            "first-name-v006", "first-name-v006", "second-name");

    assertThat(replacedReplicaSet.getSpec().getTemplate().getSpec().getContainers())
        .flatExtracting(V1Container::getEnv)
        .extracting(V1EnvVar::getValueFrom)
        .extracting(V1EnvVarSource::getSecretKeyRef)
        .filteredOn(Objects::nonNull)
        .extracting(V1SecretKeySelector::getName)
        .containsExactly(
            // We should not have replaced any secret references.
            "first-name", "second-name", "second-name");

    Set<Artifact> artifacts = replaceResult.getBoundArtifacts();
    assertThat(artifacts).hasSize(1);
    assertThat(Iterables.getOnlyElement(artifacts)).isEqualTo(inputArtifact);
  }

  @Test
  void findSecretKeyValue() {
    ArtifactReplacer artifactReplacer =
        new ArtifactReplacer(ImmutableList.of(Replacer.secretKeyValue()));
    KubernetesManifest replicaSet = getReplicaSetWithKeyRefs();

    Set<Artifact> artifacts = artifactReplacer.findAll(replicaSet);
    assertThat(artifacts).hasSize(2);

    Map<String, Artifact> byReference =
        artifacts.stream().collect(toImmutableMap(Artifact::getReference, a -> a));

    assertThat(byReference.get("first-name"))
        .satisfies(
            artifact -> {
              assertThat(artifact).isNotNull();
              assertThat(artifact.getType()).isEqualTo("kubernetes/secret");
              assertThat(artifact.getName()).isEqualTo("first-name");
              assertThat(artifact.getReference()).isEqualTo("first-name");
            });

    assertThat(byReference.get("second-name"))
        .satisfies(
            artifact -> {
              assertThat(artifact).isNotNull();
              assertThat(artifact.getType()).isEqualTo("kubernetes/secret");
              assertThat(artifact.getName()).isEqualTo("second-name");
              assertThat(artifact.getReference()).isEqualTo("second-name");
            });
  }

  @Test
  void replaceSecretKeyValue() {
    ArtifactReplacer artifactReplacer =
        new ArtifactReplacer(ImmutableList.of(Replacer.secretKeyValue()));
    KubernetesManifest replicaSet = getReplicaSetWithKeyRefs();

    Artifact inputArtifact =
        Artifact.builder()
            .type("kubernetes/secret")
            .name("second-name")
            .location(NAMESPACE)
            .version("v009")
            .reference("second-name-v009")
            .putMetadata("account", ACCOUNT)
            .build();
    ReplaceResult replaceResult =
        artifactReplacer.replaceAll(
            DEFAULT_DOCKER_IMAGE_BINDING,
            replicaSet,
            ImmutableList.of(inputArtifact),
            NAMESPACE,
            ACCOUNT);

    V1ReplicaSet replacedReplicaSet =
        KubernetesCacheDataConverter.getResource(replaceResult.getManifest(), V1ReplicaSet.class);
    assertThat(replacedReplicaSet.getSpec().getTemplate().getSpec().getContainers())
        .flatExtracting(V1Container::getEnv)
        .extracting(V1EnvVar::getValueFrom)
        .extracting(V1EnvVarSource::getConfigMapKeyRef)
        .filteredOn(Objects::nonNull)
        .extracting(V1ConfigMapKeySelector::getName)
        .containsExactly(
            // We should not have replaced any config map references.
            "first-name", "first-name", "second-name");

    assertThat(replacedReplicaSet.getSpec().getTemplate().getSpec().getContainers())
        .flatExtracting(V1Container::getEnv)
        .extracting(V1EnvVar::getValueFrom)
        .extracting(V1EnvVarSource::getSecretKeyRef)
        .filteredOn(Objects::nonNull)
        .extracting(V1SecretKeySelector::getName)
        .containsExactly(
            // We should have replaced both references to second-name.
            "first-name", "second-name-v009", "second-name-v009");

    Set<Artifact> artifacts = replaceResult.getBoundArtifacts();
    assertThat(artifacts).hasSize(1);
    assertThat(Iterables.getOnlyElement(artifacts)).isEqualTo(inputArtifact);
  }

  private KubernetesManifest getReplicaSetWithKeyRefs() {
    String replicaSet =
        json.serialize(
            new V1ReplicaSetBuilder()
                .withNewSpec()
                .withNewTemplate()
                .withNewSpec()
                .addToContainers(
                    new V1ContainerBuilder()
                        .withName("my-image-with-tag")
                        .withEnv(
                            new V1EnvVarBuilder()
                                .withValueFrom(
                                    new V1EnvVarSourceBuilder()
                                        .withConfigMapKeyRef(
                                            new V1ConfigMapKeySelectorBuilder()
                                                .withName("first-name")
                                                .withKey("first-key")
                                                .build())
                                        .build())
                                .build(),
                            new V1EnvVarBuilder()
                                .withValueFrom(
                                    new V1EnvVarSourceBuilder()
                                        .withConfigMapKeyRef(
                                            new V1ConfigMapKeySelectorBuilder()
                                                // Second key also from the first config map
                                                .withName("first-name")
                                                .withKey("second-key")
                                                .build())
                                        .build())
                                .build(),
                            new V1EnvVarBuilder()
                                .withValueFrom(
                                    new V1EnvVarSourceBuilder()
                                        .withConfigMapKeyRef(
                                            new V1ConfigMapKeySelectorBuilder()
                                                .withName("second-name")
                                                .withKey("third-key")
                                                .build())
                                        .build())
                                .build(),
                            new V1EnvVarBuilder()
                                .withValueFrom(
                                    new V1EnvVarSourceBuilder()
                                        .withSecretKeyRef(
                                            new V1SecretKeySelectorBuilder()
                                                .withName("first-name")
                                                .withKey("first-key")
                                                .build())
                                        .build())
                                .build(),
                            new V1EnvVarBuilder()
                                .withValueFrom(
                                    new V1EnvVarSourceBuilder()
                                        .withSecretKeyRef(
                                            new V1SecretKeySelectorBuilder()
                                                .withName("second-name")
                                                .withKey("second-key")
                                                .build())
                                        .build())
                                .build(),
                            new V1EnvVarBuilder()
                                .withValueFrom(
                                    new V1EnvVarSourceBuilder()
                                        .withSecretKeyRef(
                                            new V1SecretKeySelectorBuilder()
                                                // Third key also from the second secret
                                                .withName("second-name")
                                                .withKey("third-key")
                                                .build())
                                        .build())
                                .build())
                        .build())
                .endSpec()
                .endTemplate()
                .endSpec()
                .build());
    return gson.fromJson(replicaSet, KubernetesManifest.class);
  }

  @Test
  void findConfigMapEnvFrom() {
    ArtifactReplacer artifactReplacer =
        new ArtifactReplacer(ImmutableList.of(Replacer.configMapEnv()));
    KubernetesManifest replicaSet = getReplicaSetWithEnvFrom();

    Set<Artifact> artifacts = artifactReplacer.findAll(replicaSet);
    assertThat(artifacts).hasSize(2);

    Map<String, Artifact> byReference =
        artifacts.stream().collect(toImmutableMap(Artifact::getReference, a -> a));

    assertThat(byReference.get("config-map-name"))
        .satisfies(
            artifact -> {
              assertThat(artifact).isNotNull();
              assertThat(artifact.getType()).isEqualTo("kubernetes/configMap");
              assertThat(artifact.getName()).isEqualTo("config-map-name");
              assertThat(artifact.getReference()).isEqualTo("config-map-name");
            });

    assertThat(byReference.get("shared-name"))
        .satisfies(
            artifact -> {
              assertThat(artifact).isNotNull();
              assertThat(artifact.getType()).isEqualTo("kubernetes/configMap");
              assertThat(artifact.getName()).isEqualTo("shared-name");
              assertThat(artifact.getReference()).isEqualTo("shared-name");
            });
  }

  @Test
  void replaceConfigMapEnvFrom() {
    ArtifactReplacer artifactReplacer =
        new ArtifactReplacer(ImmutableList.of(Replacer.configMapEnv()));
    KubernetesManifest replicaSet = getReplicaSetWithEnvFrom();

    Artifact inputArtifact =
        Artifact.builder()
            .type("kubernetes/configMap")
            .name("shared-name")
            .location(NAMESPACE)
            .version("v020")
            .reference("shared-name-v020")
            .putMetadata("account", ACCOUNT)
            .build();
    ReplaceResult replaceResult =
        artifactReplacer.replaceAll(
            DEFAULT_DOCKER_IMAGE_BINDING,
            replicaSet,
            ImmutableList.of(inputArtifact),
            NAMESPACE,
            ACCOUNT);

    V1ReplicaSet replacedReplicaSet =
        KubernetesCacheDataConverter.getResource(replaceResult.getManifest(), V1ReplicaSet.class);
    assertThat(replacedReplicaSet.getSpec().getTemplate().getSpec().getContainers())
        .flatExtracting(V1Container::getEnvFrom)
        .extracting(V1EnvFromSource::getConfigMapRef)
        .filteredOn(Objects::nonNull)
        .extracting(V1ConfigMapEnvSource::getName)
        .containsExactly(
            // We should have replaced only shared-name.
            "config-map-name", "shared-name-v020");

    assertThat(replacedReplicaSet.getSpec().getTemplate().getSpec().getContainers())
        .flatExtracting(V1Container::getEnvFrom)
        .extracting(V1EnvFromSource::getSecretRef)
        .filteredOn(Objects::nonNull)
        .extracting(V1SecretEnvSource::getName)
        .containsExactly(
            // We should not have replaced any secret references, even the one with the same name as
            // the artifact.
            "secret-name", "shared-name");

    Set<Artifact> artifacts = replaceResult.getBoundArtifacts();
    assertThat(artifacts).hasSize(1);
    assertThat(Iterables.getOnlyElement(artifacts)).isEqualTo(inputArtifact);
  }

  @Test
  void findSecretEnvFrom() {
    ArtifactReplacer artifactReplacer =
        new ArtifactReplacer(ImmutableList.of(Replacer.secretEnv()));
    KubernetesManifest replicaSet = getReplicaSetWithEnvFrom();

    Set<Artifact> artifacts = artifactReplacer.findAll(replicaSet);
    assertThat(artifacts).hasSize(2);

    Map<String, Artifact> byReference =
        artifacts.stream().collect(toImmutableMap(Artifact::getReference, a -> a));

    assertThat(byReference.get("secret-name"))
        .satisfies(
            artifact -> {
              assertThat(artifact).isNotNull();
              assertThat(artifact.getType()).isEqualTo("kubernetes/secret");
              assertThat(artifact.getName()).isEqualTo("secret-name");
              assertThat(artifact.getReference()).isEqualTo("secret-name");
            });

    assertThat(byReference.get("shared-name"))
        .satisfies(
            artifact -> {
              assertThat(artifact).isNotNull();
              assertThat(artifact.getType()).isEqualTo("kubernetes/secret");
              assertThat(artifact.getName()).isEqualTo("shared-name");
              assertThat(artifact.getReference()).isEqualTo("shared-name");
            });
  }

  @Test
  void replaceSecretEnvFrom() {
    ArtifactReplacer artifactReplacer =
        new ArtifactReplacer(ImmutableList.of(Replacer.secretEnv()));
    KubernetesManifest replicaSet = getReplicaSetWithEnvFrom();

    Artifact inputArtifact =
        Artifact.builder()
            .type("kubernetes/secret")
            .name("shared-name")
            .location(NAMESPACE)
            .version("v987")
            .reference("shared-name-v987")
            .putMetadata("account", ACCOUNT)
            .build();
    ReplaceResult replaceResult =
        artifactReplacer.replaceAll(
            DEFAULT_DOCKER_IMAGE_BINDING,
            replicaSet,
            ImmutableList.of(inputArtifact),
            NAMESPACE,
            ACCOUNT);

    V1ReplicaSet replacedReplicaSet =
        KubernetesCacheDataConverter.getResource(replaceResult.getManifest(), V1ReplicaSet.class);
    assertThat(replacedReplicaSet.getSpec().getTemplate().getSpec().getContainers())
        .flatExtracting(V1Container::getEnvFrom)
        .extracting(V1EnvFromSource::getConfigMapRef)
        .filteredOn(Objects::nonNull)
        .extracting(V1ConfigMapEnvSource::getName)
        .containsExactly(
            // We should not have replaced any config map references, even the one with the same
            // name as the artifact.
            "config-map-name", "shared-name");

    assertThat(replacedReplicaSet.getSpec().getTemplate().getSpec().getContainers())
        .flatExtracting(V1Container::getEnvFrom)
        .extracting(V1EnvFromSource::getSecretRef)
        .filteredOn(Objects::nonNull)
        .extracting(V1SecretEnvSource::getName)
        .containsExactly(
            // We should not have replaced any secret references, even the one with the same name as
            // the artifact.
            "secret-name", "shared-name-v987");

    Set<Artifact> artifacts = replaceResult.getBoundArtifacts();
    assertThat(artifacts).hasSize(1);
    assertThat(Iterables.getOnlyElement(artifacts)).isEqualTo(inputArtifact);
  }

  private KubernetesManifest getReplicaSetWithEnvFrom() {
    String replicaSet =
        json.serialize(
            new V1ReplicaSetBuilder()
                .withNewSpec()
                .withNewTemplate()
                .withNewSpec()
                .addToContainers(
                    new V1ContainerBuilder()
                        .withName("my-image-with-tag")
                        .withEnvFrom(
                            // Give them both the same name so we can ensure we don't mix
                            // secrets/configMaps
                            new V1EnvFromSourceBuilder()
                                .withConfigMapRef(
                                    new V1ConfigMapEnvSourceBuilder()
                                        .withName("config-map-name")
                                        .build())
                                .build(),
                            new V1EnvFromSourceBuilder()
                                .withConfigMapRef(
                                    new V1ConfigMapEnvSourceBuilder()
                                        .withName("shared-name")
                                        .build())
                                .build(),
                            new V1EnvFromSourceBuilder()
                                .withSecretRef(
                                    new V1SecretEnvSourceBuilder().withName("secret-name").build())
                                .build(),
                            new V1EnvFromSourceBuilder()
                                .withSecretRef(
                                    new V1SecretEnvSourceBuilder().withName("shared-name").build())
                                .build())
                        .build())
                .endSpec()
                .endTemplate()
                .endSpec()
                .build());
    return gson.fromJson(replicaSet, KubernetesManifest.class);
  }

  @Test
  void findHpaDeployment() {
    ArtifactReplacer artifactReplacer =
        new ArtifactReplacer(ImmutableList.of(Replacer.hpaDeployment()));
    KubernetesManifest hpa = getHpaForDeployment();

    Set<Artifact> artifacts = artifactReplacer.findAll(hpa);
    assertThat(artifacts).hasSize(1);
    assertThat(Iterables.getOnlyElement(artifacts))
        .satisfies(
            artifact -> {
              assertThat(artifact).isNotNull();
              assertThat(artifact.getType()).isEqualTo("kubernetes/deployment");
              assertThat(artifact.getName()).isEqualTo("my-deployment");
              assertThat(artifact.getReference()).isEqualTo("my-deployment");
            });
  }

  @Test
  void findHpaDeploymentIgnoresReplicaSet() {
    ArtifactReplacer artifactReplacer =
        new ArtifactReplacer(ImmutableList.of(Replacer.hpaDeployment()));
    KubernetesManifest hpa = getHpaForReplicaSet();

    Set<Artifact> artifacts = artifactReplacer.findAll(hpa);
    assertThat(artifacts).isEmpty();
  }

  @Test
  void replaceHpaDeployment() {
    ArtifactReplacer artifactReplacer =
        new ArtifactReplacer(ImmutableList.of(Replacer.hpaDeployment()));
    KubernetesManifest hpa = getHpaForDeployment();

    Artifact inputArtifact =
        Artifact.builder()
            .type("kubernetes/deployment")
            .name("my-deployment")
            .location(NAMESPACE)
            .version("v020")
            .reference("my-deployment-v020")
            .putMetadata("account", ACCOUNT)
            .build();
    ReplaceResult replaceResult =
        artifactReplacer.replaceAll(
            DEFAULT_DOCKER_IMAGE_BINDING, hpa, ImmutableList.of(inputArtifact), NAMESPACE, ACCOUNT);

    V1HorizontalPodAutoscaler replacedHpa =
        KubernetesCacheDataConverter.getResource(
            replaceResult.getManifest(), V1HorizontalPodAutoscaler.class);
    assertThat(replacedHpa.getSpec().getScaleTargetRef().getName()).isEqualTo("my-deployment-v020");

    Set<Artifact> artifacts = replaceResult.getBoundArtifacts();
    assertThat(artifacts).hasSize(1);
    assertThat(Iterables.getOnlyElement(artifacts)).isEqualTo(inputArtifact);
  }

  @Test
  void findHpaReplicaSet() {
    ArtifactReplacer artifactReplacer =
        new ArtifactReplacer(ImmutableList.of(Replacer.hpaReplicaSet()));
    KubernetesManifest hpa = getHpaForReplicaSet();

    Set<Artifact> artifacts = artifactReplacer.findAll(hpa);
    assertThat(artifacts).hasSize(1);
    assertThat(Iterables.getOnlyElement(artifacts))
        .satisfies(
            artifact -> {
              assertThat(artifact).isNotNull();
              assertThat(artifact.getType()).isEqualTo("kubernetes/replicaSet");
              assertThat(artifact.getName()).isEqualTo("my-replica-set");
              assertThat(artifact.getReference()).isEqualTo("my-replica-set");
            });
  }

  @Test
  void findHpaReplicaSetIgnoresDeployment() {
    ArtifactReplacer artifactReplacer =
        new ArtifactReplacer(ImmutableList.of(Replacer.hpaReplicaSet()));
    KubernetesManifest hpa = getHpaForDeployment();

    Set<Artifact> artifacts = artifactReplacer.findAll(hpa);
    assertThat(artifacts).isEmpty();
  }

  @Test
  void replaceHpaReplicaSet() {
    ArtifactReplacer artifactReplacer =
        new ArtifactReplacer(ImmutableList.of(Replacer.hpaReplicaSet()));
    KubernetesManifest hpa = getHpaForReplicaSet();

    Artifact inputArtifact =
        Artifact.builder()
            .type("kubernetes/replicaSet")
            .name("my-replica-set")
            .location(NAMESPACE)
            .version("v020")
            .reference("my-replica-set-v013")
            .putMetadata("account", ACCOUNT)
            .build();
    ReplaceResult replaceResult =
        artifactReplacer.replaceAll(
            DEFAULT_DOCKER_IMAGE_BINDING, hpa, ImmutableList.of(inputArtifact), NAMESPACE, ACCOUNT);

    V1HorizontalPodAutoscaler replacedHpa =
        KubernetesCacheDataConverter.getResource(
            replaceResult.getManifest(), V1HorizontalPodAutoscaler.class);
    assertThat(replacedHpa.getSpec().getScaleTargetRef().getName())
        .isEqualTo("my-replica-set-v013");

    Set<Artifact> artifacts = replaceResult.getBoundArtifacts();
    assertThat(artifacts).hasSize(1);
    assertThat(Iterables.getOnlyElement(artifacts)).isEqualTo(inputArtifact);
  }

  @Test
  void replaceHpaWrongArtifact() {
    ArtifactReplacer artifactReplacer =
        new ArtifactReplacer(ImmutableList.of(Replacer.hpaReplicaSet()));
    KubernetesManifest hpa = getHpaForReplicaSet();

    // The input artifact has the correct name but is of type deployment; we should not replace
    // the reference.
    Artifact inputArtifact =
        Artifact.builder()
            .type("kubernetes/deployment")
            .name("my-replica-set")
            .location(NAMESPACE)
            .version("v020")
            .reference("my-replica-set-v013")
            .putMetadata("account", ACCOUNT)
            .build();
    ReplaceResult replaceResult =
        artifactReplacer.replaceAll(
            DEFAULT_DOCKER_IMAGE_BINDING, hpa, ImmutableList.of(inputArtifact), NAMESPACE, ACCOUNT);

    V1HorizontalPodAutoscaler replacedHpa =
        KubernetesCacheDataConverter.getResource(
            replaceResult.getManifest(), V1HorizontalPodAutoscaler.class);
    assertThat(replacedHpa.getSpec().getScaleTargetRef().getName()).isEqualTo("my-replica-set");

    Set<Artifact> artifacts = replaceResult.getBoundArtifacts();
    assertThat(artifacts).isEmpty();
  }

  private KubernetesManifest getHpaForDeployment() {
    String hpa =
        json.serialize(
            new V1HorizontalPodAutoscalerBuilder()
                .withNewSpec()
                .withScaleTargetRef(
                    new V1CrossVersionObjectReferenceBuilder()
                        .withKind("deployment")
                        .withName("my-deployment")
                        .build())
                .endSpec()
                .build());
    return gson.fromJson(hpa, KubernetesManifest.class);
  }

  private KubernetesManifest getHpaForReplicaSet() {
    String hpa =
        json.serialize(
            new V1HorizontalPodAutoscalerBuilder()
                .withNewSpec()
                .withScaleTargetRef(
                    new V1CrossVersionObjectReferenceBuilder()
                        .withKind("replicaSet")
                        .withName("my-replica-set")
                        .build())
                .endSpec()
                .build());
    return gson.fromJson(hpa, KubernetesManifest.class);
  }

  @Test
  void findCronJobDockerImages() {
    ArtifactReplacer artifactReplacer =
        new ArtifactReplacer(ImmutableList.of(Replacer.cronJobDockerImage()));
    KubernetesManifest cronJob = getCronJob();

    Set<Artifact> artifacts = artifactReplacer.findAll(cronJob);
    assertThat(artifacts).hasSize(2);

    Map<String, Artifact> byReference =
        artifacts.stream().collect(toImmutableMap(Artifact::getReference, a -> a));

    assertThat(byReference.get("gcr.io/my-repository/my-image:my-tag"))
        .satisfies(
            artifact -> {
              assertThat(artifact).isNotNull();
              assertThat(artifact.getType()).isEqualTo("docker/image");
              assertThat(artifact.getName()).isEqualTo("gcr.io/my-repository/my-image:my-tag");
              assertThat(artifact.getReference()).isEqualTo("gcr.io/my-repository/my-image:my-tag");
            });

    assertThat(byReference.get("gcr.io/my-other-repository/some-image"))
        .satisfies(
            artifact -> {
              assertThat(artifact).isNotNull();
              assertThat(artifact.getType()).isEqualTo("docker/image");
              assertThat(artifact.getName()).isEqualTo("gcr.io/my-other-repository/some-image");
              assertThat(artifact.getReference())
                  .isEqualTo("gcr.io/my-other-repository/some-image");
            });
  }

  @Test
  void replaceCronJobDockerImages() {
    ArtifactReplacer artifactReplacer =
        new ArtifactReplacer(ImmutableList.of(Replacer.cronJobDockerImage()));
    KubernetesManifest cronJob = getCronJob();

    Artifact inputArtifact =
        Artifact.builder()
            .type("docker/image")
            .name("gcr.io/my-other-repository/some-image")
            .reference("gcr.io/my-other-repository/some-image:some-tag")
            .build();
    ReplaceResult replaceResult =
        artifactReplacer.replaceAll(
            DEFAULT_DOCKER_IMAGE_BINDING,
            cronJob,
            ImmutableList.of(inputArtifact),
            NAMESPACE,
            ACCOUNT);

    V1beta1CronJob replacedCronJob =
        KubernetesCacheDataConverter.getResource(replaceResult.getManifest(), V1beta1CronJob.class);
    assertThat(
            replacedCronJob
                .getSpec()
                .getJobTemplate()
                .getSpec()
                .getTemplate()
                .getSpec()
                .getContainers())
        .extracting(V1Container::getImage)
        .containsExactly(
            // Only the second image should have been replaced.
            "gcr.io/my-repository/my-image:my-tag",
            "gcr.io/my-other-repository/some-image:some-tag");

    Set<Artifact> artifacts = replaceResult.getBoundArtifacts();
    assertThat(artifacts).hasSize(1);
    assertThat(Iterables.getOnlyElement(artifacts)).isEqualTo(inputArtifact);
  }

  private KubernetesManifest getCronJob() {
    String cronJob =
        json.serialize(
            new V1beta1CronJobBuilder()
                .withNewSpec()
                .withNewJobTemplate()
                .withNewSpec()
                .withNewTemplate()
                .withNewSpec()
                .addNewContainer()
                .withName("my-image-with-tag")
                .withImage("gcr.io/my-repository/my-image:my-tag")
                .endContainer()
                .addNewContainer()
                .withName("my-image-without-tag")
                .withImage("gcr.io/my-other-repository/some-image")
                .endContainer()
                .endSpec()
                .endTemplate()
                .endSpec()
                .endJobTemplate()
                .endSpec()
                .build());

    return gson.fromJson(cronJob, KubernetesManifest.class);
  }
}
