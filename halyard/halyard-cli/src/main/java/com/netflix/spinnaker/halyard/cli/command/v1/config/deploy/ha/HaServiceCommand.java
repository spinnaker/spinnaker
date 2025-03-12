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

package com.netflix.spinnaker.halyard.cli.command.v1.config.deploy.ha;

import com.beust.jcommander.Parameters;
import com.netflix.spinnaker.halyard.cli.command.v1.NestableCommand;
import lombok.AccessLevel;
import lombok.Getter;

@Parameters(separators = "=")
public class HaServiceCommand extends NestableCommand {
  @Getter(AccessLevel.PUBLIC)
  private String commandName = "ha";

  @Getter(AccessLevel.PUBLIC)
  private String shortDescription =
      "Configure, validate, and view the specified high availability Spinnaker service configuration.";

  public HaServiceCommand() {
    registerSubcommand(new ClouddriverHaServiceCommand());
    registerSubcommand(new EchoHaServiceCommand());
  }

  @Override
  protected void executeThis() {
    showHelp();
  }
}
