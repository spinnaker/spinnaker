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

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import com.netflix.spinnaker.rosco.services.ClouddriverService;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;
import org.springframework.http.HttpStatus;
import retrofit.RetrofitError;
import retrofit.client.Response;
import retrofit.mime.TypedByteArray;

@RunWith(JUnitPlatform.class)
final class ArtifactDownloaderImplTest {
  private static final ClouddriverService clouddriverService = mock(ClouddriverService.class);
  private static final Artifact testArtifact =
      Artifact.builder().name("test-artifact").version("3").build();

  @Test
  public void downloadsArtifactContent() throws IOException {
    ArtifactDownloaderImpl artifactDownloader = new ArtifactDownloaderImpl(clouddriverService);
    String testContent = "abcdefg";

    try (ArtifactDownloaderImplTest.AutoDeletingFile file = new AutoDeletingFile()) {
      when(clouddriverService.fetchArtifact(testArtifact))
          .thenReturn(successfulResponse(testContent));
      artifactDownloader.downloadArtifactToFile(testArtifact, file.path);

      Assertions.assertThat(file.path).hasContent(testContent);
    }
  }

  @Test
  public void retries() throws IOException {
    ArtifactDownloaderImpl artifactDownloader = new ArtifactDownloaderImpl(clouddriverService);
    String testContent = "abcdefg";

    try (ArtifactDownloaderImplTest.AutoDeletingFile file = new AutoDeletingFile()) {
      when(clouddriverService.fetchArtifact(testArtifact))
          .thenThrow(RetrofitError.networkError("", new IOException("timeout")))
          .thenReturn(successfulResponse(testContent));
      artifactDownloader.downloadArtifactToFile(testArtifact, file.path);

      Assertions.assertThat(file.path).hasContent(testContent);
    }
  }

  private Response successfulResponse(String content) {
    return new Response(
        "",
        HttpStatus.OK.value(),
        "",
        ImmutableList.of(),
        new TypedByteArray(null, content.getBytes()));
  }

  private static class AutoDeletingFile implements AutoCloseable {
    final Path path;

    AutoDeletingFile() throws IOException {
      this.path = Files.createTempFile("artifact-test", "");
    }

    @Override
    public void close() throws IOException {
      Files.delete(path);
    }
  }
}
