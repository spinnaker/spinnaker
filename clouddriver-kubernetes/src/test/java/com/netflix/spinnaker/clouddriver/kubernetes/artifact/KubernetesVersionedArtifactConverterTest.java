/*
 * Copyright 2020 Armory
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

package com.netflix.spinnaker.clouddriver.kubernetes.artifact;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import com.netflix.spinnaker.clouddriver.kubernetes.caching.KubernetesV2Provider;
import com.netflix.spinnaker.clouddriver.kubernetes.description.manifest.KubernetesManifest;
import com.netflix.spinnaker.clouddriver.model.ArtifactProvider;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;

@RunWith(JUnitPlatform.class)
public class KubernetesVersionedArtifactConverterTest {

  @Test
  public void checkArtifactAcrossAccount() {
    KubernetesVersionedArtifactConverter converter = KubernetesVersionedArtifactConverter.INSTANCE;
    ArtifactProvider provider = mock(ArtifactProvider.class);
    List<Artifact> artifactList = new ArrayList<>();
    String name = "myname";

    artifactList.add(
        Artifact.builder()
            .metadata(ImmutableMap.of("account", "account1"))
            .version("v003")
            .build());
    artifactList.add(
        Artifact.builder()
            .metadata(ImmutableMap.of("account", "account2"))
            .version("v005")
            .build());

    when(provider.getArtifacts(KubernetesV2Provider.PROVIDER_NAME + "/pod", name, "ns"))
        .thenReturn(artifactList);
    Map<String, Object> manifestMap =
        ImmutableMap.of(
            "kind", "Pod", "metadata", ImmutableMap.of("name", name, "namespace", "ns"));
    ObjectMapper mapper = new ObjectMapper();
    KubernetesManifest manifest = mapper.convertValue(manifestMap, KubernetesManifest.class);

    Artifact artifact = converter.toArtifact(provider, manifest, "account1");
    assertThat(artifact).isNotNull();
    assertThat(artifact.getVersion()).isEqualTo("v004");
  }
}
