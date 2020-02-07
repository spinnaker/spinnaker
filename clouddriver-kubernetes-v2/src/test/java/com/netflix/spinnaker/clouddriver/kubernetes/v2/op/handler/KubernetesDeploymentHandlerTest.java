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

final class KubernetesDeploymentHandlerTest {
  private KubernetesDeploymentHandler handler = new KubernetesDeploymentHandler();

  @Test
  void noStatus() {
    KubernetesManifest deployment = ManifestFetcher.getManifest("deployment/base.yml");

    // Documenting the existing behavior before refactoring
    assertThatExceptionOfType(NullPointerException.class)
        .isThrownBy(() -> handler.status(deployment));
  }

  @Test
  void stable() {
    KubernetesManifest deployment =
        ManifestFetcher.getManifest("deployment/base.yml", "deployment/stable.yml");
    Status status = handler.status(deployment);

    assertThat(status.getStable().isState()).isTrue();
    assertThat(status.getAvailable().isState()).isTrue();
    assertThat(status.getPaused().isState()).isFalse();
    assertThat(status.getFailed().isState()).isFalse();
  }

  @Test
  void noReplicas() {
    KubernetesManifest deployment =
        ManifestFetcher.getManifest("deployment/base.yml", "deployment/no-replicas.yml");
    Status status = handler.status(deployment);

    assertThat(status.getStable().isState()).isFalse();
    assertThat(status.getStable().getMessage()).isEqualTo("Waiting for all replicas to be updated");
    assertThat(status.getAvailable().isState()).isTrue();
    assertThat(status.getPaused().isState()).isFalse();
    assertThat(status.getFailed().isState()).isFalse();
  }

  @Test
  void noReplicasWhenNoneDesired() {
    KubernetesManifest deployment =
        ManifestFetcher.getManifest(
            "deployment/base-no-replicas.yml", "deployment/no-replicas.yml");
    Status status = handler.status(deployment);

    assertThat(status.getStable().isState()).isTrue();
    assertThat(status.getAvailable().isState()).isTrue();
    assertThat(status.getPaused().isState()).isFalse();
    assertThat(status.getFailed().isState()).isFalse();
  }

  @Test
  void unknownWithOldGeneration() {
    KubernetesManifest deployment =
        ManifestFetcher.getManifest(
            "deployment/base.yml", "deployment/stable-with-old-generation.yml");
    Status status = handler.status(deployment);

    assertThat(status.getStable()).isNull();
    assertThat(status.getAvailable().isState()).isTrue();
    assertThat(status.getPaused().isState()).isFalse();
    assertThat(status.getFailed()).isNull();
  }

  @Test
  void conditionReportsUnavailable() {
    KubernetesManifest deployment =
        ManifestFetcher.getManifest("deployment/base.yml", "deployment/condition-unavailable.yml");
    Status status = handler.status(deployment);

    assertThat(status.getStable().isState()).isFalse();
    assertThat(status.getStable().getMessage())
        .isEqualTo("Waiting for all replicas to be available");
    assertThat(status.getAvailable().isState()).isFalse();
    assertThat(status.getAvailable().getMessage())
        .isEqualTo("Deployment does not have minimum availability.");
    assertThat(status.getPaused().isState()).isFalse();
    assertThat(status.getFailed().isState()).isFalse();
  }

  @Test
  void progressDeadlineExceededUnavailable() {
    KubernetesManifest deployment =
        ManifestFetcher.getManifest(
            "deployment/base.yml", "deployment/progress-deadline-exceeded-unavailable.yml");
    Status status = handler.status(deployment);

    assertThat(status.getStable().isState()).isFalse();
    assertThat(status.getStable().getMessage())
        .isEqualTo("Waiting for all replicas to be available");
    assertThat(status.getAvailable().isState()).isFalse();
    assertThat(status.getAvailable().getMessage())
        .isEqualTo("Deployment does not have minimum availability.");
    assertThat(status.getPaused().isState()).isFalse();
    assertThat(status.getFailed().isState()).isTrue();
    assertThat(status.getFailed().getMessage())
        .isEqualTo("Deployment exceeded its progress deadline");
  }

  @Test
  void progressDeadlineExceeded() {
    KubernetesManifest deployment =
        ManifestFetcher.getManifest(
            "deployment/base.yml", "deployment/progress-deadline-exceeded.yml");
    Status status = handler.status(deployment);

    assertThat(status.getStable().isState()).isFalse();
    assertThat(status.getStable().getMessage())
        .isEqualTo("Waiting for all replicas to be available");
    assertThat(status.getAvailable().isState()).isTrue();
    assertThat(status.getPaused().isState()).isFalse();
    assertThat(status.getFailed().isState()).isTrue();
    assertThat(status.getFailed().getMessage())
        .isEqualTo("Deployment exceeded its progress deadline");
  }

