/*
 * Copyright 2026 McIntosh.farm
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for GitRepoArtifactCredentials to verify URL validation against allowedHosts list when
 * invoking the download method.
 */
class GitRepoArtifactCredentialsTest {
  GitJobExecutor mockExecutor;
  GitRepoFileSystem mockFileSystem;

  @BeforeEach
  void setUp() {
    mockExecutor = mock(GitJobExecutor.class);
    mockFileSystem = mock(GitRepoFileSystem.class);
    GitRepoArtifactAccount mockAccount =
        new GitRepoArtifactAccount(
            "mockAccount", null, null, null, null, null, null, null, null, false);
    when(mockExecutor.getAccount()).thenReturn(mockAccount);
    mockFileSystem = mock(GitRepoFileSystem.class);
  }

  @Test
  @DisplayName("Should allow download when URL host matches allowedHosts list.  ")
  void shouldAllowDownloadWhenHostMatches() throws Exception {
    List<String> allowedHosts = Arrays.asList("github.com", "gitlab.com", "bitbucket.org");

    GitRepoArtifactCredentials credentials =
        new GitRepoArtifactCredentials(mockExecutor, mockFileSystem, allowedHosts);

    Artifact artifact =
        Artifact.builder()
            .reference("https://github.com/spinnaker/clouddriver.git")
            .version("main")
            .build();
    validateHostVerificationByVerifyingALaterException(credentials, artifact);
  }

  @Test
  @DisplayName("Should allow download when URL host with subdomain matches allowedHosts")
  void shouldAllowDownloadWhenSubdomainMatches() throws Exception {
    List<String> allowedHosts = Collections.singletonList("github.com");

    GitRepoArtifactCredentials credentials =
        new GitRepoArtifactCredentials(mockExecutor, mockFileSystem, allowedHosts);

    Artifact artifact =
        Artifact.builder()
            .reference("https://api.github.com/repos/spinnaker/clouddriver.git")
            .version("main")
            .build();
    validateHostVerificationByVerifyingALaterException(credentials, artifact);
  }

  @Test
  @DisplayName("Should not allow a parent if a subdomain is set")
  void failIfSubDomainAllowedAndTryToUseParent() throws Exception {
    List<String> allowedHosts = Collections.singletonList("internal.github.com");

    GitRepoArtifactCredentials credentials =
        new GitRepoArtifactCredentials(mockExecutor, mockFileSystem, allowedHosts);

    Artifact artifact =
        Artifact.builder()
            .reference("https://github.com/repos/spinnaker/clouddriver.git")
            .version("main")
            .build();
    IllegalArgumentException exception =
        assertThrows(IllegalArgumentException.class, () -> credentials.download(artifact));
    assertThat(exception.getMessage()).containsIgnoringCase("host");
  }

  @Test
  @DisplayName("Should reject download when URL host is not in allowedHosts list")
  void shouldRejectDownloadWhenHostNotInList() {
    List<String> allowedHosts = Arrays.asList("github.com", "gitlab.com");

    GitRepoArtifactCredentials credentials =
        new GitRepoArtifactCredentials(mockExecutor, mockFileSystem, allowedHosts);

    Artifact artifact =
        Artifact.builder().reference("https://evil.com/malicious/repo.git").version("main").build();

    IllegalArgumentException exception =
        assertThrows(IllegalArgumentException.class, () -> credentials.download(artifact));

    assertThat(exception.getMessage()).containsIgnoringCase("host");
  }

  @Test
  @DisplayName("Should allow download when allowedHosts list is null/empty")
  void allowAnyHostIfAllowedHostsIsEmpty() throws Exception {
    Artifact artifact =
        Artifact.builder()
            .reference("https://github.com/spinnaker/clouddriver.git")
            .version("main")
            .build();
    when(mockFileSystem.tryTimedLock(anyString(), anyString())).thenReturn(true);
    when(mockFileSystem.getCloneWaitLockTimeoutSec()).thenReturn(60);
    when(mockFileSystem.getLocalClonePath(anyString(), anyString()))
        .thenReturn(Files.createTempDirectory("gitrepoartifacttests"));

    validateHostVerificationByVerifyingALaterException(
        new GitRepoArtifactCredentials(mockExecutor, mockFileSystem, Collections.emptyList()),
        artifact);
    validateHostVerificationByVerifyingALaterException(
        new GitRepoArtifactCredentials(mockExecutor, mockFileSystem, null), artifact);
  }

  @Test
  @DisplayName("Should handle SSH URLs for host validation")
  void shouldHandleSshUrls() throws Exception {
    List<String> allowedHosts = Collections.singletonList("github.com");

    GitRepoArtifactCredentials credentials =
        new GitRepoArtifactCredentials(mockExecutor, mockFileSystem, allowedHosts);

    Artifact artifact =
        Artifact.builder()
            .reference("git@github.com:spinnaker/clouddriver.git")
            .version("main")
            .build();

    validateHostVerificationByVerifyingALaterException(credentials, artifact);
  }

