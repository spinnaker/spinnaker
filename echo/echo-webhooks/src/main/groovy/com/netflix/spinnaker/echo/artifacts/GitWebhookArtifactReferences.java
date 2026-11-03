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

package com.netflix.spinnaker.echo.artifacts;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.util.UriUtils;

/**
 * Checks for the values the git artifact extractors copy out of a webhook payload into an artifact
 * reference. Clouddriver later fetches that reference, with an account's credentials, so only the
 * shapes the git hosts actually send are accepted.
 *
 * <p>These checks constrain the <em>shape</em> of the reference (scheme, no user info, no query,
 * repository and file path segments). They can not tell whether the host in the payload is the host
 * the operator means: for that, the trigger needs a secret and the artifact account needs {@code
 * urlRestrictions.allowedDomains}.
 */
@Slf4j
final class GitWebhookArtifactReferences {
  private GitWebhookArtifactReferences() {}

  /** An {@code owner/repository} name; owner and repository names use alphanumerics, -, _ and . */
  static final Pattern OWNER_AND_REPOSITORY = Pattern.compile("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+");

  /** A GitLab {@code group[/subgroup...]/project} path. */
  static final Pattern GROUPS_AND_PROJECT = Pattern.compile("[A-Za-z0-9_.-]+(/[A-Za-z0-9_.-]+)+");

  /**
   * True for an {@code http} or {@code https} URL with a host and without user info, query or
   * fragment.
   */
  static boolean isPlainHttpUrl(String url) {
    try {
      URI uri = new URI(url);
      return ("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
          && uri.getHost() != null
          && uri.getRawUserInfo() == null
          && uri.getRawQuery() == null
          && uri.getRawFragment() == null;
    } catch (URISyntaxException e) {
      return false;
    }
  }

  static boolean hasDotSegment(String path) {
    return Arrays.stream(path.split("/", -1))
        .anyMatch(segment -> segment.isEmpty() || segment.equals(".") || segment.equals(".."));
  }

  /**
   * Git cannot store a path with an empty, {@code .} or {@code ..} segment, so such a path is never
   * legitimate and would be rewritten when the URL is normalized.
   */
  static boolean isSafeFilePath(String path) {
    if (path == null || path.isEmpty() || hasDotSegment(path)) {
      log.warn("Ignoring file path with an empty, '.' or '..' segment");
      return false;
    }
    return true;
  }

  /** Percent-encodes each segment of a file path, keeping the {@code /} separators. */
  static String encodePath(String path) {
    return Arrays.stream(path.split("/"))
        .map(segment -> UriUtils.encodePathSegment(segment, StandardCharsets.UTF_8))
        .collect(Collectors.joining("/"));
  }
}
