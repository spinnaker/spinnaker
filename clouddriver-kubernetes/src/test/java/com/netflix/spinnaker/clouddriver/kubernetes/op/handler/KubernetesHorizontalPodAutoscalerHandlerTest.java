/*
 * Copyright 2020 Snap Inc
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
 *
 */

package com.netflix.spinnaker.clouddriver.kubernetes.op.handler;

import static org.assertj.core.api.Assertions.assertThat;

import com.netflix.spinnaker.clouddriver.kubernetes.description.manifest.KubernetesManifest;
import com.netflix.spinnaker.clouddriver.kubernetes.model.Manifest.Status;
import org.junit.jupiter.api.Test;

final class KubernetesHorizontalPodAutoscalerHandlerTest {
  private KubernetesHorizontalPodAutoscalerHandler handler =
      new KubernetesHorizontalPodAutoscalerHandler();

  @Test
  void noStatus() {
    KubernetesManifest hpa = ManifestFetcher.getManifest("horizontalpodautoscaler/base.yml");
    Status status = handler.status(hpa);

    assertThat(status.getStable().isState()).isFalse();
    assertThat(status.getStable().getMessage()).isEqualTo("No status reported yet");
    assertThat(status.getAvailable().isState()).isFalse();
    assertThat(status.getPaused().isState()).isFalse();
    assertThat(status.getFailed().isState()).isFalse();
  }

  @Test
  void waitingForScaleup() {
    KubernetesManifest hpa =
        ManifestFetcher.getManifest(
            "horizontalpodautoscaler/base.yml", "horizontalpodautoscaler/waiting-for-scaleup.yml");
    Status status = handler.status(hpa);

    assertThat(status.getStable().isState()).isFalse();
    assertThat(status.getStable().getMessage())
        .isEqualTo("Waiting for HPA to complete a scale up, current: 2 desired: 3");
    assertThat(status.getAvailable().isState()).isTrue();
    assertThat(status.getPaused().isState()).isFalse();
    assertThat(status.getFailed().isState()).isFalse();
  }

  @Test
  void waitingForScaledown() {
    KubernetesManifest hpa =
        ManifestFetcher.getManifest(
            "horizontalpodautoscaler/base.yml",
            "horizontalpodautoscaler/waiting-for-scaledown.yml");
    Status status = handler.status(hpa);

    assertThat(status.getStable().isState()).isFalse();
    assertThat(status.getStable().getMessage())
        .isEqualTo("Waiting for HPA to complete a scale down, current: 5 desired: 2");
    assertThat(status.getAvailable().isState()).isTrue();
    assertThat(status.getPaused().isState()).isFalse();
    assertThat(status.getFailed().isState()).isFalse();
  }

  @Test
  void noReplicas() {
    KubernetesManifest hpa =
        ManifestFetcher.getManifest(
            "horizontalpodautoscaler/base.yml", "horizontalpodautoscaler/no-replicas.yml");
    Status status = handler.status(hpa);

    assertThat(status.getStable().isState()).isTrue();
    assertThat(status.getAvailable().isState()).isTrue();
    assertThat(status.getPaused().isState()).isFalse();
    assertThat(status.getFailed().isState()).isFalse();
  }

  @Test
  void stable() {
    KubernetesManifest hpa =
        ManifestFetcher.getManifest(
            "horizontalpodautoscaler/base.yml", "horizontalpodautoscaler/stable.yml");
    Status status = handler.status(hpa);

    assertThat(status.getStable().isState()).isTrue();
    assertThat(status.getAvailable().isState()).isTrue();
    assertThat(status.getPaused().isState()).isFalse();
    assertThat(status.getFailed().isState()).isFalse();
  }
}
