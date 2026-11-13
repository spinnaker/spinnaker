/*
 * Copyright 2026 DoorDash, Inc.
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
 */

package com.netflix.spinnaker.clouddriver.kubernetes.caching.view.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;
import com.netflix.spinnaker.clouddriver.kubernetes.caching.view.model.KubernetesManifestContainer;
import com.netflix.spinnaker.clouddriver.kubernetes.description.KubernetesResourceProperties;
import com.netflix.spinnaker.clouddriver.kubernetes.description.ResourcePropertyRegistry;
import com.netflix.spinnaker.clouddriver.kubernetes.description.manifest.KubernetesApiVersion;
import com.netflix.spinnaker.clouddriver.kubernetes.description.manifest.KubernetesKind;
import com.netflix.spinnaker.clouddriver.kubernetes.description.manifest.KubernetesManifest;
import com.netflix.spinnaker.clouddriver.kubernetes.op.handler.KubernetesDeploymentHandler;
import com.netflix.spinnaker.clouddriver.kubernetes.security.KubernetesCredentials;
import com.netflix.spinnaker.clouddriver.kubernetes.security.KubernetesSelectorList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class KubernetesManifestProviderTest {

  @Mock private KubernetesAccountResolver accountResolver;
  @Mock private KubernetesCredentials credentials;
  @Mock private ResourcePropertyRegistry resourcePropertyRegistry;

  private KubernetesManifestProvider manifestProvider;

  @BeforeEach
  void setup() {
    manifestProvider = new KubernetesManifestProvider(accountResolver);
  }

  @Test
  void getManifestsByLabelsShouldReturnManifests() {
    // given
    String account = "test-account";
    String location = "test-namespace";
    String kind = "Deployment";
    Map<String, String> labelSelectors = new HashMap<>();
    labelSelectors.put("app", "test-app");
    labelSelectors.put("env", "prod");

    KubernetesManifest manifest1 = deploymentManifest("manifest-1");
    KubernetesManifest manifest2 = deploymentManifest("manifest-2");
    ImmutableList<KubernetesManifest> expectedManifests = ImmutableList.of(manifest1, manifest2);

    when(accountResolver.getCredentials(account)).thenReturn(Optional.of(credentials));
    when(credentials.list(
            eq(List.of(KubernetesKind.fromString(kind))),
            eq(location),
            any(KubernetesSelectorList.class)))
        .thenReturn(expectedManifests);
    when(credentials.getResourcePropertyRegistry()).thenReturn(resourcePropertyRegistry);
    when(resourcePropertyRegistry.get(any()))
        .thenReturn(new KubernetesResourceProperties(new KubernetesDeploymentHandler(), false));

    // when
    List<KubernetesManifestContainer> result =
        manifestProvider.getManifestsByKindsAndLabels(
            account, location, List.of(kind), labelSelectors);

    // then
    assertThat(result).isNotNull();
    assertThat(result.size()).isEqualTo(2);

    List<KubernetesManifest> resultManifests =
        result.stream().map(KubernetesManifestContainer::getManifest).toList();

    assertThat(resultManifests).isEqualTo(expectedManifests);
    verify(credentials)
        .list(
            eq(List.of(KubernetesKind.fromString(kind))),
            eq(location),
            any(KubernetesSelectorList.class));
  }

  /** A test Deployment manifest */
  private static KubernetesManifest deploymentManifest(String deploymentName) {
    KubernetesManifest deployment = new KubernetesManifest();
    deployment.put("metadata", new HashMap<>());
    deployment.setNamespace("test-namespace");
    deployment.setKind(KubernetesKind.DEPLOYMENT);
    deployment.setApiVersion(KubernetesApiVersion.APPS_V1);
    deployment.setName(deploymentName);
    return deployment;
  }
}
