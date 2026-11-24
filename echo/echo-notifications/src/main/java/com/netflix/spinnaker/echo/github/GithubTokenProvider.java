/*
 * Copyright 2026 Red Bull Media House GmbH
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

package com.netflix.spinnaker.echo.github;

import java.io.IOException;

/** Supplies the token that authenticates GitHub commit status requests for a repository. */
public interface GithubTokenProvider {

  /**
   * @param repo the repository, as {@code owner/name}
   * @return the token to send in the {@code Authorization} header
   * @throws IOException if the token cannot be obtained
   * @throws IllegalArgumentException if no token can be issued for the repository
   */
  String getToken(String repo) throws IOException;

  /**
   * Called when GitHub rejected the token for a repository with 401 Unauthorized, so that a cached
   * token can be dropped and the next request uses a new one.
   *
   * @param repo the repository, as {@code owner/name}
   */
  default void invalidate(String repo) {}

  /**
   * @param token a personal access token
   * @return a provider that always returns the given token
   */
  static GithubTokenProvider personalAccessToken(String token) {
    return repo -> token;
  }
}
