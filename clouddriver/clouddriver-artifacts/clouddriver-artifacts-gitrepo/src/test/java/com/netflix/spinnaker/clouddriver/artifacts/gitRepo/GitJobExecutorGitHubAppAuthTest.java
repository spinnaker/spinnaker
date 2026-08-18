/*
 * Copyright 2026 Harness, Inc.
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.netflix.spinnaker.clouddriver.jobs.JobExecutor;
import com.netflix.spinnaker.clouddriver.jobs.JobRequest;
import com.netflix.spinnaker.clouddriver.jobs.JobResult;
import com.netflix.spinnaker.kork.github.GitHubAppAuthenticator;
import com.netflix.spinnaker.kork.github.GitHubAppCredentials;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.junitpioneer.jupiter.TempDirectory;
import org.mockito.ArgumentCaptor;

/** Tests for GitHub App authentication in GitJobExecutor. */
@ExtendWith({TempDirectory.class})
class GitJobExecutorGitHubAppAuthTest {

  private static final String INSTALLATION_TOKEN = "ghs_installation-token-123";

  @TempDir Path tempDir;

  private JobExecutor mockJobExecutor;
  private GitHubAppAuthenticator mockAuthenticator;

  @BeforeEach
  void setUp() throws IOException {
    mockJobExecutor = mock(JobExecutor.class);
    when(mockJobExecutor.runJob(any()))
        .thenReturn(
            JobResult.<String>builder().result(JobResult.Result.SUCCESS).output("success").build());

    mockAuthenticator = mock(GitHubAppAuthenticator.class);
    when(mockAuthenticator.getInstallationToken()).thenReturn(INSTALLATION_TOKEN);
  }

  @Test
  @DisplayName("Clone uses x-access-token with the installation token resolved per command")
  void cloneUsesGitHubAppInstallationToken() throws IOException {
    GitJobExecutor executor = executorFor(accountWithGitHubApp().build());

    executor.cloneOrPull(
        "https://github.com/org/repo.git", "main", tempDir.resolve("clone"), "repo");

    JobRequest request = capturedJobRequest();
    assertThat(request.getTokenizedCommand())
        .containsExactly(
            "sh",
            "-c",
            "git clone --branch main --depth 1 https://x-access-token:$GIT_TOKEN@github.com/org/repo.git");
    assertThat(request.getEnvironment()).containsEntry("GIT_TOKEN", INSTALLATION_TOKEN);
    verify(mockAuthenticator).getInstallationToken();
  }

  @Test
  @DisplayName("GitHub App auth takes precedence over basic auth and token auth")
  void githubAppTakesPrecedenceOverOtherAuthMethods() throws IOException {
    GitRepoArtifactAccount account =
        accountWithGitHubApp()
            .username("some-user")
            .password("some-password")
            .token("some-token")
            .build();
    GitJobExecutor executor = executorFor(account);

    executor.cloneOrPull(
        "https://github.com/org/repo.git", "main", tempDir.resolve("clone"), "repo");

    JobRequest request = capturedJobRequest();
    assertThat(String.join(" ", request.getTokenizedCommand()))
        .contains("x-access-token:$GIT_TOKEN@")
        .doesNotContain("$GIT_USER");
    assertThat(request.getEnvironment())
        .containsEntry("GIT_TOKEN", INSTALLATION_TOKEN)
        .doesNotContainKey("GIT_USER")
        .doesNotContainKey("GIT_PASS");
  }

  @Test
  @DisplayName("SSH-style repo URLs are rejected when using GitHub App auth")
  void rejectsSshStyleUrls() throws IOException {
    GitJobExecutor executor = executorFor(accountWithGitHubApp().build());

    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                executor.cloneOrPull(
                    "git@github.com:org/repo.git", "main", tempDir.resolve("clone"), "repo"));

