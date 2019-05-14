/*
 * Copyright 2019 Pivotal, Inc.
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

package com.netflix.spinnaker.clouddriver.artifacts.maven;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import com.squareup.okhttp.OkHttpClient;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import javax.annotation.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import ru.lanwen.wiremock.ext.WiremockResolver;

@ExtendWith(WiremockResolver.class)
class MavenArtifactCredentialsTest {
  @Test
  void release(@WiremockResolver.Wiremock WireMockServer server) {
    server.stubFor(
        any(urlPathMatching("/com/test/app/maven-metadata.xml"))
            .willReturn(
                aResponse()
                    .withBody(
                        "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                            + "<metadata modelVersion=\"1.1.0\">\n"
                            + "  <groupId>com.test</groupId>\n"
                            + "  <artifactId>app</artifactId>\n"
                            + "  <version>1.1</version>\n"
                            + "  <versioning>\n"
                            + "    <latest>1.1</latest>\n"
                            + "    <release>1.1</release>\n"
                            + "    <versions>\n"
                            + "      <version>1.0</version>\n"
                            + "      <version>1.1</version>\n"
                            + "    </versions>\n"
                            + "    <lastUpdated>20190322061505</lastUpdated>\n"
                            + "  </versioning>\n"
                            + "</metadata>")));

    assertResolvable(server, "latest.release", "1.1");
    assertResolvable(server, "RELEASE", "1.1");
    assertResolvable(server, "LATEST", "1.1");
    assertResolvable(server, "1.1", "1.1");
    assertResolvable(server, "1.0", "1.0");
  }

  @Test
  void snapshot(@WiremockResolver.Wiremock WireMockServer server) {
    server.stubFor(
        any(urlPathMatching("/com/test/app/maven-metadata.xml"))
            .willReturn(
                aResponse()
                    .withBody(
                        "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                            + "<metadata modelVersion=\"1.1.0\">\n"
                            + "  <groupId>com.test</groupId>\n"
                            + "  <artifactId>app</artifactId>\n"
                            + "  <version>1.1-SNAPSHOT</version>\n"
                            + "  <versioning>\n"
                            + "    <latest>1.1-SNAPSHOT</latest>\n"
                            + "    <versions>\n"
                            + "      <version>1.0-SNAPSHOT</version>\n"
                            + "      <version>1.1-SNAPSHOT</version>\n"
                            + "    </versions>\n"
                            + "    <lastUpdated>20190322061505</lastUpdated>\n"
                            + "  </versioning>\n"
                            + "</metadata>")));

    server.stubFor(
        any(urlPathMatching("/com/test/app/1.1-SNAPSHOT/maven-metadata.xml"))
            .willReturn(
                aResponse()
                    .withBody(
                        "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                            + "<metadata modelVersion=\"1.1.0\">\n"
                            + "  <groupId>com.test</groupId>\n"
                            + "  <artifactId>app</artifactId>\n"
                            + "  <version>1.1-SNAPSHOT</version>\n"
                            + "  <versioning>\n"
                            + "    <snapshot>\n"
                            + "      <timestamp>20190322.061344</timestamp>\n"
                            + "      <buildNumber>90</buildNumber>\n"
                            + "    </snapshot>\n"
                            + "    <lastUpdated>20190322061504</lastUpdated>\n"
                            + "    <snapshotVersions>\n"
                            + "      <snapshotVersion>\n"
                            + "        <classifier>sources</classifier>\n"
                            + "        <extension>jar</extension>\n"
                            + "        <value>1.1-20190322.061344-90</value>\n"
                            + "        <updated>20190322061344</updated>\n"
                            + "      </snapshotVersion>\n"
                            + "      <snapshotVersion>\n"
                            + "        <extension>jar</extension>\n"
                            + "        <value>1.1-20190322.061344-90</value>\n"
                            + "        <updated>20190322061344</updated>\n"
                            + "      </snapshotVersion>\n"
                            + "      <snapshotVersion>\n"
                            + "        <extension>pom</extension>\n"
                            + "        <value>1.1-20190322.061344-90</value>\n"
                            + "        <updated>20190322061344</updated>\n"
                            + "      </snapshotVersion>\n"
                            + "    </snapshotVersions>\n"
                            + "  </versioning>\n"
                            + "</metadata>")));

    assertResolvable(server, "latest.integration", "1.1-20190322.061344-90", "1.1-SNAPSHOT");
    assertResolvable(server, "LATEST", "1.1-20190322.061344-90", "1.1-SNAPSHOT");
    assertResolvable(server, "SNAPSHOT", "1.1-20190322.061344-90", "1.1-SNAPSHOT");
    assertResolvable(server, "1.1-SNAPSHOT", "1.1-20190322.061344-90", "1.1-SNAPSHOT");
  }

  @Test
  void rangeVersion(@WiremockResolver.Wiremock WireMockServer server) {
    server.stubFor(
        any(urlPathMatching("/com/test/app/maven-metadata.xml"))
            .willReturn(
                aResponse()
                    .withBody(
                        "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                            + "<metadata modelVersion=\"1.1.0\">\n"
                            + "  <groupId>com.test</groupId>\n"
                            + "  <artifactId>app</artifactId>\n"
                            + "  <version>2.0</version>\n"
                            + "  <versioning>\n"
                            + "    <latest>2.0</latest>\n"
                            + "    <release>2.0</release>\n"
                            + "    <versions>\n"
                            + "      <version>1.0</version>\n"
                            + "      <version>1.1</version>\n"
                            + "      <version>2.0</version>\n"
                            + "    </versions>\n"
                            + "    <lastUpdated>20190322061505</lastUpdated>\n"
                            + "  </versioning>\n"
                            + "</metadata>")));

    assertResolvable(server, "[1.0,)", "2.0");
    assertResolvable(server, "[1.0,2.0)", "1.1");
    assertResolvable(server, "(,2.0]", "2.0");
  }

  private void assertResolvable(WireMockServer server, String version, String expectedVersion) {
    assertResolvable(server, version, expectedVersion, null);
  }

  private void assertResolvable(
      WireMockServer server,
      String version,
      String expectedVersion,
      @Nullable String expectedSnapshotVersion) {
    String jarUrl =
        "/com/test/app/"
            + (expectedSnapshotVersion == null ? expectedVersion : expectedSnapshotVersion)
            + "/app-"
            + expectedVersion
            + ".jar";

    server.stubFor(any(urlEqualTo(jarUrl)).willReturn(aResponse().withBody(expectedVersion)));

    MavenArtifactAccount account = new MavenArtifactAccount();
    account.setRepositoryUrl(server.baseUrl());

    Artifact artifact = new Artifact();
    artifact.setReference("com.test:app:" + version);

    assertThat(new MavenArtifactCredentials(account, new OkHttpClient()).download(artifact))
        .hasSameContentAs(
            new ByteArrayInputStream(expectedVersion.getBytes(StandardCharsets.UTF_8)));

    assertThat(server.findUnmatchedRequests().getRequests()).isEmpty();
  }
}
