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

package com.netflix.spinnaker.clouddriver.kubernetes.caching.agent;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.clouddriver.kubernetes.caching.Keys;
import com.netflix.spinnaker.clouddriver.kubernetes.description.manifest.KubernetesManifest;
import java.io.IOException;
import java.io.InputStream;
import java.util.Set;
import org.junit.jupiter.api.Test;

public class KubernetesCacheDataConverterTest {

  @Test
  public void testOwnerRefUnregisteredKind() throws IOException {
    try (InputStream stream = KubernetesManifest.class.getResourceAsStream("owned-manifest.json")) {
      ObjectMapper objectMapper = new ObjectMapper();
      KubernetesManifest manifest = objectMapper.readValue(stream, KubernetesManifest.class);
      Set<Keys.CacheKey> ownerKeys =
          KubernetesCacheDataConverter.ownerReferenceRelationships(
              "account", "ns", manifest.getOwnerReferences());

      assertThat(ownerKeys).hasSize(1);
      ownerKeys.stream()
          .findFirst()
          .ifPresent(key -> assertThat(key.getGroup()).isEqualTo("Owner.group"));
    }
  }
}
