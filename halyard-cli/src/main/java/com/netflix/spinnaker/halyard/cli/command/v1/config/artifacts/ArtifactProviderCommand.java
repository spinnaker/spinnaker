/*
 * Copyright 2017 Google, Inc.
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

package com.netflix.spinnaker.halyard.cli.command.v1.config.artifacts;

import com.beust.jcommander.Parameters;
import com.netflix.spinnaker.halyard.cli.command.v1.NestableCommand;
import com.netflix.spinnaker.halyard.cli.command.v1.config.artifacts.bitbucket.BitbucketArtifactProviderCommand;
import com.netflix.spinnaker.halyard.cli.command.v1.config.artifacts.gcs.GcsArtifactProviderCommand;
import com.netflix.spinnaker.halyard.cli.command.v1.config.artifacts.github.GitHubArtifactProviderCommand;
import com.netflix.spinnaker.halyard.cli.command.v1.config.artifacts.gitlab.GitlabArtifactProviderCommand;
import com.netflix.spinnaker.halyard.cli.command.v1.config.artifacts.gitrepo.GitRepoArtifactProviderCommand;
import com.netflix.spinnaker.halyard.cli.command.v1.config.artifacts.helm.HelmArtifactProviderCommand;
import com.netflix.spinnaker.halyard.cli.command.v1.config.artifacts.http.HttpArtifactProviderCommand;
import com.netflix.spinnaker.halyard.cli.command.v1.config.artifacts.maven.MavenArtifactProviderCommand;
import com.netflix.spinnaker.halyard.cli.command.v1.config.artifacts.oracle.OracleArtifactProviderCommand;
import com.netflix.spinnaker.halyard.cli.command.v1.config.artifacts.s3.S3ArtifactProviderCommand;
import com.netflix.spinnaker.halyard.cli.command.v1.config.artifacts.templates.ArtifactTemplateCommand;
import lombok.AccessLevel;
import lombok.Getter;

/**
 * This is a top-level command for dealing with your halconfig.
 *
 * <p>Usage is `$ hal config artifact`
 */
@Parameters(separators = "=")
public class ArtifactProviderCommand extends NestableCommand {
  @Getter(AccessLevel.PUBLIC)
  private String commandName = "artifact";

  @Getter(AccessLevel.PUBLIC)
  private String shortDescription =
      "Configure, validate, and view the specified artifact provider.";

  public ArtifactProviderCommand() {
    registerSubcommand(new BitbucketArtifactProviderCommand());
    registerSubcommand(new GcsArtifactProviderCommand());
    registerSubcommand(new OracleArtifactProviderCommand());
    registerSubcommand(new GitHubArtifactProviderCommand());
    registerSubcommand(new GitlabArtifactProviderCommand());
    registerSubcommand(new GitRepoArtifactProviderCommand());
    registerSubcommand(new HttpArtifactProviderCommand());
    registerSubcommand(new HelmArtifactProviderCommand());
    registerSubcommand(new S3ArtifactProviderCommand());
    registerSubcommand(new MavenArtifactProviderCommand());
    registerSubcommand((new ArtifactTemplateCommand()));
  }

  @Override
  protected void executeThis() {
    showHelp();
  }
}
