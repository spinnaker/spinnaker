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

import static com.netflix.spinnaker.rosco.manifests.ManifestTestUtils.makeSpinnakerHttpException;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import com.netflix.spinnaker.kork.exceptions.SpinnakerException;
import com.netflix.spinnaker.kork.retrofit.exceptions.SpinnakerHttpException;
import com.netflix.spinnaker.kork.retrofit.exceptions.SpinnakerNetworkException;
import com.netflix.spinnaker.rosco.services.ClouddriverService;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import okhttp3.Request;
import okhttp3.ResponseBody;
import org.junit.jupiter.api.Test;
import retrofit2.Call;
import retrofit2.Response;

final class ArtifactDownloaderImplTest {
  private final ClouddriverService clouddriverService = mock(ClouddriverService.class);
  private final Call mockCall = mock(Call.class);
  private static final Artifact testArtifact =
      Artifact.builder().name("test-artifact").version("3").build();

  @Test
  public void downloadsArtifactContent() throws IOException {
    ArtifactDownloaderImpl artifactDownloader = new ArtifactDownloaderImpl(clouddriverService);
    String testContent = "abcdefg";

    try (ArtifactDownloaderImplTest.AutoDeletingFile file = new AutoDeletingFile()) {
      when(clouddriverService.fetchArtifact(testArtifact)).thenReturn(mockCall);
      when(mockCall.execute()).thenReturn(successfulResponse(testContent));
      artifactDownloader.downloadArtifactToFile(testArtifact, file.path);

      assertThat(file.path).hasContent(testContent);
    }
  }

  @Test
  public void retries() throws IOException {
    ArtifactDownloaderImpl artifactDownloader = new ArtifactDownloaderImpl(clouddriverService);
    Request request = new Request.Builder().url("http://some-url").build();
    String testContent = "abcdefg";
    try (ArtifactDownloaderImplTest.AutoDeletingFile file = new AutoDeletingFile()) {
      when(clouddriverService.fetchArtifact(testArtifact)).thenReturn(mockCall);
      when(mockCall.execute())
          .thenThrow(new SpinnakerNetworkException(new IOException("timeout"), request))
          .thenReturn(successfulResponse(testContent));
      artifactDownloader.downloadArtifactToFile(testArtifact, file.path);

      assertThat(file.path).hasContent(testContent);
    }
  }

  @Test
  public void exceptionDownloadingArtifactContent() throws IOException {
    ArtifactDownloaderImpl artifactDownloader = new ArtifactDownloaderImpl(clouddriverService);
    SpinnakerException spinnakerException = new SpinnakerException("error from clouddriver");
    try (ArtifactDownloaderImplTest.AutoDeletingFile file = new AutoDeletingFile()) {
      when(clouddriverService.fetchArtifact(testArtifact)).thenReturn(mockCall);
      when(mockCall.execute()).thenThrow(spinnakerException);
      SpinnakerException thrown =
          assertThrows(
              SpinnakerException.class,
              () -> artifactDownloader.downloadArtifactToFile(testArtifact, file.path));

      // Make sure we have the message we expect, and that we wrapped the
      // underlying exception to not lose any info.
      assertThat(thrown.getMessage()).contains("Failed to download artifact");
      assertThat(thrown.getCause()).isEqualTo(spinnakerException);
    }
  }

  @Test
  public void artifactNotFound() throws IOException {
    // When clouddriver responds with a 404, ErrorHandlingExecutorCallAdapterFactory
    // generates a SpinnakerHttpException, so test that case.
    ArtifactDownloaderImpl artifactDownloader = new ArtifactDownloaderImpl(clouddriverService);
    SpinnakerHttpException spinnakerHttpException = makeSpinnakerHttpException(404);
    try (ArtifactDownloaderImplTest.AutoDeletingFile file = new AutoDeletingFile()) {
      when(clouddriverService.fetchArtifact(testArtifact)).thenReturn(mockCall);
      when(mockCall.execute()).thenThrow(spinnakerHttpException);

      // Make sure that the exception from artifactDownloader is also a
      // SpinnakerHttpException with 404 status since that'll make rosco
      // eventually respond with a 404 itself, and not log an error/stack trace,
      // as rosco hasn't done anything wrong.  For this to all work, the code
      // that calls ArtifactDownloader (e.g. HelmTemplateUtils) has to handle
      // SpinnakerHttpException properly, but at least this gives a chance for
      // success.
      SpinnakerHttpException thrown =
          assertThrows(
              SpinnakerHttpException.class,
              () -> artifactDownloader.downloadArtifactToFile(testArtifact, file.path));

      // Make sure we have the message we expect, and that we wrapped the
      // underlying exception to not lose any info.
      assertThat(thrown.getMessage()).contains("Failed to download artifact");
      assertThat(thrown.getResponseCode()).isEqualTo(404);
      assertThat(thrown.getCause()).isEqualTo(spinnakerHttpException);
    }
  }

  private Response successfulResponse(String content) {
    return Response.success(200, ResponseBody.create(null, content.getBytes()));
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
