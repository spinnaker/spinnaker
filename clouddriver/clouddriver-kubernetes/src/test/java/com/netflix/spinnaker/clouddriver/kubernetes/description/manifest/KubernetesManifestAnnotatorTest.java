/*
 * Copyright 2022 Salesforce.com, Inc.
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

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import com.netflix.spinnaker.moniker.Moniker;
import java.util.HashMap;
import org.junit.jupiter.api.Test;

public class KubernetesManifestAnnotatorTest {
  @Test
  public void testDeriveMonikerForAnUnversionedManifest() {
    // when:
    Moniker moniker =
        KubernetesManifestAnnotater.getMoniker(manifest("testapp-abc", KubernetesKind.DEPLOYMENT));

    // then:
    assertThat(moniker.getApp()).isEqualTo("testapp");
    assertThat(moniker.getCluster()).isEqualTo("testapp-abc");
    assertThat(moniker.getStack()).isNull();
    assertThat(moniker.getDetail()).isNull();
    assertThat(moniker.getSequence()).isNull();
  }

  @Test
  public void testDeriveMonikerForAVersionedManifestName() {
    // when:
    Moniker moniker =
        KubernetesManifestAnnotater.getMoniker(
            manifest("testapp-abc-v003", KubernetesKind.DEPLOYMENT));

    // then:
    assertThat(moniker.getApp()).isEqualTo("testapp");
    assertThat(moniker.getCluster()).isEqualTo("testapp-abc");
    assertThat(moniker.getStack()).isNull();
    assertThat(moniker.getDetail()).isNull();
    assertThat(moniker.getSequence()).isEqualTo(3);
  }

  @Test
  public void testDeriveMonikerForAnUnversionedKubernetesSystemResource() {
    // when:
    Moniker moniker =
        KubernetesManifestAnnotater.getMoniker(
            manifest("system:coredns", KubernetesKind.DEPLOYMENT));

    // then:
    assertThat(moniker).isNotNull();
    assertThat(moniker.getApp()).isEqualTo("system");
    assertThat(moniker.getCluster()).isEqualTo("system:coredns");
    assertThat(moniker.getStack()).isNull();
    assertThat(moniker.getDetail()).isNull();
    assertThat(moniker.getSequence()).isNull();
  }

  @Test
  public void testDeriveMonikerForAnUnversionedKubernetesSystemResourceWithMultipleColons() {
    // when:
    Moniker moniker =
        KubernetesManifestAnnotater.getMoniker(
            manifest(
                "system:certificates.k8s.io:certificatesigningrequests:nodeclient",
                KubernetesKind.CLUSTER_ROLE));

    // then:
    assertThat(moniker).isNotNull();
    assertThat(moniker.getApp()).isEqualTo("system");
    assertThat(moniker.getCluster())
        .isEqualTo("system:certificates.k8s.io:certificatesigningrequests:nodeclient");
    assertThat(moniker.getStack()).isNull();
    assertThat(moniker.getDetail()).isNull();
    assertThat(moniker.getSequence()).isNull();
  }

  @Test
  public void testDeriveMonikerForAVersionedKubernetesSystemResource() {
    // when:
    Moniker moniker =
        KubernetesManifestAnnotater.getMoniker(
            manifest(
                "system:certificates.k8s.io:certificatesigningrequests:nodeclient-v003",
                KubernetesKind.CLUSTER_ROLE));

    // then:
    assertThat(moniker).isNotNull();
    assertThat(moniker.getApp()).isEqualTo("system");
    assertThat(moniker.getCluster())
        .isEqualTo("system:certificates.k8s.io:certificatesigningrequests:nodeclient");
    assertThat(moniker.getStack()).isNull();
    assertThat(moniker.getDetail()).isNull();
    assertThat(moniker.getSequence()).isEqualTo(3);
  }

  /** A test manifest */
  private static KubernetesManifest manifest(String deploymentName, KubernetesKind kind) {
    KubernetesManifest deployment = new KubernetesManifest();
    deployment.put("metadata", new HashMap<>());
    deployment.setNamespace("namespace");
    deployment.setKind(kind);
    deployment.setApiVersion(KubernetesApiVersion.APPS_V1);
    deployment.setName(deploymentName);
    return deployment;
  }
}
