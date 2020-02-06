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
 */

package com.netflix.spinnaker.halyard.cli.command.v1.config.ci;

import com.beust.jcommander.Parameters;
import com.netflix.spinnaker.halyard.cli.command.v1.NestableCommand;
import com.netflix.spinnaker.halyard.cli.command.v1.config.ci.codebuild.AwsCodeBuildCommand;
import com.netflix.spinnaker.halyard.cli.command.v1.config.ci.concourse.ConcourseCommand;
import com.netflix.spinnaker.halyard.cli.command.v1.config.ci.gcb.GoogleCloudBuildCommand;
import com.netflix.spinnaker.halyard.cli.command.v1.config.ci.jenkins.JenkinsCommand;
import com.netflix.spinnaker.halyard.cli.command.v1.config.ci.travis.TravisCommand;
import com.netflix.spinnaker.halyard.cli.command.v1.config.ci.wercker.WerckerCommand;
import lombok.AccessLevel;
import lombok.Getter;

/**
 * This is a top-level command for dealing with your halconfig.
 *
 * <p>Usage is `$ hal config ci`
 */
@Parameters(separators = "=")
public class CiCommand extends NestableCommand {
  @Getter(AccessLevel.PUBLIC)
  private String commandName = "ci";

  @Getter(AccessLevel.PUBLIC)
  private String shortDescription =
      "Configure, validate, and view the specified Continuous Integration service.";

  public CiCommand() {
    registerSubcommand(new AwsCodeBuildCommand());
    registerSubcommand(new ConcourseCommand());
    registerSubcommand(new GoogleCloudBuildCommand());
    registerSubcommand(new JenkinsCommand());
    registerSubcommand(new TravisCommand());
    registerSubcommand(new WerckerCommand());
  }

  @Override
  protected void executeThis() {
    showHelp();
  }
}
