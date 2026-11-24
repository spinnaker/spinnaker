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

import com.netflix.spinnaker.kork.github.GitHubAppAuthenticator;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;

/**
 * Supplies GitHub App installation tokens. With a pinned installation every repository uses that
 * installation's token; otherwise the installation is derived from the repository owner.
 */
@Slf4j
public class GithubAppTokenProvider implements GithubTokenProvider {

  private final GitHubAppAuthenticator authenticator;
  private final boolean pinnedInstallation;

  /**
   * @param authenticator mints and caches installation tokens
   * @param pinnedInstallation whether the authenticator was created with an installation ID
   */
  public GithubAppTokenProvider(GitHubAppAuthenticator authenticator, boolean pinnedInstallation) {
    this.authenticator = authenticator;
    this.pinnedInstallation = pinnedInstallation;
  }

  @Override
  public String getToken(String repo) throws IOException {
    if (pinnedInstallation) {
      return authenticator.getInstallationToken();
    }
    String[] ownerAndName = repo == null ? new String[0] : repo.split("/", -1);
    if (ownerAndName.length != 2 || ownerAndName[0].isEmpty() || ownerAndName[1].isEmpty()) {
      throw new IllegalArgumentException(
          "Unable to derive the GitHub App installation for repository '"
              + repo
              + "': expected 'owner/name'. Set github-status.github-app.app-installation-id to"
              + " pin an installation.");
    }
    return authenticator.getInstallationTokenForRepo(ownerAndName[0], ownerAndName[1]);
  }

  @Override
  public void invalidate(String repo) {
    if (pinnedInstallation) {
      authenticator.invalidateInstallationToken();
    } else {
      // TODO: drop the owner's cached token once kork's GitHubAppAuthenticator can invalidate
      // derived installation tokens; until then it is replaced when it nears expiry.
      log.debug(
          "GitHub rejected the GitHub App installation token for repository '{}'; derived"
              + " installation tokens cannot be invalidated individually",
          repo);
    }
  }
}
