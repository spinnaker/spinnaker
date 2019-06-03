/*
 * Copyright 2019 Pivotal, Inc.
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

package com.netflix.spinnaker.halyard.cli.command.v1.config.artifacts.maven;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.netflix.spinnaker.halyard.cli.command.v1.config.artifacts.account.AbstractAddArtifactAccountCommand;
import com.netflix.spinnaker.halyard.config.model.v1.artifacts.maven.MavenArtifactAccount;
import com.netflix.spinnaker.halyard.config.model.v1.node.ArtifactAccount;

@Parameters(separators = "=")
public class MavenAddArtifactAccountCommand extends AbstractAddArtifactAccountCommand {
  @Parameter(
      names = "--repository-url",
      required = true,
      description = MavenArtifactCommandProperties.REPOSITORY_URL_DESCRIPTION)
  private String repositoryUrl;

  @Override
  protected ArtifactAccount buildArtifactAccount(String accountName) {
    return new MavenArtifactAccount().setName(accountName).setRepositoryUrl(repositoryUrl);
  }

  @Override
  protected ArtifactAccount emptyArtifactAccount() {
    return new MavenArtifactAccount();
  }

  @Override
  protected String getArtifactProviderName() {
    return "maven";
  }
}
