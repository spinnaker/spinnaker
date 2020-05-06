/*
 * Copyright 2020 Google, Inc.
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

package com.netflix.spinnaker.clouddriver.kubernetes.op.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.netflix.spinnaker.clouddriver.data.task.DefaultTask;
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository;
import com.netflix.spinnaker.clouddriver.kubernetes.KubernetesCloudProvider;
import com.netflix.spinnaker.clouddriver.kubernetes.config.KubernetesConfigurationProperties;
import com.netflix.spinnaker.clouddriver.kubernetes.description.GlobalResourcePropertyRegistry;
import com.netflix.spinnaker.clouddriver.kubernetes.description.ResourcePropertyRegistry;
import com.netflix.spinnaker.clouddriver.kubernetes.description.job.KubernetesRunJobOperationDescription;
import com.netflix.spinnaker.clouddriver.kubernetes.description.manifest.KubernetesKind;
import com.netflix.spinnaker.clouddriver.kubernetes.description.manifest.KubernetesKindProperties;
import com.netflix.spinnaker.clouddriver.kubernetes.description.manifest.KubernetesManifest;
import com.netflix.spinnaker.clouddriver.kubernetes.names.KubernetesManifestNamer;
import com.netflix.spinnaker.clouddriver.kubernetes.op.OperationResult;
import com.netflix.spinnaker.clouddriver.kubernetes.op.handler.KubernetesReplicaSetHandler;
import com.netflix.spinnaker.clouddriver.kubernetes.op.handler.KubernetesServiceHandler;
import com.netflix.spinnaker.clouddriver.kubernetes.op.handler.KubernetesUnregisteredCustomResourceHandler;
import com.netflix.spinnaker.clouddriver.kubernetes.op.handler.ManifestFetcher;
import com.netflix.spinnaker.clouddriver.kubernetes.security.KubernetesNamedAccountCredentials;
import com.netflix.spinnaker.clouddriver.kubernetes.security.KubernetesV2Credentials;
import com.netflix.spinnaker.clouddriver.model.ArtifactProvider;
import com.netflix.spinnaker.clouddriver.names.NamerRegistry;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;

@RunWith(JUnitPlatform.class)
final class KubernetesRunJobOperationTest {
  private static final String NAMESPACE = "my-namespace";
  private static final String GENERATE_SUFFIX = "-abcd";
  private static final ResourcePropertyRegistry resourcePropertyRegistry =
      new GlobalResourcePropertyRegistry(
          ImmutableList.of(new KubernetesReplicaSetHandler(), new KubernetesServiceHandler()),
          new KubernetesUnregisteredCustomResourceHandler());

  @BeforeEach
  void setTask() {
    TaskRepository.threadLocalTask.set(new DefaultTask("task-id"));
  }

  @Test
  void deploysJobWithName() {
    KubernetesRunJobOperationDescription runJobDescription = baseJobDescription("job.yml");
    OperationResult result = operate(runJobDescription);

    assertThat(result.getManifestNamesByNamespace()).containsOnlyKeys(NAMESPACE);
    assertThat(result.getManifestNamesByNamespace().get(NAMESPACE))
        .containsExactlyInAnyOrder("job my-job");
  }

  @Test
  void deploysJobWithGenerateName() {
    KubernetesRunJobOperationDescription runJobDescription =
        baseJobDescription("job-generate-name.yml");
    OperationResult result = operate(runJobDescription);

    assertThat(result.getManifestNamesByNamespace()).containsOnlyKeys(NAMESPACE);
    assertThat(result.getManifestNamesByNamespace().get(NAMESPACE))
        .containsExactlyInAnyOrder("job my-job" + GENERATE_SUFFIX);
  }

  @Test
  void overridesNamespace() {
    String overrideNamespace = "override-namespace";
    KubernetesRunJobOperationDescription runJobDescription =
        baseJobDescription("job.yml").setNamespace(overrideNamespace);
    OperationResult result = operate(runJobDescription);

    assertThat(result.getManifestNamesByNamespace()).containsOnlyKeys(overrideNamespace);
    assertThat(result.getManifestNamesByNamespace().get(overrideNamespace))
        .containsExactlyInAnyOrder("job my-job");
  }

  private static KubernetesRunJobOperationDescription baseJobDescription(String manifest) {
    KubernetesRunJobOperationDescription runJobDescription =
        new KubernetesRunJobOperationDescription()
            .setManifest(
                ManifestFetcher.getManifest(KubernetesRunJobOperationTest.class, manifest));
    runJobDescription.setCredentials(getNamedAccountCredentials());
    return runJobDescription;
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
    when(credentialsMock.deploy(any(KubernetesManifest.class)))
        .thenAnswer(
            invocation -> {
              KubernetesManifest result =
                  invocation.getArgument(0, KubernetesManifest.class).clone();
              if (Strings.isNullOrEmpty(result.getName())) {
                // We can't apply if there is no name; throw an exception here
                throw new KubectlJobExecutor.KubectlException(
                    "error: error when retrieving current configuration");
              }
              return result;
            });
    when(credentialsMock.create(any(KubernetesManifest.class)))
        .thenAnswer(
            invocation -> {
              // This simulates the fact that the Kubernetes API will add a suffix to a generated
              // name.
              KubernetesManifest result =
                  invocation.getArgument(0, KubernetesManifest.class).clone();
              if (Strings.isNullOrEmpty(result.getName())) {
                Map<String, String> metadata = (Map<String, String>) result.get("metadata");
                metadata.put("name", metadata.get("generateName") + GENERATE_SUFFIX);
              }
              return result;
            });
    return credentialsMock;
  }

  private static OperationResult operate(KubernetesRunJobOperationDescription description) {
    ArtifactProvider provider = mock(ArtifactProvider.class);
    when(provider.getArtifacts(any(String.class), any(String.class), any(String.class)))
        .thenReturn(ImmutableList.of());
    return new KubernetesRunJobOperation(description, provider).operate(ImmutableList.of());
  }
}
