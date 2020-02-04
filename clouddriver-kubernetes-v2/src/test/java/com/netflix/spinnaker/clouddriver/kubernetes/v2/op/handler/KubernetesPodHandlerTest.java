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

final class KubernetesPodHandlerTest {
  private KubernetesPodHandler handler = new KubernetesPodHandler();

  @Test
  void noStatus() {
    KubernetesManifest pod = ManifestFetcher.getManifest("pod/base.yml");
    Status status = handler.status(pod);

    assertThat(status.getStable().isState()).isFalse();
    assertThat(status.getStable().getMessage()).isEqualTo("No status reported yet");
    assertThat(status.getAvailable().isState()).isFalse();
    assertThat(status.getAvailable().getMessage()).isEqualTo("No availability reported");
    assertThat(status.getPaused().isState()).isFalse();
    assertThat(status.getFailed().isState()).isFalse();
  }

  @Test
  void nullPhase() {
    KubernetesManifest pod = ManifestFetcher.getManifest("pod/base.yml", "pod/null-phase.yml");
    Status status = handler.status(pod);

    assertThat(status.getStable().isState()).isFalse();
    assertThat(status.getStable().getMessage()).isEqualTo("No phase reported yet");
    assertThat(status.getAvailable().isState()).isFalse();
    assertThat(status.getAvailable().getMessage()).isEqualTo("No availability reported");
    assertThat(status.getPaused().isState()).isFalse();
    assertThat(status.getFailed().isState()).isFalse();
  }

  @Test
  void pendingPhase() {
    KubernetesManifest pod = ManifestFetcher.getManifest("pod/base.yml", "pod/pending-phase.yml");
    Status status = handler.status(pod);

    // TODO(ezimanyi): Should be unstable; see comment in KubernetesPodHandler.status
    assertThat(status.getStable().isState()).isTrue();
    assertThat(status.getAvailable().isState()).isTrue();
    assertThat(status.getPaused().isState()).isFalse();
    assertThat(status.getFailed().isState()).isFalse();
  }

  @Test
  void runningPhase() {
    KubernetesManifest pod = ManifestFetcher.getManifest("pod/base.yml", "pod/running-phase.yml");
    Status status = handler.status(pod);

    assertThat(status.getStable().isState()).isTrue();
    assertThat(status.getAvailable().isState()).isTrue();
    assertThat(status.getPaused().isState()).isFalse();
    assertThat(status.getFailed().isState()).isFalse();
  }

  @Test
  void succeededPhase() {
    KubernetesManifest pod = ManifestFetcher.getManifest("pod/base.yml", "pod/succeeded-phase.yml");
    Status status = handler.status(pod);

    assertThat(status.getStable().isState()).isTrue();
    assertThat(status.getAvailable().isState()).isTrue();
    assertThat(status.getPaused().isState()).isFalse();
    assertThat(status.getFailed().isState()).isFalse();
  }

  @Test
  void failedPhase() {
    KubernetesManifest pod = ManifestFetcher.getManifest("pod/base.yml", "pod/failed-phase.yml");
    Status status = handler.status(pod);

    // TODO(ezimanyi): Should be unstable; see comment in KubernetesPodHandler.status
    assertThat(status.getStable().isState()).isTrue();
    assertThat(status.getAvailable().isState()).isTrue();
    assertThat(status.getPaused().isState()).isFalse();
    assertThat(status.getFailed().isState()).isFalse();
  }

  @Test
  void unknownPhase() {
    KubernetesManifest pod = ManifestFetcher.getManifest("pod/base.yml", "pod/unknown-phase.yml");
    Status status = handler.status(pod);

    // TODO(ezimanyi): Should be unstable; see comment in KubernetesPodHandler.status
    assertThat(status.getStable().isState()).isTrue();
    assertThat(status.getAvailable().isState()).isTrue();
    assertThat(status.getPaused().isState()).isFalse();
    assertThat(status.getFailed().isState()).isFalse();
  }
}
