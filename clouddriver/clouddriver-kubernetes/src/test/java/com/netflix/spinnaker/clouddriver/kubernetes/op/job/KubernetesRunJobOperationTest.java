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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterators;
import com.netflix.spinnaker.clouddriver.data.task.DefaultTask;
import com.netflix.spinnaker.clouddriver.data.task.Task;
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository;
import com.netflix.spinnaker.clouddriver.kubernetes.KubernetesCloudProvider;
import com.netflix.spinnaker.clouddriver.kubernetes.artifact.ResourceVersioner;
import com.netflix.spinnaker.clouddriver.kubernetes.caching.view.provider.ArtifactProvider;
import com.netflix.spinnaker.clouddriver.kubernetes.config.KubernetesAccountProperties.ManagedAccount;
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
import com.netflix.spinnaker.clouddriver.kubernetes.security.KubernetesCredentials;
import com.netflix.spinnaker.clouddriver.kubernetes.security.KubernetesNamedAccountCredentials;
import com.netflix.spinnaker.clouddriver.kubernetes.security.KubernetesSelectorList;
import com.netflix.spinnaker.clouddriver.names.NamerRegistry;
import com.netflix.spinnaker.moniker.Namer;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

final class KubernetesRunJobOperationTest {
  private static final String NAMESPACE = "my-namespace";
  private static final String GENERATE_SUFFIX = "-abcd";
  private static final String DEPLOYED_JOB = "job my-job";
  private static final ResourcePropertyRegistry resourcePropertyRegistry =
      new GlobalResourcePropertyRegistry(
          ImmutableList.of(new KubernetesReplicaSetHandler(), new KubernetesServiceHandler()),
          new KubernetesUnregisteredCustomResourceHandler());
  private static final Namer<KubernetesManifest> NAMER = new KubernetesManifestNamer();

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
        .containsExactlyInAnyOrder(DEPLOYED_JOB);
  }

  @Test
  void deploysJobWithGenerateName() {
    KubernetesRunJobOperationDescription runJobDescription =
        baseJobDescription("job-generate-name.yml");
    OperationResult result = operate(runJobDescription);

    assertThat(result.getManifestNamesByNamespace()).containsOnlyKeys(NAMESPACE);
    assertThat(result.getManifestNamesByNamespace().get(NAMESPACE))
        .containsExactlyInAnyOrder(DEPLOYED_JOB + GENERATE_SUFFIX);
  }

  @Test
  void overridesNamespace() {
    String overrideNamespace = "override-namespace";
    KubernetesRunJobOperationDescription runJobDescription =
        baseJobDescription("job.yml").setNamespace(overrideNamespace);
    OperationResult result = operate(runJobDescription);

    assertThat(result.getManifestNamesByNamespace()).containsOnlyKeys(overrideNamespace);
    assertThat(result.getManifestNamesByNamespace().get(overrideNamespace)).hasSize(1);
    String job =
        Iterators.getOnlyElement(
            result.getManifestNamesByNamespace().get(overrideNamespace).iterator());
    // In this test, we don't care whether a suffix was added, we're just checking that the job
    // ended up in the right namespace, so we only check that the entry starts with the expected
    // job name.
    assertThat(job).startsWith(DEPLOYED_JOB);
  }

  private static KubernetesRunJobOperationDescription baseJobDescription(String manifest) {
    KubernetesRunJobOperationDescription runJobDescription =
        new KubernetesRunJobOperationDescription()
            .setManifest(
                ManifestFetcher.getManifest(KubernetesRunJobOperationTest.class, manifest).get(0));
    runJobDescription.setCredentials(getNamedAccountCredentials());
    return runJobDescription;
  }

  private static KubernetesNamedAccountCredentials getNamedAccountCredentials() {
    ManagedAccount managedAccount = new ManagedAccount();
    managedAccount.setName("my-account");

    NamerRegistry.lookup()
        .withProvider(KubernetesCloudProvider.ID)
        .withAccount(managedAccount.getName())
        .setNamer(KubernetesManifest.class, new KubernetesManifestNamer());

    KubernetesCredentials mockCredentials = getMockKubernetesCredential();
    KubernetesCredentials.Factory credentialFactory = mock(KubernetesCredentials.Factory.class);
    when(credentialFactory.build(managedAccount)).thenReturn(mockCredentials);
    return new KubernetesNamedAccountCredentials(managedAccount, credentialFactory);
  }

  private static KubernetesCredentials getMockKubernetesCredential() {
    KubernetesCredentials credentialsMock = mock(KubernetesCredentials.class);
    when(credentialsMock.getKindProperties(any(KubernetesKind.class)))
        .thenAnswer(
            invocation ->
                KubernetesKindProperties.withDefaultProperties(
                    invocation.getArgument(0, KubernetesKind.class)));
    when(credentialsMock.getResourcePropertyRegistry()).thenReturn(resourcePropertyRegistry);
    when(credentialsMock.deploy(
            any(KubernetesManifest.class),
            any(Task.class),
            anyString(),
            any(KubernetesSelectorList.class)))
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
    when(credentialsMock.create(
            any(KubernetesManifest.class),
            any(Task.class),
            anyString(),
            any(KubernetesSelectorList.class)))
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
    when(credentialsMock.getNamer()).thenReturn(NAMER);
    return credentialsMock;
  }

  private static OperationResult operate(KubernetesRunJobOperationDescription description) {
    ArtifactProvider artifactProvider = mock(ArtifactProvider.class);
    when(artifactProvider.getArtifacts(
            any(KubernetesKind.class),
            any(String.class),
            any(String.class),
            any(KubernetesCredentials.class)))
        .thenReturn(ImmutableList.of());
    ResourceVersioner resourceVersioner = new ResourceVersioner(artifactProvider);
    return new KubernetesRunJobOperation(description, resourceVersioner)
        .operate(ImmutableList.of());
  }
}
