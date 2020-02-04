/*
 * Copyright 2020 Google, LLC
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

package com.netflix.spinnaker.clouddriver.kubernetes.v2.op.handler;

import static org.assertj.core.api.Assertions.assertThat;

import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesManifest;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.model.Manifest.Status;
import org.junit.jupiter.api.Test;

final class KubernetesReplicaSetHandlerTest {
  private KubernetesReplicaSetHandler handler = new KubernetesReplicaSetHandler();

  @Test
  void noStatus() {
    KubernetesManifest replicaSet = ManifestFetcher.getManifest("replicaset/base.yml");
    Status status = handler.status(replicaSet);

    assertThat(status.getStable().isState()).isFalse();
    assertThat(status.getStable().getMessage()).isEqualTo("No status reported yet");
    assertThat(status.getAvailable().isState()).isFalse();
    assertThat(status.getAvailable().getMessage()).isEqualTo("No availability reported");
    assertThat(status.getPaused().isState()).isFalse();
    assertThat(status.getFailed().isState()).isFalse();
  }

  @Test
  void stable() {
    KubernetesManifest replicaSet =
        ManifestFetcher.getManifest("replicaset/base.yml", "replicaset/stable.yml");
    Status status = handler.status(replicaSet);

    assertThat(status.getStable().isState()).isTrue();
    assertThat(status.getAvailable().isState()).isTrue();
    assertThat(status.getPaused().isState()).isFalse();
    assertThat(status.getFailed().isState()).isFalse();
  }

  @Test
  void oldGeneration() {
    KubernetesManifest replicaSet =
        ManifestFetcher.getManifest("replicaset/base.yml", "replicaset/old-generation.yml");
    Status status = handler.status(replicaSet);

    assertThat(status.getStable().isState()).isFalse();
    assertThat(status.getStable().getMessage())
        .isEqualTo("Waiting for replicaset spec update to be observed");
    assertThat(status.getAvailable().isState()).isTrue();
    assertThat(status.getPaused().isState()).isFalse();
    assertThat(status.getFailed().isState()).isFalse();
  }

  @Test
  void awaitingFullyLabeled() {
    KubernetesManifest replicaSet =
        ManifestFetcher.getManifest("replicaset/base.yml", "replicaset/awaiting-fully-labeled.yml");
    Status status = handler.status(replicaSet);

    assertThat(status.getStable().isState()).isFalse();
    assertThat(status.getStable().getMessage())
        .isEqualTo("Waiting for all replicas to be fully-labeled");
    assertThat(status.getAvailable().isState()).isFalse();
    assertThat(status.getAvailable().getMessage())
        .isEqualTo("Not all replicas have become labeled yet");
    assertThat(status.getPaused().isState()).isFalse();
    assertThat(status.getFailed().isState()).isFalse();
  }

  @Test
  void awaitingAvailable() {
    KubernetesManifest replicaSet =
        ManifestFetcher.getManifest("replicaset/base.yml", "replicaset/awaiting-available.yml");
    Status status = handler.status(replicaSet);

    assertThat(status.getStable().isState()).isFalse();
    assertThat(status.getStable().getMessage())
        .isEqualTo("Waiting for all replicas to be available");
    assertThat(status.getAvailable().isState()).isFalse();
    assertThat(status.getAvailable().getMessage())
        .isEqualTo("Not all replicas have become available yet");
    assertThat(status.getPaused().isState()).isFalse();
    assertThat(status.getFailed().isState()).isFalse();
  }

  @Test
  void awaitingReady() {
    KubernetesManifest replicaSet =
        ManifestFetcher.getManifest("replicaset/base.yml", "replicaset/awaiting-ready.yml");
    Status status = handler.status(replicaSet);

    assertThat(status.getStable().isState()).isFalse();
    assertThat(status.getStable().getMessage())
        .isEqualTo("Waiting for all replicas to be available");
    assertThat(status.getAvailable().isState()).isFalse();
    assertThat(status.getAvailable().getMessage())
        .isEqualTo("Not all replicas have become available yet");
    assertThat(status.getPaused().isState()).isFalse();
    assertThat(status.getFailed().isState()).isFalse();
  }

  @Test
  void noReplicas() {
    KubernetesManifest replicaSet =
        ManifestFetcher.getManifest("replicaset/base.yml", "replicaset/no-replicas.yml");
    Status status = handler.status(replicaSet);

    assertThat(status.getStable().isState()).isFalse();
    assertThat(status.getStable().getMessage())
        .isEqualTo("Waiting for all replicas to be fully-labeled");
    assertThat(status.getAvailable().isState()).isFalse();
    assertThat(status.getAvailable().getMessage())
        .isEqualTo("Not all replicas have become labeled yet");
    assertThat(status.getPaused().isState()).isFalse();
    assertThat(status.getFailed().isState()).isFalse();
  }

  @Test
  void noReplicasWhenNoneDesired() {
    KubernetesManifest replicaSet =
        ManifestFetcher.getManifest(
            "replicaset/base-no-replicas.yml", "replicaset/no-replicas.yml");
    Status status = handler.status(replicaSet);

    assertThat(status.getStable().isState()).isTrue();
    assertThat(status.getAvailable().isState()).isTrue();
    assertThat(status.getPaused().isState()).isFalse();
    assertThat(status.getFailed().isState()).isFalse();
  }
}
