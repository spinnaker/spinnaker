/*
 * Copyright 2019 Google, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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

package com.netflix.spinnaker.rosco.manifests;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.jupiter.api.Test;

final class BakeManifestEnvironmentTest {
  @Test
  void rejectsInvalidPaths() throws IOException {
    try (BakeManifestEnvironment bakeManifestEnvironment = BakeManifestEnvironment.create()) {

      Path result = bakeManifestEnvironment.resolvePath(Paths.get("abc"));
      assertThat(result.endsWith("abc")).isTrue();

      Throwable thrown;
      thrown = catchThrowable(() -> bakeManifestEnvironment.resolvePath(Paths.get("..")));
      assertThat(thrown).isInstanceOf(Exception.class);

      thrown =
          catchThrowable(() -> bakeManifestEnvironment.resolvePath(Paths.get("../../etc/passwd")));
      assertThat(thrown).isInstanceOf(Exception.class);
    }
  }

  @Test
  void rejectsInvalidStringPaths() throws IOException {
    try (BakeManifestEnvironment bakeManifestEnvironment = BakeManifestEnvironment.create()) {

      Path result = bakeManifestEnvironment.resolvePath("abc");
      assertThat(result.endsWith("abc")).isTrue();

      Throwable thrown;
      thrown = catchThrowable(() -> bakeManifestEnvironment.resolvePath(".."));
      assertThat(thrown).isInstanceOf(Exception.class);

      thrown = catchThrowable(() -> bakeManifestEnvironment.resolvePath("../../etc/passwd"));
      assertThat(thrown).isInstanceOf(Exception.class);
    }
  }
}
