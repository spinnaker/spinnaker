/*
 * Copyright 2026 spinnaker.io
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

package com.netflix.spinnaker.clouddriver.artifacts.github;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.clouddriver.artifacts.config.BaseHttpArtifactCredentials;
import com.netflix.spinnaker.kork.github.GitHubAppCredentials;
import com.netflix.spinnaker.kork.github.test.GitHubAppTestKeys;
import com.netflix.spinnaker.kork.web.url.UrlRestrictionsProperties;
import java.nio.file.Path;
import java.util.List;
import okhttp3.OkHttpClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;

/**
 * A GitHub App authenticates with an installation token rather than a token or password, but the
 * token is sent with the requests like any other credential, so it needs host restrictions too.
 */
class GitHubArtifactAccountWarningTest {
  private final Logger logger = (Logger) LoggerFactory.getLogger(BaseHttpArtifactCredentials.class);
  private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

  @BeforeEach
  void captureLogs() {
    appender.start();
    logger.addAppender(appender);
  }

  @AfterEach
  void stopCapturingLogs() {
    logger.detachAppender(appender);
  }

  private List<ILoggingEvent> warnings() {
    return appender.list.stream().filter(e -> e.getLevel() == Level.WARN).toList();
  }

  private static GitHubArtifactAccount.GitHubArtifactAccountBuilder appAccount(Path tempDir)
      throws Exception {
    Path privateKeyFile = tempDir.resolve("gh-app-key.pem");
    GitHubAppTestKeys.writePkcs8Pem(privateKeyFile);
    return GitHubArtifactAccount.builder()
        .name("my-account")
        .githubApp(
            new GitHubAppCredentials("12345", privateKeyFile.toString(), null, null, List.of()));
  }

  private static void create(GitHubArtifactAccount account) throws Exception {
    new GitHubArtifactCredentials(account, new OkHttpClient(), new ObjectMapper());
  }

  @Test
  void warnsForAGitHubAppWithoutAllowedDomains(@TempDir Path tempDir) throws Exception {
    GitHubArtifactAccount account = appAccount(tempDir).build();

    create(account);

    assertThat(account.hasCredentials()).isTrue();
    assertThat(warnings()).hasSize(1);
    assertThat(warnings().get(0).getFormattedMessage())
        .contains("my-account")
        .contains("allowedDomains");
  }

  @Test
  void doesNotWarnForAGitHubAppWithAllowedDomains(@TempDir Path tempDir) throws Exception {
    create(
        appAccount(tempDir)
            .urlRestrictions(
                UrlRestrictionsProperties.builder()
                    .allowedDomains(List.of("github\\.com", "api\\.github\\.com"))
                    .build())
            .build());

    assertThat(warnings()).isEmpty();
  }

  @Test
  void warnsForATokenWithoutAllowedDomains() throws Exception {
    create(GitHubArtifactAccount.builder().name("my-account").token("t").build());

    assertThat(warnings()).hasSize(1);
  }

  @Test
  void doesNotWarnWithoutCredentials() throws Exception {
    GitHubArtifactAccount account = GitHubArtifactAccount.builder().name("anonymous").build();

    create(account);

    assertThat(account.hasCredentials()).isFalse();
    assertThat(warnings()).isEmpty();
  }
}
