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

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nullable;
import lombok.Value;
import org.springframework.util.StringUtils;

/**
 * An owner (organization or user) and repository name pair, parsed from a GitHub URL.
 *
 * <p>Used to resolve which GitHub App installation should serve a request. Parsing is intentionally
 * conservative: only URL shapes where the owner and repository are unambiguous are recognized, so
 * that callers never authenticate against a wrongly-derived owner.
 *
 * <p>Recognized shapes:
 *
 * <ul>
 *   <li>API URLs containing a {@code repos} segment, e.g. {@code
 *       https://api.github.com/repos/<owner>/<repo>/contents/manifest.yml} or {@code
 *       https://ghe.example.com/api/v3/repos/<owner>/<repo>}
 *   <li>Clone URLs whose path is exactly the owner and repository, e.g. {@code
 *       https://github.com/<owner>/<repo>.git}
 * </ul>
 *
 * <p>Anything else (e.g. raw content download URLs, whose layout varies between github.com and
 * GitHub Enterprise) yields an empty result.
 */
@Value
public class GitHubRepoRef {

  private static final String REPOS_SEGMENT = "repos";
  private static final String GIT_SUFFIX = ".git";

  String owner;
  String repo;

  /**
   * Parses an owner/repository pair from a URL.
   *
   * @param url the URL to parse; may be null
   * @return the parsed reference, or empty if the URL does not unambiguously identify a repository
   */
  public static Optional<GitHubRepoRef> parse(@Nullable String url) {
    if (!StringUtils.hasText(url)) {
      return Optional.empty();
    }
    String path;
    try {
      path = new URI(url).getPath();
    } catch (URISyntaxException e) {
      return Optional.empty();
    }
    if (path == null) {
      return Optional.empty();
    }
    return fromPathSegments(List.of(path.split("/")));
  }

  /**
   * Parses an owner/repository pair from the path segments of a URL.
   *
   * @param pathSegments the URL path segments; empty segments are ignored
   * @return the parsed reference, or empty if the segments do not unambiguously identify a
   *     repository
   */
  public static Optional<GitHubRepoRef> fromPathSegments(List<String> pathSegments) {
    List<String> segments = new ArrayList<>();
    for (String segment : pathSegments) {
      if (!segment.isEmpty()) {
        segments.add(segment);
      }
    }

    // API shape: .../repos/<owner>/<repo>/...
    for (int i = 0; i < segments.size() - 2; i++) {
      if (REPOS_SEGMENT.equals(segments.get(i))) {
        return repoRef(segments.get(i + 1), segments.get(i + 2));
      }
    }

    // Clone URL shape: /<owner>/<repo>(.git)
    if (segments.size() == 2) {
      return repoRef(segments.get(0), segments.get(1));
    }

    return Optional.empty();
  }

  private static Optional<GitHubRepoRef> repoRef(String owner, String repo) {
    String name =
        repo.endsWith(GIT_SUFFIX) ? repo.substring(0, repo.length() - GIT_SUFFIX.length()) : repo;
    if (owner.isEmpty() || name.isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(new GitHubRepoRef(owner, name));
  }

  /**
   * @return the {@code owner/repo} form used by GitHub, suitable for log and error messages
   */
  public String fullName() {
    return owner + "/" + repo;
  }
}
