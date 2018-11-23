/*
 * Copyright 2018 Pivotal, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.artifacts.ivy;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.netflix.spinnaker.clouddriver.artifacts.ivy.settings.IvySettings;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import org.apache.commons.io.Charsets;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junitpioneer.jupiter.TempDirectory;
import ru.lanwen.wiremock.ext.WiremockResolver;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith({WiremockResolver.class, TempDirectory.class})
class IvyArtifactCredentialsTest {
  @Test
  void downloadIvyBasedJar(@WiremockResolver.Wiremock WireMockServer server, @TempDirectory.TempDir Path tempDir) throws IOException {
    server.stubFor(any(urlEqualTo("/com/test/app/1.0/app-1.0.jar"))
      .willReturn(aResponse()
        .withBody("contents")));
    server.stubFor(any(urlEqualTo("/com/test/app/1.0/app-1.0.xml"))
      .willReturn(aResponse()
        .withBody("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
          "<ivy-module version=\"2.0\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xsi:noNamespaceSchemaLocation=\"http://ant.apache.org/ivy/schemas/ivy.xsd\">\n" +
          "  <info organisation=\"com.test\" module=\"app\" revision=\"1.0\" status=\"release\"/>\n" +
          "  <configurations defaultconf=\"master\">\n" +
          "    <conf name=\"master\"/>\n" +
          "  </configurations>\n" +
          "  <publications>\n" +
          "    <artifact/>\n" +
          "  </publications>\n" +
          "</ivy-module>")));

    String ivySettingsXml = "<ivy-settings>\n" +
      "  <settings defaultResolver=\"main\" />\n" +
      "  <resolvers>\n" +
      "    <url name=\"main\">\n" +
      "      <ivy pattern=\"" + server.baseUrl() + "/[orgPath]/[module]/[revision]/[module]-[revision].xml\"/>\n" +
      "      <artifact pattern=\"" + server.baseUrl() + "/[orgPath]/[module]/[revision]/[module]-[revision].[ext]\"/>\n" +
      "    </url>\n" +
      "  </resolvers>\n" +
      "</ivy-settings>";

    assertDownloadArtifact(tempDir, ivySettingsXml);
    assertThat(server.findUnmatchedRequests().getRequests()).isEmpty();
  }

  @Test
  void downloadMavenBasedJarFromIvySettings(@WiremockResolver.Wiremock WireMockServer server, @TempDirectory.TempDir Path tempDir) throws IOException {
    server.stubFor(any(urlEqualTo("/com/test/app/1.0/app-1.0.jar"))
      .willReturn(aResponse().withBody("contents")));

    // only HEAD requests, should not be downloaded
    server.stubFor(head(urlEqualTo("/com/test/app/1.0/app-1.0-sources.jar"))
      .willReturn(aResponse().withBody("contents")));
    server.stubFor(head(urlEqualTo("/com/test/app/1.0/app-1.0-javadoc.jar"))
      .willReturn(aResponse().withBody("contents")));

    server.stubFor(any(urlEqualTo("/com/test/app/1.0/app-1.0.pom"))
      .willReturn(aResponse()
        .withBody("<project>\n" +
          "  <modelVersion>4.0.0</modelVersion>\n" +
          "  <groupId>com.test</groupId>\n" +
          "  <artifactId>app</artifactId  >\n" +
          "  <version>1.0</version>\n" +
          "</project>")));

    String ivySettingsXml = "<ivy-settings>\n" +
      "  <settings defaultResolver=\"main\" />\n" +
      "  <resolvers>\n" +
      "    <chain name=\"main\">\n" +
      "      <ibiblio name=\"public\" m2compatible=\"true\" root=\"" + server.baseUrl() + "\" />\n" +
      "    </chain>\n" +
      "  </resolvers>\n" +
      "</ivy-settings>";

    assertDownloadArtifact(tempDir, ivySettingsXml);
    assertThat(server.findUnmatchedRequests().getRequests()).isEmpty();
  }

  private void assertDownloadArtifact(@TempDirectory.TempDir Path tempDir, String ivySettingsXml) throws IOException {
    IvyArtifactAccount account = new IvyArtifactAccount();
    account.setSettings(IvySettings.parse(ivySettingsXml));

    Path cache = tempDir.resolve("cache");
    Files.createDirectories(cache);

    Artifact artifact = new Artifact();
    artifact.setReference("com.test:app:1.0");

    assertThat(new IvyArtifactCredentials(account, () -> cache).download(artifact))
      .hasSameContentAs(new ByteArrayInputStream("contents".getBytes(Charsets.UTF_8)));
    assertThat(cache).doesNotExist();
  }
}