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
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesManifest;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.model.Manifest.Status;
import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;

@RunWith(JUnitPlatform.class)
final class KubernetesStatefulSetHandlerTest {
  private KubernetesStatefulSetHandler handler = new KubernetesStatefulSetHandler();

  @Test
  void noStatus() {
    KubernetesManifest statefulSet = ManifestFetcher.getManifest("statefulset/base.yml");
    // Documenting the existing behavior before refactoring
    assertThatExceptionOfType(NullPointerException.class)
        .isThrownBy(() -> handler.status(statefulSet));
  }

  @Test
  void oldGeneration() {
    KubernetesManifest statefulSet =
        ManifestFetcher.getManifest("statefulset/base.yml", "statefulset/old-generation.yml");
    Status status = handler.status(statefulSet);

    assertThat(status.getStable()).isNull();
    assertThat(status.getAvailable().isState()).isTrue();
    assertThat(status.getPaused().isState()).isFalse();
    assertThat(status.getFailed()).isNull();
  }

  @Test
  void awaitingReplicas() {
    KubernetesManifest statefulSet =
        ManifestFetcher.getManifest("statefulset/base.yml", "statefulset/awaiting-replicas.yml");
    Status status = handler.status(statefulSet);

    assertThat(status.getStable().isState()).isFalse();
    assertThat(status.getStable().getMessage())
        .isEqualTo("Waiting for at least the desired replica count to be met");
    assertThat(status.getAvailable().isState()).isTrue();
    assertThat(status.getPaused().isState()).isFalse();
    assertThat(status.getFailed().isState()).isFalse();
  }

  @Test
  void awaitingReadyReplicas() {
    KubernetesManifest statefulSet =
        ManifestFetcher.getManifest(
            "statefulset/base.yml", "statefulset/awaiting-ready-replicas.yml");
    Status status = handler.status(statefulSet);

    assertThat(status.getStable().isState()).isFalse();
    assertThat(status.getStable().getMessage())
        .isEqualTo("Waiting for all updated replicas to be ready");
    assertThat(status.getAvailable().isState()).isTrue();
    assertThat(status.getPaused().isState()).isFalse();
    assertThat(status.getFailed().isState()).isFalse();
  }

  @Test
  void awaitingPartitionedRollout() {
    KubernetesManifest statefulSet =
        ManifestFetcher.getManifest(
            "statefulset/base-with-partition.yml", "statefulset/awaiting-partitioned-rollout.yml");
    Status status = handler.status(statefulSet);

    assertThat(status.getStable().isState()).isFalse();
    assertThat(status.getStable().getMessage())
        .isEqualTo("Waiting for partitioned roll out to finish");
    assertThat(status.getAvailable().isState()).isTrue();
    assertThat(status.getPaused().isState()).isFalse();
    assertThat(status.getFailed().isState()).isFalse();
  }

  @Test
  void partitionedRolloutComplete() {
    KubernetesManifest statefulSet =
        ManifestFetcher.getManifest(
            "statefulset/base-with-partition.yml", "statefulset/partitioned-rollout-complete.yml");
    Status status = handler.status(statefulSet);

    assertThat(status.getStable().isState()).isTrue();
    assertThat(status.getStable().getMessage()).isEqualTo("Partitioned roll out complete");
    assertThat(status.getAvailable().isState()).isTrue();
    assertThat(status.getPaused().isState()).isFalse();
    assertThat(status.getFailed().isState()).isFalse();
  }

  @Test
  void waitingForReplicas() {
    KubernetesManifest statefulSet =
        ManifestFetcher.getManifest("statefulset/base.yml", "statefulset/waiting-for-replicas.yml");
    Status status = handler.status(statefulSet);

    assertThat(status.getStable().isState()).isFalse();
    assertThat(status.getStable().getMessage())
        .isEqualTo("Waiting for all updated replicas to be scheduled");
    assertThat(status.getAvailable().isState()).isTrue();
    assertThat(status.getPaused().isState()).isFalse();
    assertThat(status.getFailed().isState()).isFalse();
  }

  @Test
  void waitingForUpdatedRevision() {
    KubernetesManifest statefulSet =
        ManifestFetcher.getManifest(
            "statefulset/base.yml", "statefulset/waiting-for-updated-revision.yml");
    Status status = handler.status(statefulSet);

    assertThat(status.getStable().isState()).isFalse();
    assertThat(status.getStable().getMessage())
        .isEqualTo("Waiting for the updated revision to match the current revision");
    assertThat(status.getAvailable().isState()).isTrue();
    assertThat(status.getPaused().isState()).isFalse();
    assertThat(status.getFailed().isState()).isFalse();
  }

  @Test
  void noReplicas() {
    KubernetesManifest statefulSet =
        ManifestFetcher.getManifest("statefulset/base.yml", "statefulset/no-replicas.yml");
    Status status = handler.status(statefulSet);

    assertThat(status.getStable().isState()).isFalse();
    assertThat(status.getStable().getMessage())
        .isEqualTo("Waiting for at least the desired replica count to be met");
    assertThat(status.getAvailable().isState()).isTrue();
    assertThat(status.getPaused().isState()).isFalse();
    assertThat(status.getFailed().isState()).isFalse();
  }

  @Test
  void noReplicasWhenNoneDesired() {
    KubernetesManifest statefulSet =
        ManifestFetcher.getManifest(
            "statefulset/base-no-desired-replicas.yml", "statefulset/no-replicas.yml");
    Status status = handler.status(statefulSet);

    assertThat(status.getStable().isState()).isTrue();
    assertThat(status.getAvailable().isState()).isTrue();
    assertThat(status.getPaused().isState()).isFalse();
    assertThat(status.getFailed().isState()).isFalse();
  }

  @Test
  void stable() {
    KubernetesManifest statefulSet =
        ManifestFetcher.getManifest("statefulset/base.yml", "statefulset/stable.yml");
    Status status = handler.status(statefulSet);

    assertThat(status.getStable().isState()).isTrue();
    assertThat(status.getAvailable().isState()).isTrue();
    assertThat(status.getPaused().isState()).isFalse();
    assertThat(status.getFailed().isState()).isFalse();
  }
}
