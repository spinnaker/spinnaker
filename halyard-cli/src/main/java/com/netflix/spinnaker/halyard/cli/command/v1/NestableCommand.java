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
import com.beust.jcommander.ParameterDescription;
import com.beust.jcommander.Parameters;
import com.netflix.spinnaker.halyard.cli.ui.v1.*;
import com.netflix.spinnaker.halyard.config.model.v1.problem.Problem;
import com.netflix.spinnaker.halyard.config.model.v1.problem.ProblemSet;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang.StringUtils;
import retrofit.RetrofitError;

import java.net.ConnectException;
import java.util.Map;

import static com.netflix.spinnaker.halyard.config.model.v1.problem.Problem.Severity;

@Parameters(separators = "=")
public abstract class NestableCommand {
  @Setter
  @Getter(AccessLevel.PROTECTED)
  private JCommander commander;

  @Parameter(names = { "-h", "--help" }, description = "Display help text about this command")
  public boolean help;

  @Parameter(names = {"-d", "--debug"}, description = "Show detailed network traffic with halyard daemon")
  public void setDebug(boolean debug) {
    GlobalOptions.getGlobalOptions().setDebug(debug);
  }

  @Parameter(names = { "-c", "--color" }, description = "Enable terminal color output", arity = 1)
  public void setColor(boolean color) {
    GlobalOptions.getGlobalOptions().setColor(color);
  }

  private String fullCommandName = "";

  /**
   * This recursively walks the chain of subcommands, until it finds the last in the chain, and runs executeThis.
   *
   * @see NestableCommand#executeThis()
   */
  public void execute() {
    String subCommand = commander.getParsedCommand();
    if (subCommand == null) {
      if (help) {
        showHelp();
      } else {
        safeExecuteThis();
      }
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
    } catch (Exception e) {
      if (GlobalOptions.getGlobalOptions().isDebug()) {
        throw e;
      } else {
        AnsiUi.error(e.getMessage());
      }
    }
  }

  public void showHelp() {
    AnsiStoryBuilder story = new AnsiStoryBuilder();
    int indentWidth = 2;

    AnsiParagraphBuilder paragraph = story.addParagraph();
    paragraph.addSnippet(getCommandName().toUpperCase()).addStyle(AnsiStyle.BOLD);
    story.addNewline();

    paragraph = story.addParagraph().setIndentWidth(indentWidth);
    paragraph.addSnippet(getDescription());
    story.addNewline();

    String usage = fullCommandName;

    if (!commander.getParameters().isEmpty()) {
      usage += " [parameters]";
    }

    if (!getSubcommands().isEmpty()) {
      usage += " [subcommands]";
    }


    paragraph = story.addParagraph();
    paragraph.addSnippet("USAGE").addStyle(AnsiStyle.BOLD);
    story.addNewline();

    paragraph = story.addParagraph().setIndentWidth(indentWidth);
    paragraph.addSnippet(usage);
    story.addNewline();

    if (!commander.getParameters().isEmpty()) {
      paragraph = story.addParagraph();
      paragraph.addSnippet("PARAMETERS").addStyle(AnsiStyle.BOLD);
      story.addNewline();

      for (ParameterDescription parameter : commander.getParameters()) {
        paragraph = story.addParagraph().setIndentWidth(indentWidth);
        paragraph.addSnippet(parameter.getNames()).addStyle(AnsiStyle.BOLD);

        if (parameter.getDefault() != null) {
          paragraph.addSnippet("=");
          paragraph.addSnippet(parameter.getDefault().toString());
        }

        paragraph = story.addParagraph().setIndentWidth(indentWidth * 2);
        paragraph.addSnippet(parameter.getDescription());
        story.addNewline();
      }
    }

    if (!getSubcommands().isEmpty()) {
      int maxLen = -1;
      for (String key : getSubcommands().keySet()) {
        if (key.length() > maxLen) {
          maxLen = key.length();
        }
      }

      paragraph = story.addParagraph();
      paragraph.addSnippet("SUBCOMMANDS").addStyle(AnsiStyle.BOLD);
      story.addNewline();

      for (String key : getSubcommands().keySet()) {
        paragraph = story.addParagraph().setIndentWidth(indentWidth);
        paragraph.addSnippet(key).addStyle(AnsiStyle.BOLD);

        paragraph = story.addParagraph().setIndentWidth(indentWidth * 2);
        paragraph.addSnippet(getSubcommands().get(key).getDescription());
        story.addNewline();
      }
    }

    AnsiPrinter.println(story.toString());
  }

  abstract public String getDescription();
  abstract public String getCommandName();
  abstract protected Map<String, NestableCommand> getSubcommands();
  abstract protected void executeThis();

  /**
   * Register all subcommands with this class's commander, and then recursively set the subcommands, configuring their
   * command names along the way.
   */
  public void configureSubcommands() {
    if (fullCommandName.isEmpty()) {
      fullCommandName = getCommandName();
    }

    for (NestableCommand subCommand: getSubcommands().values()) {
      subCommand.fullCommandName = fullCommandName + " " + subCommand.getCommandName();

      commander.addCommand(subCommand.getCommandName(), subCommand);

      // We need to provide the subcommand with its own commander before recursively populating its subcommands, since
      // they need to be registered with this subcommander we retrieve here.
      JCommander subCommander = commander.getCommands().get(subCommand.getCommandName());
      subCommand.setCommander(subCommander);
      subCommand.configureSubcommands();
    }
  }
}
