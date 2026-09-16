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

package com.netflix.spinnaker.kork.github;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class GitHubRepoRefTest {

  @ParameterizedTest
  @CsvSource({
    // clone URLs
    "https://github.com/my-org/my-repo.git, my-org, my-repo",
    "https://github.com/my-org/my-repo, my-org, my-repo",
    "https://ghe.example.com/my-org/my-repo.git, my-org, my-repo",
    "https://user:pass@github.com/my-org/my-repo.git, my-org, my-repo",
    "https://github.com/my-org/my-repo/, my-org, my-repo",
    // contents API URLs
    "https://api.github.com/repos/my-org/my-repo/contents/manifest.yml, my-org, my-repo",
    "https://api.github.com/repos/my-org/my-repo, my-org, my-repo",
    "https://ghe.example.com/api/v3/repos/my-org/my-repo/contents/dir/manifest.yml, my-org, my-repo",
    // owner may be a user account rather than an organization
    "https://github.com/some-user/personal-repo.git, some-user, personal-repo"
  })
  void shouldParseUnambiguousUrls(String url, String expectedOwner, String expectedRepo) {
    Optional<GitHubRepoRef> ref = GitHubRepoRef.parse(url);

    assertTrue(ref.isPresent(), "expected " + url + " to be parsed");
    assertEquals(expectedOwner, ref.get().getOwner());
    assertEquals(expectedRepo, ref.get().getRepo());
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        // raw content download URLs: layout varies between github.com and GitHub Enterprise, so
        // the owner cannot be derived with confidence
        "https://raw.githubusercontent.com/my-org/my-repo/main/manifest.yml",
        "https://ghe.example.com/raw/my-org/my-repo/main/manifest.yml",
        // not enough information
        "https://github.com/my-org",
        "https://github.com/",
        "https://github.com"
      })
  void shouldNotParseAmbiguousUrls(String url) {
    assertTrue(GitHubRepoRef.parse(url).isEmpty(), "expected " + url + " to be unparseable");
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "   ", "not a url"})
  void shouldNotParseBlankOrMalformedUrls(String url) {
    assertTrue(GitHubRepoRef.parse(url).isEmpty());
  }

  @Test
  void shouldNotParseNullUrl() {
    assertTrue(GitHubRepoRef.parse(null).isEmpty());
  }

  @Test
  void shouldParseFromPathSegmentsIgnoringEmptySegments() {
    Optional<GitHubRepoRef> ref =
        GitHubRepoRef.fromPathSegments(List.of("", "repos", "my-org", "my-repo", "contents", ""));

    assertTrue(ref.isPresent());
    assertEquals("my-org", ref.get().getOwner());
    assertEquals("my-repo", ref.get().getRepo());
  }

  @Test
  void shouldRenderFullNameAsOwnerSlashRepo() {
    assertEquals(
        "my-org/my-repo",
        GitHubRepoRef.parse("https://github.com/my-org/my-repo.git").get().fullName());
  }
}
