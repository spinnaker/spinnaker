/*
 * Copyright 2026 DoorDash, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.security.s2s.client;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ProjectedServiceAccountTokenSourceTest {

  @TempDir Path tempDir;

  @Test
  @DisplayName("reads the token and strips the trailing newline kubelet writes")
  void readsToken() throws IOException {
    Path token = tempDir.resolve("token");
    Files.writeString(token, "header.payload.signature\n");

    ProjectedServiceAccountTokenSource source =
        new ProjectedServiceAccountTokenSource(token, Duration.ofMinutes(1));

    assertThat(source.get()).contains("header.payload.signature");
  }

  @Test
  @DisplayName("a missing volume yields no token instead of throwing")
  void missingFileIsNotFatal() {
    ProjectedServiceAccountTokenSource source =
        new ProjectedServiceAccountTokenSource(tempDir.resolve("absent"), Duration.ofMinutes(1));

    assertThat(source.get()).isEmpty();
  }

  @Test
  @DisplayName("an empty file yields no token rather than an empty header value")
  void emptyFileYieldsNoToken() throws IOException {
    Path token = tempDir.resolve("token");
    Files.writeString(token, "   \n");

    ProjectedServiceAccountTokenSource source =
        new ProjectedServiceAccountTokenSource(token, Duration.ofMinutes(1));

    assertThat(source.get()).isEmpty();
  }

  @Test
  @DisplayName("caches within the refresh interval so every request is not a file read")
  void cachesWithinInterval() throws IOException {
    Path token = tempDir.resolve("token");
    Files.writeString(token, "first");

    ProjectedServiceAccountTokenSource source =
        new ProjectedServiceAccountTokenSource(token, Duration.ofMinutes(10));
    assertThat(source.get()).contains("first");

    Files.writeString(token, "second");

    assertThat(source.get()).contains("first");
  }

  @Test
  @DisplayName("picks up a rotated token once the refresh interval has elapsed")
  void rereadsAfterInterval() throws IOException {
    Path token = tempDir.resolve("token");
    Files.writeString(token, "first");

    // Zero interval makes every call expired, standing in for the wall-clock passage of the
    // configured TTL without sleeping.
    ProjectedServiceAccountTokenSource source =
        new ProjectedServiceAccountTokenSource(token, Duration.ZERO);
    assertThat(source.get()).contains("first");

    Files.writeString(token, "second");

    assertThat(source.get()).contains("second");
  }

  @Test
  @DisplayName("a disabled source never reads a file and never yields a token")
  void disabledYieldsNothing() {
    assertThat(ProjectedServiceAccountTokenSource.disabled().get()).isEmpty();
  }
}