  @Test
  @DisplayName("Should reject SSH URLs when host is not in allowedHosts")
  void shouldRejectSshUrlsWhenHostNotAllowed() {
    List<String> allowedHosts = Collections.singletonList("github.com");

    GitRepoArtifactCredentials credentials =
        new GitRepoArtifactCredentials(mockExecutor, mockFileSystem, allowedHosts);

    Artifact artifact =
        Artifact.builder().reference("git@evil.com:malicious/repo.git").version("main").build();

    IllegalArgumentException exception =
        assertThrows(IllegalArgumentException.class, () -> credentials.download(artifact));

    assertThat(exception.getMessage()).containsIgnoringCase("host");
  }

  @Test
  @DisplayName("Should handle git:// protocol URLs")
  void shouldHandleGitProtocolUrls() throws Exception {
    List<String> allowedHosts = Collections.singletonList("github.com");

    GitRepoArtifactCredentials credentials =
        new GitRepoArtifactCredentials(mockExecutor, mockFileSystem, allowedHosts);

    Artifact artifact =
        Artifact.builder()
            .reference("git://github.com/spinnaker/clouddriver.git")
            .version("main")
            .build();

    validateHostVerificationByVerifyingALaterException(credentials, artifact);
  }

  @Test
  @DisplayName("Should reject git:// protocol URLs when host not allowed")
  void shouldRejectGitProtocolUrlsWhenHostNotAllowed() {
    List<String> allowedHosts = Collections.singletonList("github.com");

    GitRepoArtifactCredentials credentials =
        new GitRepoArtifactCredentials(mockExecutor, mockFileSystem, allowedHosts);

    Artifact artifact =
        Artifact.builder().reference("git://evil.com/malicious/repo.git").version("main").build();

    IllegalArgumentException exception =
        assertThrows(IllegalArgumentException.class, () -> credentials.download(artifact));

    assertThat(exception.getMessage()).containsIgnoringCase("host");
  }

  @Test
  @DisplayName("Should handle URLs with ports")
  void shouldHandleUrlsWithPorts() throws Exception {
    List<String> allowedHosts = Collections.singletonList("gitlab.internal.com");

    GitRepoArtifactCredentials credentials =
        new GitRepoArtifactCredentials(mockExecutor, mockFileSystem, allowedHosts);

    Artifact artifact =
        Artifact.builder()
            .reference("https://gitlab.internal.com:8443/spinnaker/clouddriver.git")
            .version("main")
            .build();

    validateHostVerificationByVerifyingALaterException(credentials, artifact);
  }

  @Test
  @DisplayName("Should handle URLs with authentication in URL")
  void shouldHandleUrlsWithAuth() throws Exception {
    List<String> allowedHosts = Collections.singletonList("github.com");

    GitRepoArtifactCredentials credentials =
        new GitRepoArtifactCredentials(mockExecutor, mockFileSystem, allowedHosts);

    Artifact artifact =
        Artifact.builder()
            .reference("https://user:token@github.com/spinnaker/clouddriver.git")
            .version("main")
            .build();

    validateHostVerificationByVerifyingALaterException(credentials, artifact);
  }

  @Test
  @DisplayName("Should reject URL with invalid host even with auth in URL")
  void shouldRejectInvalidHostWithAuth() {
    List<String> allowedHosts = Collections.singletonList("github.com");

    GitRepoArtifactCredentials credentials =
        new GitRepoArtifactCredentials(mockExecutor, mockFileSystem, allowedHosts);

    Artifact artifact =
        Artifact.builder()
            .reference("https://user:token@evil.com/malicious/repo.git")
            .version("main")
            .build();

    IllegalArgumentException exception =
        assertThrows(IllegalArgumentException.class, () -> credentials.download(artifact));

    assertThat(exception.getMessage()).containsIgnoringCase("host");
  }

  @Test
  @DisplayName("Should be case-insensitive for host matching")
  void shouldBeCaseInsensitiveForHostMatching() throws Exception {
    List<String> allowedHosts = Collections.singletonList("GitHub.COM");

    GitRepoArtifactCredentials credentials =
        new GitRepoArtifactCredentials(mockExecutor, mockFileSystem, allowedHosts);

    Artifact artifact =
        Artifact.builder()
            .reference("https://github.com/spinnaker/clouddriver.git")
            .version("main")
            .build();

    validateHostVerificationByVerifyingALaterException(credentials, artifact);
  }

  @Test
  @DisplayName("Should handle malformed URLs gracefully")
  void shouldHandleMalformedUrls() {
    List<String> allowedHosts = Collections.singletonList("github.com");

    GitRepoArtifactCredentials credentials =
        new GitRepoArtifactCredentials(mockExecutor, mockFileSystem, allowedHosts);

    Artifact artifact = Artifact.builder().reference("not-a-valid-url").version("main").build();

    assertThrows(IllegalArgumentException.class, () -> credentials.download(artifact));
  }