    assertThat(exception.getMessage()).contains("GITHUB_APP");
  }

  @Test
  @DisplayName("Pull on a retained clone re-points origin at a fresh installation token")
  void pullRefreshesOriginWithFreshInstallationToken() throws IOException {
    GitJobExecutor executor = executorFor(accountWithGitHubApp().build());
    Path localPath = retainedCloneLayout();

    executor.cloneOrPull("https://github.com/org/repo.git", "main", localPath, "repo");

    List<JobRequest> requests = capturedJobRequests(3);
    assertThat(String.join(" ", requests.get(0).getTokenizedCommand()))
        .isEqualTo("sh -c git symbolic-ref HEAD");
    assertThat(requests.get(1).getTokenizedCommand())
        .containsExactly(
            "sh",
            "-c",
            "git remote set-url origin https://x-access-token:$GIT_TOKEN@github.com/org/repo.git");
    assertThat(requests.get(1).getEnvironment()).containsEntry("GIT_TOKEN", INSTALLATION_TOKEN);
    assertThat(String.join(" ", requests.get(2).getTokenizedCommand())).isEqualTo("sh -c git pull");
  }

  @Test
  @DisplayName("Pull on a retained clone does not rewrite origin for static token auth")
  void pullDoesNotRewriteOriginForStaticTokenAuth() throws IOException {
    GitRepoArtifactAccount account =
        GitRepoArtifactAccount.builder().name("test-account").token("static-token").build();
    GitJobExecutor executor = executorFor(account);
    Path localPath = retainedCloneLayout();

    executor.cloneOrPull("https://github.com/org/repo.git", "main", localPath, "repo");

    List<JobRequest> requests = capturedJobRequests(2);
    assertThat(String.join(" ", requests.get(0).getTokenizedCommand()))
        .isEqualTo("sh -c git symbolic-ref HEAD");
    assertThat(String.join(" ", requests.get(1).getTokenizedCommand())).isEqualTo("sh -c git pull");
  }

  @Test
  @DisplayName("Repo URLs on a different GitHub instance than apiBaseUrl are rejected")
  void rejectsRepoHostMismatchingApiBaseUrl() throws IOException {
    // account mints tokens against api.github.com (default), but the repo lives on GHE
    GitJobExecutor executor = executorFor(accountWithGitHubApp(null).build());

    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                executor.cloneOrPull(
                    "https://ghe.example.com/org/repo.git",
                    "main",
                    tempDir.resolve("clone"),
                    "repo"));

    assertThat(exception.getMessage())
        .contains("ghe.example.com")
        .contains("api.github.com")
        .contains("githubApp.apiBaseUrl");
    verify(mockJobExecutor, never()).runJob(any());
  }

  @Test
  @DisplayName("GitHub Enterprise repos work with a matching apiBaseUrl")
  void acceptsGheRepoWithMatchingApiBaseUrl() throws IOException {
    GitJobExecutor executor =
        executorFor(accountWithGitHubApp("https://ghe.example.com/api/v3").build());

    executor.cloneOrPull(
        "https://ghe.example.com/org/repo.git", "main", tempDir.resolve("clone"), "repo");

    JobRequest request = capturedJobRequest();
    assertThat(request.getTokenizedCommand())
        .containsExactly(
            "sh",
            "-c",
            "git clone --branch main --depth 1 https://x-access-token:$GIT_TOKEN@ghe.example.com/org/repo.git");
    assertThat(request.getEnvironment()).containsEntry("GIT_TOKEN", INSTALLATION_TOKEN);
  }

  @Test
  @DisplayName("Account initialization fails fast when the private key cannot be loaded")
  void failsFastWhenPrivateKeyCannotBeLoaded() {
    GitHubAppCredentials githubApp =
        new GitHubAppCredentials("12345", "/nonexistent/key.pem", "67890", null);
    GitRepoArtifactAccount account =
        GitRepoArtifactAccount.builder().name("test-account").githubApp(githubApp).build();

    IOException exception =
        assertThrows(
            IOException.class,
            () ->
                new GitJobExecutor(
                    account,
                    mockJobExecutor,
                    "git",
                    GitRepoArtifactProviderProperties.DEFAULT_GIT_URL_REGEX_PATTERN));

    assertThat(exception.getMessage()).contains("Failed to initialize GitHub App authentication");
  }

  private GitRepoArtifactAccount.GitRepoArtifactAccountBuilder accountWithGitHubApp() {
    return accountWithGitHubApp(null);
  }

  private GitRepoArtifactAccount.GitRepoArtifactAccountBuilder accountWithGitHubApp(
      String apiBaseUrl) {
    GitHubAppCredentials githubApp =
        new GitHubAppCredentials("12345", "/path/to/key.pem", "67890", apiBaseUrl);
    return GitRepoArtifactAccount.builder().name("test-account").githubApp(githubApp);
  }

  private GitJobExecutor executorFor(GitRepoArtifactAccount account) throws IOException {
    return new GitJobExecutor(
        account,
        mockJobExecutor,
        "git",
        GitRepoArtifactProviderProperties.DEFAULT_GIT_URL_REGEX_PATTERN,
        mockAuthenticator);
  }

  private JobRequest capturedJobRequest() {
    return capturedJobRequests(1).get(0);
  }

  private List<JobRequest> capturedJobRequests(int times) {
    ArgumentCaptor<JobRequest> captor = ArgumentCaptor.forClass(JobRequest.class);
    verify(mockJobExecutor, times(times)).runJob(captor.capture());
    return captor.getAllValues();
  }

  // Simulates a previously cloned, retained repo: <localPath>/<repoBasename>/.git exists
  private Path retainedCloneLayout() throws IOException {
    Path localPath = tempDir.resolve("clone");
    Files.createDirectories(localPath.resolve("repo").resolve(".git"));
    return localPath;
  }
}
