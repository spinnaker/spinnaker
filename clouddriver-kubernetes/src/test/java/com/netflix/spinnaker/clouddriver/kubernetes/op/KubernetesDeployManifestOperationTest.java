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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.netflix.spinnaker.clouddriver.data.task.DefaultTask;
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository;
import com.netflix.spinnaker.clouddriver.kubernetes.KubernetesCloudProvider;
import com.netflix.spinnaker.clouddriver.kubernetes.config.KubernetesConfigurationProperties;
import com.netflix.spinnaker.clouddriver.kubernetes.description.GlobalResourcePropertyRegistry;
import com.netflix.spinnaker.clouddriver.kubernetes.description.ResourcePropertyRegistry;
import com.netflix.spinnaker.clouddriver.kubernetes.description.manifest.KubernetesDeployManifestDescription;
import com.netflix.spinnaker.clouddriver.kubernetes.description.manifest.KubernetesKind;
import com.netflix.spinnaker.clouddriver.kubernetes.description.manifest.KubernetesKindProperties;
import com.netflix.spinnaker.clouddriver.kubernetes.description.manifest.KubernetesManifest;
import com.netflix.spinnaker.clouddriver.kubernetes.description.manifest.KubernetesManifestAnnotater;
import com.netflix.spinnaker.clouddriver.kubernetes.description.manifest.KubernetesManifestTraffic;
import com.netflix.spinnaker.clouddriver.kubernetes.names.KubernetesManifestNamer;
import com.netflix.spinnaker.clouddriver.kubernetes.op.handler.KubernetesReplicaSetHandler;
import com.netflix.spinnaker.clouddriver.kubernetes.op.handler.KubernetesServiceHandler;
import com.netflix.spinnaker.clouddriver.kubernetes.op.handler.KubernetesUnregisteredCustomResourceHandler;
import com.netflix.spinnaker.clouddriver.kubernetes.op.handler.ManifestFetcher;
import com.netflix.spinnaker.clouddriver.kubernetes.op.manifest.KubernetesDeployManifestOperation;
import com.netflix.spinnaker.clouddriver.kubernetes.security.KubernetesNamedAccountCredentials;
import com.netflix.spinnaker.clouddriver.kubernetes.security.KubernetesV2Credentials;
import com.netflix.spinnaker.clouddriver.model.ArtifactProvider;
import com.netflix.spinnaker.clouddriver.names.NamerRegistry;
import com.netflix.spinnaker.moniker.Moniker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;

@RunWith(JUnitPlatform.class)
final class KubernetesDeployManifestOperationTest {
  private static final String DEFAULT_NAMESPACE = "default-namespace";
  private static final ResourcePropertyRegistry resourcePropertyRegistry =
      new GlobalResourcePropertyRegistry(
          ImmutableList.of(new KubernetesReplicaSetHandler(), new KubernetesServiceHandler()),
          new KubernetesUnregisteredCustomResourceHandler());

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
  void replicaSetDeployerUsesOverrideNamespace() {
    String overrideNamespace = "my-override";
    KubernetesDeployManifestDescription description =
        baseDeployDescription("deploy/replicaset-no-namespace.yml")
            .setNamespaceOverride(overrideNamespace);
    OperationResult result = deploy(description);

    assertThat(result.getManifestNamesByNamespace()).containsOnlyKeys(overrideNamespace);
    assertThat(result.getManifestNamesByNamespace().get(overrideNamespace))
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
    assertThat(manifest.getLabels()).contains(entry("selector-key", "selector-value"));

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
    assertThat(manifest.getLabels()).doesNotContain(entry("selector-key", "selector-value"));

    KubernetesManifestTraffic traffic = KubernetesManifestAnnotater.getTraffic(manifest);
    assertThat(traffic.getLoadBalancers()).containsExactly("service my-service");
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

  private static KubernetesDeployManifestDescription baseDeployDescription(String manifest) {
    KubernetesDeployManifestDescription deployManifestDescription =
        new KubernetesDeployManifestDescription()
            .setManifests(
                ImmutableList.of(
                    ManifestFetcher.getManifest(
                        KubernetesDeployManifestOperationTest.class, manifest)))
            .setMoniker(new Moniker())
            .setSource(KubernetesDeployManifestDescription.Source.text);
    deployManifestDescription.setCredentials(getNamedAccountCredentials());
    return deployManifestDescription;
  }

  private static KubernetesNamedAccountCredentials<KubernetesV2Credentials>
      getNamedAccountCredentials() {
    KubernetesConfigurationProperties.ManagedAccount managedAccount =
        new KubernetesConfigurationProperties.ManagedAccount();
    managedAccount.setName("my-account");

    NamerRegistry.lookup()
        .withProvider(KubernetesCloudProvider.ID)
        .withAccount(managedAccount.getName())
        .setNamer(KubernetesManifest.class, new KubernetesManifestNamer());

    KubernetesV2Credentials mockV2Credentials = getMockKubernetesV2Credentials();
    KubernetesV2Credentials.Factory credentialFactory = mock(KubernetesV2Credentials.Factory.class);
    when(credentialFactory.build(managedAccount)).thenReturn(mockV2Credentials);
    return new KubernetesNamedAccountCredentials<>(managedAccount, credentialFactory);
  }

  private static KubernetesV2Credentials getMockKubernetesV2Credentials() {
    KubernetesV2Credentials credentialsMock = mock(KubernetesV2Credentials.class);
    when(credentialsMock.getKindProperties(any(KubernetesKind.class)))
        .thenAnswer(
            invocation ->
                KubernetesKindProperties.withDefaultProperties(
                    invocation.getArgument(0, KubernetesKind.class)));
    when(credentialsMock.getResourcePropertyRegistry()).thenReturn(resourcePropertyRegistry);
    when(credentialsMock.get(KubernetesKind.SERVICE, "my-namespace", "my-service"))
        .thenReturn(
            ManifestFetcher.getManifest(
                KubernetesDeployManifestOperationTest.class, "deploy/service.yml"));
    when(credentialsMock.get(KubernetesKind.SERVICE, "my-namespace", "my-service-no-selector"))
        .thenReturn(
            ManifestFetcher.getManifest(
                KubernetesDeployManifestOperationTest.class, "deploy/service-no-selector.yml"));
    when(credentialsMock.deploy(any(KubernetesManifest.class)))
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
    return credentialsMock;
  }

  private static OperationResult deploy(KubernetesDeployManifestDescription description) {
    ArtifactProvider provider = mock(ArtifactProvider.class);
    when(provider.getArtifacts(any(String.class), any(String.class), any(String.class)))
        .thenReturn(ImmutableList.of());
    return new KubernetesDeployManifestOperation(description, provider).operate(ImmutableList.of());
  }
}
