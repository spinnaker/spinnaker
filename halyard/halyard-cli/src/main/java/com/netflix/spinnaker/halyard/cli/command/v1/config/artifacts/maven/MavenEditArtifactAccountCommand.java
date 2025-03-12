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
import com.netflix.spinnaker.halyard.cli.command.v1.config.artifacts.account.AbstractArtifactEditAccountCommand;
import com.netflix.spinnaker.halyard.config.model.v1.artifacts.maven.MavenArtifactAccount;
import com.netflix.spinnaker.halyard.config.model.v1.node.ArtifactAccount;

@Parameters(separators = "=")
public class MavenEditArtifactAccountCommand
    extends AbstractArtifactEditAccountCommand<MavenArtifactAccount> {
  @Parameter(
      names = "--repository-url",
      description = MavenArtifactCommandProperties.REPOSITORY_URL_DESCRIPTION)
  private String repositoryUrl;

  @Override
  protected ArtifactAccount editArtifactAccount(MavenArtifactAccount account) {
    account.setRepositoryUrl(isSet(repositoryUrl) ? repositoryUrl : account.getRepositoryUrl());

    return account;
  }

  @Override
  protected String getArtifactProviderName() {
    return "maven";
  }
}
