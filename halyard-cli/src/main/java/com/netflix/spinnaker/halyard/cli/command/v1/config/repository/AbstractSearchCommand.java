/*
 * Copyright 2019 Pivotal, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.halyard.cli.command.v1.config.repository;

import com.beust.jcommander.Parameters;
import com.netflix.spinnaker.halyard.cli.command.v1.config.repository.search.AbstractHasSearchCommand;
import com.netflix.spinnaker.halyard.cli.command.v1.config.repository.search.DeleteSearchCommandBuilder;
import com.netflix.spinnaker.halyard.cli.command.v1.config.repository.search.GetSearchCommandBuilder;
import com.netflix.spinnaker.halyard.cli.command.v1.config.repository.search.ListSearchesCommandBuilder;

@Parameters(separators = "=")
public abstract class AbstractSearchCommand extends AbstractHasSearchCommand {
  @Override
  public String getCommandName() {
    return "search";
  }

  @Override
  public String getShortDescription() {
    return "Manage and view Spinnaker configuration for the "
        + getRepositoryName()
        + " repository services's search";
  }

  protected AbstractSearchCommand() {
    registerSubcommand(
        new DeleteSearchCommandBuilder().setRepositoryName(getRepositoryName()).build());

    registerSubcommand(
        new GetSearchCommandBuilder().setRepositoryName(getRepositoryName()).build());

    registerSubcommand(
        new ListSearchesCommandBuilder().setRepositoryName(getRepositoryName()).build());
  }

  @Override
  protected void executeThis() {
    showHelp();
  }
}
