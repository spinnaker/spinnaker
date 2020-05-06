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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.netflix.spinnaker.clouddriver.kubernetes.description.manifest.KubernetesManifest;
import com.netflix.spinnaker.clouddriver.kubernetes.description.manifest.KubernetesManifestStrategy.DeployStrategy;
import com.netflix.spinnaker.clouddriver.kubernetes.op.OperationResult;
import com.netflix.spinnaker.clouddriver.kubernetes.security.KubernetesSelectorList;
import com.netflix.spinnaker.clouddriver.kubernetes.security.KubernetesV2Credentials;
import io.kubernetes.client.openapi.models.V1DeleteOptions;
import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;

@RunWith(JUnitPlatform.class)
final class CanDeployTest {
  private final CanDeploy handler = new CanDeploy() {};

  @Test
  void applyMutations() {
    KubernetesV2Credentials credentials = mock(KubernetesV2Credentials.class);
    KubernetesManifest manifest = ManifestFetcher.getManifest("candeploy/deployment.yml");
    when(credentials.deploy(manifest)).thenReturn(manifest);
    handler.deploy(credentials, manifest, DeployStrategy.APPLY);
    verify(credentials).deploy(manifest);
    verifyNoMoreInteractions(credentials);
  }

  @Test
  void applyReturnValue() {
    KubernetesV2Credentials credentials = mock(KubernetesV2Credentials.class);
    KubernetesManifest manifest = ManifestFetcher.getManifest("candeploy/deployment.yml");
    when(credentials.deploy(manifest)).thenReturn(manifest);
    OperationResult result = handler.deploy(credentials, manifest, DeployStrategy.APPLY);
    assertThat(result.getManifests()).containsExactlyInAnyOrder(manifest);
  }

  @Test
  void replaceMutations() {
    KubernetesV2Credentials credentials = mock(KubernetesV2Credentials.class);
    KubernetesManifest manifest = ManifestFetcher.getManifest("candeploy/deployment.yml");
    when(credentials.createOrReplace(manifest)).thenReturn(manifest);
    handler.deploy(credentials, manifest, DeployStrategy.REPLACE);
    verify(credentials).createOrReplace(manifest);
    verifyNoMoreInteractions(credentials);
  }

  @Test
  void replaceReturnValue() {
    KubernetesV2Credentials credentials = mock(KubernetesV2Credentials.class);
    KubernetesManifest manifest = ManifestFetcher.getManifest("candeploy/deployment.yml");
    when(credentials.createOrReplace(manifest)).thenReturn(manifest);
    OperationResult result = handler.deploy(credentials, manifest, DeployStrategy.REPLACE);
    assertThat(result.getManifests()).containsExactlyInAnyOrder(manifest);
  }

  @Test
  void recreateMutations() {
    KubernetesV2Credentials credentials = mock(KubernetesV2Credentials.class);
    KubernetesManifest manifest = ManifestFetcher.getManifest("candeploy/deployment.yml");
    when(credentials.deploy(manifest)).thenReturn(manifest);
    handler.deploy(credentials, manifest, DeployStrategy.RECREATE);
    verify(credentials).deploy(manifest);
    verify(credentials)
        .delete(
            eq(manifest.getKind()),
            eq(manifest.getNamespace()),
            eq(manifest.getName()),
            any(KubernetesSelectorList.class),
            any(V1DeleteOptions.class));
    verifyNoMoreInteractions(credentials);
  }

  @Test
  void recreateReturnValue() {
    KubernetesV2Credentials credentials = mock(KubernetesV2Credentials.class);
    KubernetesManifest manifest = ManifestFetcher.getManifest("candeploy/deployment.yml");
    when(credentials.deploy(manifest)).thenReturn(manifest);
    OperationResult result = handler.deploy(credentials, manifest, DeployStrategy.RECREATE);
    assertThat(result.getManifests()).containsExactlyInAnyOrder(manifest);
  }

  @Test
  void createMutation() {
    KubernetesV2Credentials credentials = mock(KubernetesV2Credentials.class);
    KubernetesManifest manifest =
        ManifestFetcher.getManifest("candeploy/deployment-generate-name.yml");
    KubernetesManifest createResult =
        ManifestFetcher.getManifest("candeploy/deployment-generate-name-result.yml");
    when(credentials.create(manifest)).thenReturn(createResult);
    handler.deploy(credentials, manifest, DeployStrategy.APPLY);
    verify(credentials).create(manifest);
    verifyNoMoreInteractions(credentials);
  }

  @Test
  void createReturnValue() {
    KubernetesV2Credentials credentials = mock(KubernetesV2Credentials.class);
    KubernetesManifest manifest =
        ManifestFetcher.getManifest("candeploy/deployment-generate-name.yml");
    KubernetesManifest createResult =
        ManifestFetcher.getManifest("candeploy/deployment-generate-name-result.yml");
    when(credentials.create(manifest)).thenReturn(createResult);
    OperationResult result = handler.deploy(credentials, manifest, DeployStrategy.APPLY);
    assertThat(result.getManifests()).containsExactlyInAnyOrder(createResult);
  }
}
