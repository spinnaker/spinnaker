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

package com.netflix.spinnaker.halyard.cli.command.v1;

import com.beust.jcommander.Parameters;
import com.netflix.spinnaker.halyard.cli.command.v1.task.InterruptTaskCommand;
import com.netflix.spinnaker.halyard.cli.command.v1.task.ListTaskCommand;
import lombok.AccessLevel;
import lombok.Getter;

@Parameters(separators = "=")
public class TaskCommand extends NestableCommand {
  @Getter(AccessLevel.PUBLIC)
  private String commandName = "task";

  @Getter(AccessLevel.PUBLIC)
  private String shortDescription =
      "This set of commands exposes utilities of dealing with Halyard's task engine.";

  @Getter(AccessLevel.PUBLIC)
  private String longDescription =
      String.join(
          " ",
          "Every unit of work Halyard carries out is bundled in a Task. This set ",
          "of commands exposes some information about these tasks. The commands here are mainly for troubleshooting.");

  public TaskCommand() {
    registerSubcommand(new InterruptTaskCommand());
    registerSubcommand(new ListTaskCommand());
  }

  @Override
  protected void executeThis() {
    showHelp();
  }
}
