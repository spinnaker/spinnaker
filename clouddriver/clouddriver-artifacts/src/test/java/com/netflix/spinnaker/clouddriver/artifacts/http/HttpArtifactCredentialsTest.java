/*
 * Copyright 2019 Google, Inc.
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

package com.netflix.spinnaker.clouddriver.artifacts.http;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.MappingBuilder;
import com.netflix.spinnaker.clouddriver.artifacts.config.HttpUrlRestrictions;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Function;
import okhttp3.OkHttpClient;
import org.apache.commons.io.Charsets;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junitpioneer.jupiter.TempDirectory;
import ru.lanwen.wiremock.ext.WiremockResolver;

@ExtendWith({WiremockResolver.class, TempDirectory.class})
class HttpArtifactCredentialsTest {
  private final OkHttpClient okHttpClient = new OkHttpClient();

  private final String URL = "/my/file.yaml";
  private final String FILE_CONTENTS = "file contents";

  @Test
  void downloadWithBasicAuth(@WiremockResolver.Wiremock WireMockServer server) throws IOException {
    HttpArtifactAccount account =
        HttpArtifactAccount.builder()
            .urlRestrictions(HttpUrlRestrictions.builder().rejectLocalhost(false).build())
            .name("my-http-account")
            .username("user")
            .password("passw0rd")
            .build();

    runTestCase(server, account, m -> m.withBasicAuth("user", "passw0rd"));
  }

  @Test
  void downloadWithBasicAuthFromFile(
      @TempDirectory.TempDir Path tempDir, @WiremockResolver.Wiremock WireMockServer server)
      throws IOException {
    Path authFile = tempDir.resolve("auth-file");
    Files.write(authFile, "someuser:somepassw0rd!".getBytes());

    HttpArtifactAccount account =
        HttpArtifactAccount.builder()
            .urlRestrictions(HttpUrlRestrictions.builder().rejectLocalhost(false).build())
            .name("my-http-account")
            .usernamePasswordFile(authFile.toAbsolutePath().toString())
            .build();

    runTestCase(server, account, m -> m.withBasicAuth("someuser", "somepassw0rd!"));
  }

  @Test
  void downloadWithNoAuth(@WiremockResolver.Wiremock WireMockServer server) throws IOException {
    HttpArtifactAccount account =
        HttpArtifactAccount.builder()
            .name("my-http-account")
            .urlRestrictions(HttpUrlRestrictions.builder().rejectLocalhost(false).build())
            .build();

    runTestCase(server, account, m -> m.withHeader("Authorization", absent()));
  }

  @Test
  void throwExceptionOnNonSuccessfulResponse(@WiremockResolver.Wiremock WireMockServer server) {
    HttpArtifactAccount account =
        HttpArtifactAccount.builder()
            .name("my-http-account")
            .urlRestrictions(HttpUrlRestrictions.builder().rejectLocalhost(false).build())
            .build();
    HttpArtifactCredentials credentials = new HttpArtifactCredentials(account, okHttpClient);
    Artifact artifact =
        Artifact.builder().reference(server.baseUrl() + URL).type("http/file").build();
    server.stubFor(any(urlPathEqualTo(URL)).willReturn(aResponse().withStatus(404)));

    Throwable thrown = catchThrowable(() -> credentials.download(artifact));

    assertThat(thrown)
        .isInstanceOf(IOException.class)
        .hasMessageContaining("404")
        .hasMessageContaining(server.baseUrl());
    assertThat(server.findUnmatchedRequests().getRequests()).isEmpty();
  }

  @Test
  void blockUnlessInWhiteList() throws IOException {
    // explicitly deny the test server we're hitting.
    HttpArtifactAccount account =
        HttpArtifactAccount.builder()
            .urlRestrictions(
                HttpUrlRestrictions.builder().allowedDomains(List.of("google.com")).build())
            .name("my-bitbucket-account")
            .build();
    HttpArtifactCredentials credentials = new HttpArtifactCredentials(account, okHttpClient);
    assertThat(credentials.download(Artifact.builder().reference("http://google.com").build()))
        .isNotNull();
    Assertions.assertThrows(
        IllegalArgumentException.class,
        () -> credentials.download(Artifact.builder().reference("http://example.com").build()));
  }

  @Test
  void defaultRestrictLinkLocalAndLocalhost() {
    // explicitly deny the test server we're hitting.
    HttpArtifactAccount account = HttpArtifactAccount.builder().name("my-github-account").build();
    HttpArtifactCredentials credentials = new HttpArtifactCredentials(account, okHttpClient);
    Assertions.assertThrows(
        IllegalArgumentException.class,
        () -> credentials.download(Artifact.builder().reference("http://localhost").build()));
  }

  private void runTestCase(
      WireMockServer server,
      HttpArtifactAccount account,
      Function<MappingBuilder, MappingBuilder> expectedAuth)
      throws IOException {
    HttpArtifactCredentials credentials = new HttpArtifactCredentials(account, okHttpClient);

    Artifact artifact =
        Artifact.builder().reference(server.baseUrl() + URL).type("http/file").build();

    prepareServer(server, expectedAuth);

    assertThat(credentials.download(artifact))
        .hasSameContentAs(new ByteArrayInputStream(FILE_CONTENTS.getBytes(Charsets.UTF_8)));
    assertThat(server.findUnmatchedRequests().getRequests()).isEmpty();
  }

  private void prepareServer(
      WireMockServer server, Function<MappingBuilder, MappingBuilder> withAuth) {
    server.stubFor(
        withAuth.apply(any(urlPathEqualTo(URL)).willReturn(aResponse().withBody(FILE_CONTENTS))));
  }
}
