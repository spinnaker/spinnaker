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
final class KubernetesDaemonSetHandlerTest {
  private KubernetesDaemonSetHandler handler = new KubernetesDaemonSetHandler();

  @Test
  void noStatus() {
    KubernetesManifest daemonSet = ManifestFetcher.getManifest("daemonset/base.yml");
    // Documenting the existing behavior before refactoring
    assertThatExceptionOfType(NullPointerException.class)
        .isThrownBy(() -> handler.status(daemonSet));
  }

  @Test
  void unstableWhenUnavailable() {
    KubernetesManifest daemonSet =
        ManifestFetcher.getManifest("daemonset/base.yml", "daemonset/unavailable.yml");
    Status status = handler.status(daemonSet);

    assertThat(status.getStable().isState()).isFalse();
    assertThat(status.getStable().getMessage())
        .isEqualTo("Waiting for all replicas to be available");
    assertThat(status.getAvailable().isState()).isTrue();
    assertThat(status.getPaused().isState()).isFalse();
    assertThat(status.getFailed().isState()).isFalse();
  }

  @Test
  void awaitingScheduled() {
    KubernetesManifest daemonSet =
        ManifestFetcher.getManifest("daemonset/base.yml", "daemonset/awaiting-scheduled.yml");
    Status status = handler.status(daemonSet);

    assertThat(status.getStable().isState()).isFalse();
    assertThat(status.getStable().getMessage())
        .isEqualTo("Waiting for all replicas to be scheduled");
    assertThat(status.getAvailable().isState()).isTrue();
    assertThat(status.getPaused().isState()).isFalse();
    assertThat(status.getFailed().isState()).isFalse();
  }

  @Test
  void awaitingUpdatedScheduled() {
    KubernetesManifest daemonSet =
        ManifestFetcher.getManifest(
            "daemonset/base.yml", "daemonset/awaiting-updated-scheduled.yml");
    Status status = handler.status(daemonSet);

    assertThat(status.getStable().isState()).isFalse();
    assertThat(status.getStable().getMessage())
        .isEqualTo("Waiting for all updated replicas to be scheduled");
    assertThat(status.getAvailable().isState()).isTrue();
    assertThat(status.getPaused().isState()).isFalse();
    assertThat(status.getFailed().isState()).isFalse();
  }

  @Test
  void awaitingReady() {
    KubernetesManifest daemonSet =
        ManifestFetcher.getManifest("daemonset/base.yml", "daemonset/awaiting-ready.yml");
    Status status = handler.status(daemonSet);

    assertThat(status.getStable().isState()).isFalse();
    assertThat(status.getStable().getMessage()).isEqualTo("Waiting for all replicas to be ready");
    assertThat(status.getAvailable().isState()).isTrue();
    assertThat(status.getPaused().isState()).isFalse();
    assertThat(status.getFailed().isState()).isFalse();
  }

  @Test
  void oldGeneration() {
    KubernetesManifest daemonSet =
        ManifestFetcher.getManifest("daemonset/base.yml", "daemonset/old-generation.yml");
    Status status = handler.status(daemonSet);

    assertThat(status.getStable()).isNull();
    assertThat(status.getAvailable().isState()).isTrue();
    assertThat(status.getPaused().isState()).isFalse();
    assertThat(status.getFailed()).isNull();
  }

  @Test
  void stableWhenAllAvailable() {
    KubernetesManifest daemonSet =
        ManifestFetcher.getManifest("daemonset/base.yml", "daemonset/available.yml");
    Status status = handler.status(daemonSet);

    assertThat(status.getStable().isState()).isTrue();
    assertThat(status.getAvailable().isState()).isTrue();
    assertThat(status.getPaused().isState()).isFalse();
    assertThat(status.getFailed().isState()).isFalse();
  }

  @Test
  void stableWhenNoneDesired() {
    KubernetesManifest daemonSet =
        ManifestFetcher.getManifest("daemonset/base.yml", "daemonset/none-desired.yml");
    Status status = handler.status(daemonSet);

    assertThat(status.getStable().isState()).isTrue();
    assertThat(status.getAvailable().isState()).isTrue();
    assertThat(status.getPaused().isState()).isFalse();
    assertThat(status.getFailed().isState()).isFalse();
  }

  @Test
  void stableWhenOnDelete() {
    KubernetesManifest daemonSet =
        ManifestFetcher.getManifest("daemonset/base-on-delete.yml", "daemonset/unavailable.yml");
    Status status = handler.status(daemonSet);

    assertThat(status.getStable().isState()).isTrue();
    assertThat(status.getAvailable().isState()).isTrue();
    assertThat(status.getPaused().isState()).isFalse();
    assertThat(status.getFailed().isState()).isFalse();
  }
}
