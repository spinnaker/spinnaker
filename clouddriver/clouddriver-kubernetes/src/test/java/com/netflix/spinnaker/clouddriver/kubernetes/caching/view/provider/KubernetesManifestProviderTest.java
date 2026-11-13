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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;
import com.netflix.spinnaker.clouddriver.kubernetes.description.manifest.KubernetesKind;
import com.netflix.spinnaker.clouddriver.kubernetes.description.manifest.KubernetesManifest;
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

    KubernetesManifest manifest1 = mock(KubernetesManifest.class);
    KubernetesManifest manifest2 = mock(KubernetesManifest.class);
    ImmutableList<KubernetesManifest> expectedManifests = ImmutableList.of(manifest1, manifest2);

    when(accountResolver.getCredentials(account)).thenReturn(Optional.of(credentials));
    when(credentials.list(
            eq(KubernetesKind.fromString(kind)), eq(location), any(KubernetesSelectorList.class)))
        .thenReturn(expectedManifests);

    // when
    List<KubernetesManifest> result =
        manifestProvider.getManifestsByLabels(account, location, kind, labelSelectors);

    // then
    assertThat(result).isNotNull();
    assertThat(result).isEqualTo(expectedManifests);
    verify(credentials)
        .list(eq(KubernetesKind.fromString(kind)), eq(location), any(KubernetesSelectorList.class));
  }
}
