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

package com.netflix.spinnaker.clouddriver.kubernetes.op.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.netflix.spinnaker.clouddriver.data.task.DefaultTask;
import com.netflix.spinnaker.clouddriver.data.task.Task;
import com.netflix.spinnaker.clouddriver.kubernetes.description.manifest.KubernetesManifest;
import com.netflix.spinnaker.clouddriver.kubernetes.description.manifest.KubernetesManifestStrategy.DeployStrategy;
import com.netflix.spinnaker.clouddriver.kubernetes.op.OperationResult;
import com.netflix.spinnaker.clouddriver.kubernetes.security.KubernetesCredentials;
import com.netflix.spinnaker.clouddriver.kubernetes.security.KubernetesSelectorList;
import io.kubernetes.client.openapi.models.V1DeleteOptions;
import org.junit.jupiter.api.Test;

final class CanDeployTest {
  private final CanDeploy handler = new CanDeploy() {};
  private final String OP_NAME = "Can Deploy Test";
  private final Task task = new DefaultTask("task-id");

  @Test
  void applyMutations() {
    KubernetesCredentials credentials = mock(KubernetesCredentials.class);
    KubernetesManifest manifest = ManifestFetcher.getManifest("candeploy/deployment.yml");
    when(credentials.deploy(manifest, task, OP_NAME)).thenReturn(manifest);
    handler.deploy(credentials, manifest, DeployStrategy.APPLY, task, OP_NAME);
    verify(credentials).deploy(manifest, task, OP_NAME);
    verifyNoMoreInteractions(credentials);
  }

  @Test
  void applyReturnValue() {
    KubernetesCredentials credentials = mock(KubernetesCredentials.class);
    KubernetesManifest manifest = ManifestFetcher.getManifest("candeploy/deployment.yml");
    when(credentials.deploy(manifest, task, OP_NAME)).thenReturn(manifest);
    OperationResult result =
        handler.deploy(credentials, manifest, DeployStrategy.APPLY, task, OP_NAME);
    assertThat(result.getManifests()).containsExactlyInAnyOrder(manifest);
  }

  @Test
  void replaceMutations() {
    KubernetesCredentials credentials = mock(KubernetesCredentials.class);
    KubernetesManifest manifest = ManifestFetcher.getManifest("candeploy/deployment.yml");
    when(credentials.createOrReplace(manifest, task, OP_NAME)).thenReturn(manifest);
    handler.deploy(credentials, manifest, DeployStrategy.REPLACE, task, OP_NAME);
    verify(credentials).createOrReplace(manifest, task, OP_NAME);
    verifyNoMoreInteractions(credentials);
  }

  @Test
  void replaceReturnValue() {
    KubernetesCredentials credentials = mock(KubernetesCredentials.class);
    KubernetesManifest manifest = ManifestFetcher.getManifest("candeploy/deployment.yml");
    when(credentials.createOrReplace(manifest, task, OP_NAME)).thenReturn(manifest);
    OperationResult result =
        handler.deploy(credentials, manifest, DeployStrategy.REPLACE, task, OP_NAME);
    assertThat(result.getManifests()).containsExactlyInAnyOrder(manifest);
  }

  @Test
  void recreateMutations() {
    KubernetesCredentials credentials = mock(KubernetesCredentials.class);
    KubernetesManifest manifest = ManifestFetcher.getManifest("candeploy/deployment.yml");
    when(credentials.deploy(manifest, task, OP_NAME)).thenReturn(manifest);
    handler.deploy(credentials, manifest, DeployStrategy.RECREATE, task, OP_NAME);
    verify(credentials).deploy(manifest, task, OP_NAME);
    verify(credentials)
        .delete(
            eq(manifest.getKind()),
            eq(manifest.getNamespace()),
            eq(manifest.getName()),
            any(KubernetesSelectorList.class),
            any(V1DeleteOptions.class),
            any(Task.class),
            anyString());
    verifyNoMoreInteractions(credentials);
  }

  @Test
  void recreateReturnValue() {
    KubernetesCredentials credentials = mock(KubernetesCredentials.class);
    KubernetesManifest manifest = ManifestFetcher.getManifest("candeploy/deployment.yml");
    when(credentials.deploy(manifest, task, OP_NAME)).thenReturn(manifest);
    OperationResult result =
        handler.deploy(credentials, manifest, DeployStrategy.RECREATE, task, OP_NAME);
    assertThat(result.getManifests()).containsExactlyInAnyOrder(manifest);
  }

  @Test
  void createMutation() {
    KubernetesCredentials credentials = mock(KubernetesCredentials.class);
    KubernetesManifest manifest =
        ManifestFetcher.getManifest("candeploy/deployment-generate-name.yml");
    KubernetesManifest createResult =
        ManifestFetcher.getManifest("candeploy/deployment-generate-name-result.yml");
    when(credentials.create(manifest, task, OP_NAME)).thenReturn(createResult);
    handler.deploy(credentials, manifest, DeployStrategy.APPLY, task, OP_NAME);
    verify(credentials).create(manifest, task, OP_NAME);
    verifyNoMoreInteractions(credentials);
  }

  @Test
  void createReturnValue() {
    KubernetesCredentials credentials = mock(KubernetesCredentials.class);
    KubernetesManifest manifest =
        ManifestFetcher.getManifest("candeploy/deployment-generate-name.yml");
    KubernetesManifest createResult =
        ManifestFetcher.getManifest("candeploy/deployment-generate-name-result.yml");
    when(credentials.create(manifest, task, OP_NAME)).thenReturn(createResult);
    OperationResult result =
        handler.deploy(credentials, manifest, DeployStrategy.APPLY, task, OP_NAME);
    assertThat(result.getManifests()).containsExactlyInAnyOrder(createResult);
  }
}
