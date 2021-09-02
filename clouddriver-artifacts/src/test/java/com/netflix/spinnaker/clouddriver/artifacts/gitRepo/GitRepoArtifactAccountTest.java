/*
 * Copyright 2021 Netflix, Inc.
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

package com.netflix.spinnaker.clouddriver.artifacts.gitRepo;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junitpioneer.jupiter.TempDirectory;

@ExtendWith({TempDirectory.class})
public class GitRepoArtifactAccountTest {

  @Test
  void shouldGetTokenFromFile(@TempDirectory.TempDir Path tempDir) throws IOException {
    Path authFile = tempDir.resolve("auth-file");
    Files.write(authFile, "zzz".getBytes());

    GitRepoArtifactAccount account =
        GitRepoArtifactAccount.builder()
            .name("gitRepo-account")
            .tokenFile(authFile.toAbsolutePath().toString())
            .build();

    assertThat(account.getTokenAsString().get()).isEqualTo("zzz");
  }

  @Test
  void shouldGetTokenFromProperty() {
    GitRepoArtifactAccount account =
        GitRepoArtifactAccount.builder().name("gitRepo-account").token("tokentoken").build();

    assertThat(account.getTokenAsString().get()).isEqualTo("tokentoken");
  }
}