  @Test
  @DisplayName("Should reject when URL reference is null")
  void shouldRejectWhenReferenceIsNull() {
    List<String> allowedHosts = Collections.singletonList("github.com");

    GitRepoArtifactCredentials credentials =
        new GitRepoArtifactCredentials(mockExecutor, mockFileSystem, allowedHosts);

    Artifact artifact = Artifact.builder().version("main").build();

    assertThrows(IllegalArgumentException.class, () -> credentials.download(artifact));
  }

  @Test
  @DisplayName("Should reject when URL reference is empty")
  void shouldRejectWhenReferenceIsEmpty() {
    List<String> allowedHosts = Collections.singletonList("github.com");

    GitRepoArtifactCredentials credentials =
        new GitRepoArtifactCredentials(mockExecutor, mockFileSystem, allowedHosts);

    Artifact artifact = Artifact.builder().reference("").version("main").build();

    assertThrows(IllegalArgumentException.class, () -> credentials.download(artifact));
  }

  @Test
  @DisplayName("Should handle SSH URLs with ssh:// protocol")
  void shouldHandleSshProtocolUrls() throws Exception {
    List<String> allowedHosts = Collections.singletonList("github.com");

    GitRepoArtifactCredentials credentials =
        new GitRepoArtifactCredentials(mockExecutor, mockFileSystem, allowedHosts);

    Artifact artifact =
        Artifact.builder()
            .reference("ssh://git@github.com/spinnaker/clouddriver.git")
            .version("main")
            .build();

    validateHostVerificationByVerifyingALaterException(credentials, artifact);
  }

  @Test
  @DisplayName("Should support multiple allowed hosts")
  void shouldSupportMultipleAllowedHosts() throws Exception {
    List<String> allowedHosts =
        Arrays.asList("github.com", "gitlab.com", "bitbucket.org", "company.com");

    GitRepoArtifactCredentials credentials =
        new GitRepoArtifactCredentials(mockExecutor, mockFileSystem, allowedHosts);

    // Test each allowed host
    String[] validUrls = {
      "https://github.com/repo.git",
      "https://gitlab.com/repo.git",
      "https://bitbucket.org/repo.git",
      "https://github.com/example/customer-example.git",
      "git@github.com:org/example-helm-chart.git",
      "git@gitlab.com:project/with/folder/example-helm-chart.git",
      "https://gitlab.com/project/with/folder/example-agent.git",
      "https://janedoe:password@gitlab.com/project/repo.git",
      "https://github.com/repo.git",
      "https://gitlab.com/repo.git",
      "https://bitbucket.org/repo.git",
      "https://github.com/example/customer-example.git",
      "https://internal.company.com/repo.git", // allow subdomains ON the parent domain
      "https://company.com:3000/project/repo", // gitea where a .git extension is NOT needed
    };

    for (String url : validUrls) {
      validateHostVerificationByVerifyingALaterException(
          credentials, Artifact.builder().reference(url).version("main").build());
    }
  }

  @Test
  @DisplayName("Should reject host not in multi-host allowedHosts list")
  void shouldRejectHostNotInMultiHostList() {
    List<String> allowedHosts = Arrays.asList("github.com", "gitlab.com", "bitbucket.org");

    GitRepoArtifactCredentials credentials =
        new GitRepoArtifactCredentials(mockExecutor, mockFileSystem, allowedHosts);

    Artifact artifact =
        Artifact.builder().reference("https://not-allowed.com/repo.git").version("main").build();

    IllegalArgumentException exception =
        assertThrows(IllegalArgumentException.class, () -> credentials.download(artifact));

    assertThat(exception.getMessage()).containsIgnoringCase("host");
  }

  /*
  This is a TOUCH goofy that we throw an exception.  This is b/c the credentials mixes file handling in the code that
  does validation.  SO we verify WHICH exception is thrown by triggering a "lock failure" which happens AFTER
  host validation completes.
   */
  private void validateHostVerificationByVerifyingALaterException(
      GitRepoArtifactCredentials credentials, Artifact artifact)
      throws InterruptedException, IOException {
    // Mock the filesystem and executor behavior to avoid actual git operations
    when(mockFileSystem.tryTimedLock(anyString(), anyString())).thenReturn(false);
    when(mockFileSystem.getLocalClonePath(anyString(), anyString()))
        .thenReturn(Files.createTempDirectory("gitrepoartifacttests"));

    // This should throw IOException due to lock timeout, but NOT IllegalArgumentException for
    // host validation
    IOException exception = assertThrows(IOException.class, () -> credentials.download(artifact));

    assertThat(exception.getMessage())
        .contains("Timeout waiting to acquire file system lock")
        .doesNotContain("host");
  }
}
