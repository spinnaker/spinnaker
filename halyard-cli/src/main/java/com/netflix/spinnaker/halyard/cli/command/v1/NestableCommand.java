/*
 * Copyright 2016 Google, Inc.
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

package com.netflix.spinnaker.halyard.cli.command.v1;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.netflix.spinnaker.halyard.cli.ui.v1.AnsiUi;
import com.netflix.spinnaker.halyard.config.model.v1.node.NodeReference;
import com.netflix.spinnaker.halyard.config.model.v1.problem.Problem;
import com.netflix.spinnaker.halyard.config.model.v1.problem.ProblemSet;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import retrofit.RetrofitError;

import java.net.ConnectException;
import java.util.Map;

import static com.netflix.spinnaker.halyard.config.model.v1.problem.Problem.Severity;

abstract class NestableCommand {
  @Setter
  @Getter(AccessLevel.PROTECTED)
  private JCommander commander;

  @Parameter(names = { "-h", "--help" }, help = true)
  private boolean help;

  NestableCommand() {
    boolean color = GlobalOptions.getGlobalOptions().isColor();
  }

  /**
   * This recursively walks the chain of subcommands, until it finds the last in the chain, and runs executeThis.
   *
   * @see NestableCommand#executeThis()
   */
  public void execute() {
    String subCommand = commander.getParsedCommand();
    if (subCommand == null) {
      safeExecuteThis();
    } else {
      getSubcommands().get(subCommand).execute();
    }
  }

  /**
   * Used to consistently format exceptions thrown by connecting to the halyard daemon.
   */
  private void safeExecuteThis() {
    try {
      executeThis();
    } catch (RetrofitError e) {
      if (e.getCause() instanceof ConnectException) {
        AnsiUi.error(e.getCause().getMessage());
        AnsiUi.remediation("Is your daemon running?");
      } else {
        ProblemSet problemSet = (ProblemSet) e.getBodyAs(ProblemSet.class);

        problemSet.sortIncreasingSeverity();
        for (Problem problem : problemSet.getProblems()) {
          Severity severity = problem.getSeverity();
          String problemLocation = problem.getReferenceTitle();
          String message = problem.getMessage();
          String remediation = problem.getRemediation();

          switch(severity) {
            case FATAL:
            case ERROR:
              AnsiUi.error(problemLocation);
              AnsiUi.error(message);
              break;
            case WARNING:
              AnsiUi.warning(problemLocation);
              AnsiUi.warning(message);
              break;
            default:
              throw new RuntimeException("Unknown severity level " + severity);
          }

          if (!remediation.isEmpty()) {
            AnsiUi.remediation(remediation);
          }

          // Newline between errors
          AnsiUi.raw("");
        }
      }
    }
  }

  abstract protected Map<String, NestableCommand> getSubcommands();
  abstract protected String getCommandName();
  abstract protected void executeThis();

  /**
   * Register all subcommands with this class's commander, and then recursively set the subcommands.
   */
  void configureSubcommands() {
    for (NestableCommand subCommand: getSubcommands().values()) {
      commander.addCommand(subCommand.getCommandName(), subCommand);
      // We need to provide the subcommand with its own commander before recursively populating its subcommands, since
      // they need to be registered with this subcommander we retrieve here.
      JCommander subCommander = commander.getCommands().get(subCommand.getCommandName());
      subCommand.setCommander(subCommander);
      subCommand.configureSubcommands();
    }
  }
}
