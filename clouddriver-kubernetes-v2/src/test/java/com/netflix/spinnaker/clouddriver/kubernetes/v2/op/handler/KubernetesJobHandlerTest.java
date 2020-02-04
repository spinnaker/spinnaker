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

final class KubernetesJobHandlerTest {
  private KubernetesJobHandler handler = new KubernetesJobHandler();

  @Test
  void noStatus() {
    KubernetesManifest job = ManifestFetcher.getManifest("job/base.yml");
    Status status = handler.status(job);

    assertThat(status.getStable().isState()).isFalse();
    assertThat(status.getStable().getMessage()).isEqualTo("No status reported yet");
    assertThat(status.getAvailable().isState()).isFalse();
    assertThat(status.getAvailable().getMessage()).isEqualTo("No availability reported");
    assertThat(status.getPaused().isState()).isFalse();
    assertThat(status.getFailed().isState()).isFalse();
  }

  @Test
  void activeJob() {
    KubernetesManifest job = ManifestFetcher.getManifest("job/base.yml", "job/active-job.yml");
    Status status = handler.status(job);

    assertThat(status.getStable().isState()).isFalse();
    assertThat(status.getStable().getMessage()).isEqualTo("Waiting for jobs to finish");
    assertThat(status.getAvailable().isState()).isTrue();
    assertThat(status.getPaused().isState()).isFalse();
    assertThat(status.getFailed().isState()).isFalse();
  }

  @Test
  void completedJob() {
    KubernetesManifest job = ManifestFetcher.getManifest("job/base.yml", "job/completed-job.yml");
    Status status = handler.status(job);

    assertThat(status.getStable().isState()).isTrue();
    assertThat(status.getAvailable().isState()).isTrue();
    assertThat(status.getPaused().isState()).isFalse();
    assertThat(status.getFailed().isState()).isFalse();
  }

  @Test
  void multipleCompletionsInProgress() {
    KubernetesManifest job =
        ManifestFetcher.getManifest("job/base-with-completions.yml", "job/active-job.yml");
    Status status = handler.status(job);

    assertThat(status.getStable().isState()).isFalse();
    assertThat(status.getStable().getMessage()).isEqualTo("Waiting for jobs to finish");
    assertThat(status.getAvailable().isState()).isTrue();
    assertThat(status.getPaused().isState()).isFalse();
    assertThat(status.getFailed().isState()).isFalse();
  }

  @Test
  void multipleCompletionsSomeCompleted() {
    KubernetesManifest job =
        ManifestFetcher.getManifest("job/base-with-completions.yml", "job/completed-job.yml");
    Status status = handler.status(job);

    assertThat(status.getStable().isState()).isFalse();
    assertThat(status.getStable().getMessage()).isEqualTo("Waiting for jobs to finish");
    assertThat(status.getAvailable().isState()).isTrue();
    assertThat(status.getPaused().isState()).isFalse();
    assertThat(status.getFailed().isState()).isFalse();
  }

  @Test
  void multipleCompletionsSomeFailed() {
    KubernetesManifest job =
        ManifestFetcher.getManifest(
            "job/base-with-completions.yml", "job/in-progress-some-failed.yml");
    Status status = handler.status(job);

    assertThat(status.getStable().isState()).isFalse();
    assertThat(status.getStable().getMessage()).isEqualTo("Waiting for jobs to finish");
    assertThat(status.getAvailable().isState()).isTrue();
    assertThat(status.getPaused().isState()).isFalse();
    assertThat(status.getFailed().isState()).isFalse();
  }

  @Test
  void multipleCompletionsFailed() {
    KubernetesManifest job =
        ManifestFetcher.getManifest("job/base-with-completions.yml", "job/failed-job.yml");
    Status status = handler.status(job);

    assertThat(status.getStable().isState()).isTrue();
    assertThat(status.getAvailable().isState()).isTrue();
    assertThat(status.getPaused().isState()).isFalse();
    assertThat(status.getFailed().isState()).isTrue();
    assertThat(status.getFailed().getMessage())
        .isEqualTo("Job has reached the specified backoff limit");
  }
}
