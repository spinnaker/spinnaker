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

import com.netflix.spinnaker.kork.github.GitHubAppCredentials;
import com.netflix.spinnaker.kork.github.test.GitHubAppTestKeys;
import com.netflix.spinnaker.kork.web.url.UrlRestrictionsProperties;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** An account that sends credentials is limited to the hosts of github.com unless it says so. */
class GitHubArtifactAccountAllowedDomainsTest {
  private static final List<String> DEFAULTS =
      List.of(
          "github\\.com",
          "api\\.github\\.com",
          "codeload\\.github\\.com",
          "raw\\.githubusercontent\\.com",
          "media\\.githubusercontent\\.com",
          "objects\\.githubusercontent\\.com");

  private static List<String> allowedDomains(GitHubArtifactAccount account) {
    return account.getUrlRestrictions().getAllowedDomains();
  }

  @Test
  void defaultsAnAccountWithATokenToTheGitHubHosts() {
    assertThat(allowedDomains(GitHubArtifactAccount.builder().name("a").token("t").build()))
        .containsExactlyElementsOf(DEFAULTS);
  }

  @Test
  void defaultsAnAccountWithATokenFileToTheGitHubHosts() {
    assertThat(allowedDomains(GitHubArtifactAccount.builder().name("a").tokenFile("f").build()))
        .containsExactlyElementsOf(DEFAULTS);
  }

  @Test
  void defaultsAnAccountWithBasicAuthToTheGitHubHosts() {
    assertThat(
            allowedDomains(
                GitHubArtifactAccount.builder().name("a").username("u").password("p").build()))
        .containsExactlyElementsOf(DEFAULTS);
    assertThat(
            allowedDomains(
                GitHubArtifactAccount.builder().name("a").usernamePasswordFile("f").build()))
        .containsExactlyElementsOf(DEFAULTS);
  }

  @Test
  void defaultsAGitHubAppToTheGitHubHosts(@TempDir Path tempDir) throws Exception {
    Path privateKeyFile = tempDir.resolve("gh-app-key.pem");
    GitHubAppTestKeys.writePkcs8Pem(privateKeyFile);
    GitHubArtifactAccount account =
        GitHubArtifactAccount.builder()
            .name("a")
            .githubApp(
                new GitHubAppCredentials("12345", privateKeyFile.toString(), null, null, List.of()))
            .build();

    assertThat(account.hasCredentials()).isTrue();
    assertThat(allowedDomains(account)).containsExactlyElementsOf(DEFAULTS);
  }

  @Test
  void keepsConfiguredAllowedDomains() {
    GitHubArtifactAccount account =
        GitHubArtifactAccount.builder()
            .name("a")
            .token("t")
            .urlRestrictions(
                UrlRestrictionsProperties.builder()
                    .allowedDomains(List.of("ghe\\.example\\.com"))
                    .build())
            .build();

    assertThat(allowedDomains(account)).containsExactly("ghe\\.example\\.com");
  }

  @Test
  void keepsAnExplicitOptOutOfTheDefaults() {
    GitHubArtifactAccount account =
        GitHubArtifactAccount.builder()
            .name("a")
            .token("t")
            .urlRestrictions(
                UrlRestrictionsProperties.builder().allowedDomains(List.of(".*")).build())
            .build();

    assertThat(allowedDomains(account)).containsExactly(".*");
  }

  @Test
  void leavesAnAccountWithoutCredentialsUnrestricted() {
    GitHubArtifactAccount account = GitHubArtifactAccount.builder().name("anonymous").build();

    assertThat(account.hasCredentials()).isFalse();
    assertThat(allowedDomains(account)).isEmpty();
  }
}