  @Test
  void awaitingUpdatedReplicas() {
    KubernetesManifest deployment =
        ManifestFetcher.getManifest(
            "deployment/base.yml", "deployment/awaiting-updated-replicas.yml");
    Status status = handler.status(deployment);

    assertThat(status.getStable().isState()).isFalse();
    assertThat(status.getStable().getMessage()).isEqualTo("Waiting for all replicas to be updated");
    assertThat(status.getAvailable().isState()).isTrue();
    assertThat(status.getPaused().isState()).isFalse();
    assertThat(status.getFailed().isState()).isFalse();
  }

  @Test
  void awaitingUpdatedReplicasPaused() {
    KubernetesManifest deployment =
        ManifestFetcher.getManifest(
            "deployment/base.yml", "deployment/awaiting-updated-replicas-paused.yml");
    Status status = handler.status(deployment);

    assertThat(status.getStable().isState()).isFalse();
    assertThat(status.getStable().getMessage()).isEqualTo("Waiting for all replicas to be updated");
    assertThat(status.getAvailable().isState()).isTrue();
    assertThat(status.getPaused().isState()).isTrue();
    assertThat(status.getPaused().getMessage()).isEqualTo("Deployment is paused");
    assertThat(status.getFailed().isState()).isFalse();
  }

  @Test
  void awaitingTermination() {
    KubernetesManifest deployment =
        ManifestFetcher.getManifest("deployment/base.yml", "deployment/awaiting-termination.yml");
    Status status = handler.status(deployment);

    assertThat(status.getStable().isState()).isFalse();
    assertThat(status.getStable().getMessage())
        .isEqualTo("Waiting for old replicas to finish termination");
    assertThat(status.getAvailable().isState()).isTrue();
    assertThat(status.getPaused().isState()).isFalse();
    assertThat(status.getFailed().isState()).isFalse();
  }

  @Test
  void awaitingTerminationPaused() {
    KubernetesManifest deployment =
        ManifestFetcher.getManifest(
            "deployment/base.yml", "deployment/awaiting-termination-paused.yml");
    Status status = handler.status(deployment);

    assertThat(status.getStable().isState()).isFalse();
    assertThat(status.getStable().getMessage())
        .isEqualTo("Waiting for old replicas to finish termination");
    assertThat(status.getAvailable().isState()).isTrue();
    assertThat(status.getPaused().isState()).isTrue();
    assertThat(status.getPaused().getMessage()).isEqualTo("Deployment is paused");
    assertThat(status.getFailed().isState()).isFalse();
  }

  @Test
  void awaitingAvailableReplicas() {
    KubernetesManifest deployment =
        ManifestFetcher.getManifest(
            "deployment/base.yml", "deployment/awaiting-available-replicas.yml");
    Status status = handler.status(deployment);

    assertThat(status.getStable().isState()).isFalse();
    assertThat(status.getStable().getMessage())
        .isEqualTo("Waiting for all replicas to be available");
    assertThat(status.getAvailable().isState()).isTrue();
    assertThat(status.getPaused().isState()).isFalse();
    assertThat(status.getFailed().isState()).isFalse();
  }

  @Test
  void awaitingAvailableReplicasPaused() {
    KubernetesManifest deployment =
        ManifestFetcher.getManifest(
            "deployment/base.yml", "deployment/awaiting-available-replicas-paused.yml");
    Status status = handler.status(deployment);

    assertThat(status.getStable().isState()).isFalse();
    assertThat(status.getStable().getMessage())
        .isEqualTo("Waiting for all replicas to be available");
    assertThat(status.getAvailable().isState()).isTrue();
    assertThat(status.getPaused().isState()).isTrue();
    assertThat(status.getPaused().getMessage()).isEqualTo("Deployment is paused");
    assertThat(status.getFailed().isState()).isFalse();
  }

  @Test
  void awaitingReadyReplicas() {
    KubernetesManifest deployment =
        ManifestFetcher.getManifest(
            "deployment/base.yml", "deployment/awaiting-ready-replicas.yml");
    Status status = handler.status(deployment);

    assertThat(status.getStable().isState()).isFalse();
    assertThat(status.getStable().getMessage()).isEqualTo("Waiting for all replicas to be ready");
    assertThat(status.getAvailable().isState()).isTrue();
    assertThat(status.getPaused().isState()).isFalse();
    assertThat(status.getFailed().isState()).isFalse();
  }

  @Test
  void awaitingReadyReplicasPaused() {
    KubernetesManifest deployment =
        ManifestFetcher.getManifest(
            "deployment/base.yml", "deployment/awaiting-ready-replicas-paused.yml");
    Status status = handler.status(deployment);

    assertThat(status.getStable().isState()).isFalse();
    assertThat(status.getStable().getMessage()).isEqualTo("Waiting for all replicas to be ready");
    assertThat(status.getAvailable().isState()).isTrue();
    assertThat(status.getPaused().isState()).isTrue();
    assertThat(status.getPaused().getMessage()).isEqualTo("Deployment is paused");
    assertThat(status.getFailed().isState()).isFalse();
  }
}
