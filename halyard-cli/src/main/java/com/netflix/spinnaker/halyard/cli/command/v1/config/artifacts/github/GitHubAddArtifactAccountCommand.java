/*
 * Copyright 2017 Joel Wilsson
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
 *
 *
 */

package com.netflix.spinnaker.halyard.cli.command.v1.config.artifacts.github;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.netflix.spinnaker.halyard.cli.command.v1.config.artifacts.account.AbstractAddArtifactAccountCommand;
import com.netflix.spinnaker.halyard.cli.command.v1.converter.LocalFileConverter;
import com.netflix.spinnaker.halyard.config.model.v1.artifacts.github.GitHubArtifactAccount;
import com.netflix.spinnaker.halyard.config.model.v1.node.ArtifactAccount;

@Parameters(separators = "=")
public class GitHubAddArtifactAccountCommand extends AbstractAddArtifactAccountCommand {
  @Parameter(names = "--username", description = "GitHub username")
  private String username;

  @Parameter(names = "--password", password = true, description = "GitHub password")
  private String password;

  @Parameter(
      names = "--username-password-file",
      converter = LocalFileConverter.class,
      description = "File containing \"username:password\" to use for GitHub authentication")
  private String usernamePasswordFile;

  @Parameter(names = "--token", password = true, description = "GitHub token")
  private String token;

  @Parameter(
      names = "--token-file",
      converter = LocalFileConverter.class,
      description = "File containing a GitHub authentication token")
  private String tokenFile;

  @Override
  protected ArtifactAccount buildArtifactAccount(String accountName) {
    GitHubArtifactAccount artifactAccount = new GitHubArtifactAccount().setName(accountName);
    artifactAccount
        .setUsername(username)
        .setPassword(password)
        .setUsernamePasswordFile(usernamePasswordFile)
        .setToken(token)
        .setTokenFile(tokenFile);
    return artifactAccount;
  }

  @Override
  protected ArtifactAccount emptyArtifactAccount() {
    return new GitHubArtifactAccount();
  }

  @Override
  protected String getArtifactProviderName() {
    return "github";
  }
}
