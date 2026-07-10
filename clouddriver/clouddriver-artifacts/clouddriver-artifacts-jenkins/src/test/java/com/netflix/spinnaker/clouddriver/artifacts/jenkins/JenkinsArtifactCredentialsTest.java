/*
 * Copyright 2026 Harness Inc.
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

package com.netflix.spinnaker.clouddriver.artifacts.jenkins;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import java.io.IOException;
import okhttp3.OkHttpClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import ru.lanwen.wiremock.ext.WiremockResolver;

@ExtendWith(WiremockResolver.class)
class JenkinsArtifactCredentialsTest {

  private final OkHttpClient okHttpClient = new OkHttpClient();

  private static final String JOB_NAME = "my-job";
  private static final String JOB_VERSION = "42";
  private static final String ARTIFACT_REFERENCE = "/artifact/result.zip";

  @Test
  void downloadArtifact(@WiremockResolver.Wiremock WireMockServer server) throws IOException {
    JenkinsArtifactAccount account =
        JenkinsArtifactAccount.builder()
            .name("my-jenkins-account")
            .address(server.baseUrl())
            .build();

    JenkinsArtifactCredentials credentials = new JenkinsArtifactCredentials(account, okHttpClient);

    String artifactPath = "/job/" + JOB_NAME + "/" + JOB_VERSION + "/artifact" + ARTIFACT_REFERENCE;

    server.stubFor(
        any(urlPathEqualTo(artifactPath)).willReturn(aResponse().withBody("artifact-contents")));

    Artifact artifact =
        Artifact.builder()
            .name(JOB_NAME)
            .version(JOB_VERSION)
            .reference(ARTIFACT_REFERENCE)
            .type("jenkins/file")
            .build();

    assertThat(credentials.download(artifact)).isNotNull();
  }
}
