/*
 * Copyright 2018 Google, Inc.
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

package com.netflix.spinnaker.halyard.cli.command.v1.config.artifacts.gitlab;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.netflix.spinnaker.halyard.cli.command.v1.config.artifacts.account.AbstractAddArtifactAccountCommand;
import com.netflix.spinnaker.halyard.cli.command.v1.converter.LocalFileConverter;
import com.netflix.spinnaker.halyard.config.model.v1.artifacts.gitlab.GitlabArtifactAccount;
import com.netflix.spinnaker.halyard.config.model.v1.node.ArtifactAccount;

@Parameters(separators = "=")
public class GitlabAddArtifactAccountCommand extends AbstractAddArtifactAccountCommand {
  @Parameter(names = "--token", password = true, description = "Gitlab token")
  private String token;

  @Parameter(
      names = "--token-file",
      converter = LocalFileConverter.class,
      description = "File containing a Gitlab authentication token")
  private String tokenFile;

  @Override
  protected ArtifactAccount buildArtifactAccount(String accountName) {
    GitlabArtifactAccount artifactAccount = new GitlabArtifactAccount().setName(accountName);
    artifactAccount.setToken(token).setTokenFile(tokenFile);
    return artifactAccount;
  }

  @Override
  protected ArtifactAccount emptyArtifactAccount() {
    return new GitlabArtifactAccount();
  }

  @Override
  protected String getArtifactProviderName() {
    return "gitlab";
  }
}
