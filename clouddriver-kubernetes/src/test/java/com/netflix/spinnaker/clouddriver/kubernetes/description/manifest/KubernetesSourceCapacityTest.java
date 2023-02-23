/*
 * Copyright 2023 Netflix, Inc.
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

package com.netflix.spinnaker.clouddriver.kubernetes.description.manifest;

import com.netflix.spinnaker.clouddriver.kubernetes.description.KubernetesCoordinates;
import com.netflix.spinnaker.clouddriver.kubernetes.security.KubernetesCredentials;
import java.util.HashMap;
import java.util.OptionalInt;
import org.assertj.core.api.Assertions;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

public class KubernetesSourceCapacityTest {

  public static final String MANIFEST_NAME = "my-manifest";
  public static final String NAMESPACE = "my-namespace";

  @Test
  public void testInitialSourceCapacityNonVersioned() {
    // given:
    OptionalInt currentVersion = OptionalInt.empty(); // non-versioned manifest
    KubernetesManifest manifest = getKubernetesManifest(KubernetesKind.REPLICA_SET); // any manifest

    //   no previous manifest is found
    KubernetesCredentials credentials = Mockito.mock(KubernetesCredentials.class);
    Mockito.doReturn(null)
        .when(credentials)
        .get(matchCoords(KubernetesKind.REPLICA_SET, MANIFEST_NAME));

    // when:
    Integer ret = KubernetesSourceCapacity.getSourceCapacity(manifest, credentials, currentVersion);

    // then:
    Assertions.assertThat(ret).isNull();
    Mockito.verify(credentials, Mockito.only())
        .get(matchCoords(KubernetesKind.REPLICA_SET, MANIFEST_NAME));
  }

  @Test
  public void testInitialSourceCapacityVersioned() {
    // given:
    String manifestName = MANIFEST_NAME + "-v000";
    OptionalInt currentVersion = OptionalInt.of(0); // versioned manifest
    KubernetesManifest manifest = getKubernetesManifest(KubernetesKind.REPLICA_SET); // any manifest

    //   no previous manifest is found
    KubernetesCredentials credentials = Mockito.mock(KubernetesCredentials.class);
    Mockito.doReturn(null)
        .when(credentials)
        .get(matchCoords(KubernetesKind.REPLICA_SET, manifestName));

    // when:
    Integer ret = KubernetesSourceCapacity.getSourceCapacity(manifest, credentials, currentVersion);

    // then:
    Assertions.assertThat(ret).isNull();
    Mockito.verify(credentials, Mockito.only())
        .get(matchCoords(KubernetesKind.REPLICA_SET, manifestName));
  }

  @Test
  public void testSubsequentSourceCapacityNonVersioned() {
    // given:
    int previousCapacity = 5;
    String previousManifestName = MANIFEST_NAME;
    OptionalInt currentVersion = OptionalInt.empty(); // non-versioned
    KubernetesManifest manifest = getKubernetesManifest(KubernetesKind.REPLICA_SET); // any manifest
    KubernetesManifest previousManifest = manifest.clone();
    previousManifest.setReplicas(5);

    //   previous manifest is found
    KubernetesCredentials credentials = Mockito.mock(KubernetesCredentials.class);
    Mockito.doReturn(previousManifest)
        .when(credentials)
        .get(matchCoords(KubernetesKind.REPLICA_SET, previousManifestName));

    // when:
    Integer ret = KubernetesSourceCapacity.getSourceCapacity(manifest, credentials, currentVersion);

    // then:
    Assertions.assertThat(ret).isEqualTo(previousCapacity);
    Mockito.verify(credentials, Mockito.only())
        .get(matchCoords(KubernetesKind.REPLICA_SET, previousManifestName));
  }

  @Test
  public void testSubsequentSourceCapacityVersioned() {
    // given:
    int previousCapacity = 5;
    int previousVersion = 2;
    String previousManifestName = MANIFEST_NAME + "-v002";
    OptionalInt currentVersion = OptionalInt.of(previousVersion); // versioned manifest
    KubernetesManifest manifest = getKubernetesManifest(KubernetesKind.REPLICA_SET); // any manifest
    KubernetesManifest previousManifest = manifest.clone();
    previousManifest.setReplicas(5);

    //   previous manifest is found
    KubernetesCredentials credentials = Mockito.mock(KubernetesCredentials.class);
    Mockito.doReturn(previousManifest)
        .when(credentials)
        .get(matchCoords(KubernetesKind.REPLICA_SET, previousManifestName));

    // when:
    Integer ret = KubernetesSourceCapacity.getSourceCapacity(manifest, credentials, currentVersion);

    // then:
    Assertions.assertThat(ret).isEqualTo(previousCapacity);
    Mockito.verify(credentials, Mockito.only())
        .get(matchCoords(KubernetesKind.REPLICA_SET, previousManifestName));
  }

  @Test
  public void testVersionedNotFound() {
    // given:
    int previousVersion = 2;
    String previousManifestName = MANIFEST_NAME + "-v002";
    OptionalInt currentVersion = OptionalInt.of(previousVersion); // versioned manifest
    KubernetesManifest manifest = getKubernetesManifest(KubernetesKind.REPLICA_SET); // any manifest

    //   previous manifest is found
    KubernetesCredentials credentials = Mockito.mock(KubernetesCredentials.class);
    Mockito.doReturn(null)
        .when(credentials)
        .get(matchCoords(KubernetesKind.REPLICA_SET, previousManifestName));

    // when:
    Integer ret = KubernetesSourceCapacity.getSourceCapacity(manifest, credentials, currentVersion);

    // then:
    Assertions.assertThat(ret).isNull();
    Mockito.verify(credentials, Mockito.only())
        .get(matchCoords(KubernetesKind.REPLICA_SET, previousManifestName));
  }

  @Test
  public void testNonVersionedNotFound() {
    // given:
    String previousManifestName = MANIFEST_NAME;
    OptionalInt currentVersion = OptionalInt.empty(); // versioned manifest
    KubernetesManifest manifest = getKubernetesManifest(KubernetesKind.REPLICA_SET); // any manifest

    //   previous manifest is found
    KubernetesCredentials credentials = Mockito.mock(KubernetesCredentials.class);
    Mockito.doReturn(null)
        .when(credentials)
        .get(matchCoords(KubernetesKind.REPLICA_SET, previousManifestName));

    // when:
    Integer ret = KubernetesSourceCapacity.getSourceCapacity(manifest, credentials, currentVersion);

    // then:
    Assertions.assertThat(ret).isNull();
    Mockito.verify(credentials, Mockito.only())
        .get(matchCoords(KubernetesKind.REPLICA_SET, previousManifestName));
  }

  @NotNull
  private static KubernetesManifest getKubernetesManifest(KubernetesKind kind) {
    KubernetesManifest manifest = new KubernetesManifest(); // any manifest
    manifest.put("metadata", new HashMap<String, String>());
    manifest.put("spec", new HashMap<String, String>());
    manifest.setKind(kind);
    manifest.setName(MANIFEST_NAME);
    manifest.setNamespace(NAMESPACE);
    return manifest;
  }

  private static KubernetesCoordinates matchCoords(KubernetesKind kind, String manifestName) {
    return Mockito.argThat(
        a ->
            a.getKind().equals(kind)
                && a.getName().equals(manifestName)
                && a.getNamespace().equals(NAMESPACE));
  }
}
