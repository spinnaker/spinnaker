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
import com.netflix.spinnaker.halyard.cli.services.v1.ResponseUnwrapper;
import com.netflix.spinnaker.halyard.cli.ui.v1.*;
import com.netflix.spinnaker.halyard.core.DaemonResponse;
import com.netflix.spinnaker.halyard.core.resource.v1.JarResource;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import retrofit.RetrofitError;

import java.net.ConnectException;
import java.util.*;

@Parameters(separators = "=")
public abstract class NestableCommand {
  @Setter
  @Getter(AccessLevel.PROTECTED)
  private JCommander commander;

  @Parameter(names = { "-h", "--help" }, help = true, description = "Display help text about this command.")
  private boolean help;

  @Parameter(names = {"-d", "--debug"}, description = "Show detailed network traffic with halyard daemon.")
  public void setDebug(boolean debug) {
    GlobalOptions.getGlobalOptions().setDebug(debug);
  }

  @Parameter(names = { "-c", "--color" }, description = "Enable terminal color output.", arity = 1)
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
      subcommands.get(subCommand).execute();
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
        System.exit(1);
      }

      try {
        DaemonResponse d = (DaemonResponse) e.getBodyAs(DaemonResponse.class);
        if (d.getProblemSet() != null) {
          ResponseUnwrapper.get(d);
          System.exit(1);
        }
      } catch (RuntimeException re) {
      }
      AnsiUi.error(e.getMessage());
      AnsiUi.remediation("Try the command again with the --debug flag.");
      System.exit(1);
    } catch (Exception e) {
      if (GlobalOptions.getGlobalOptions().isDebug()) {
        e.printStackTrace();
      } else {
        AnsiUi.error(e.getMessage());
      }
      System.exit(1);
    }
  }

  protected void showHelp() {
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

    if (!subcommands.isEmpty()) {
      usage += " [subcommands]";
    }


    paragraph = story.addParagraph();
    paragraph.addSnippet("USAGE").addStyle(AnsiStyle.BOLD);
    story.addNewline();

    paragraph = story.addParagraph().setIndentWidth(indentWidth);
    paragraph.addSnippet(usage);
    story.addNewline();

    List<ParameterDescription> parameters = commander.getParameters();
    parameters.sort(Comparator.comparing(ParameterDescription::getNames));

    int parameterCount = 0;

    if (!parameters.isEmpty()) {
      paragraph = story.addParagraph();
      paragraph.addSnippet("GLOBAL PARAMETERS").addStyle(AnsiStyle.BOLD);
      story.addNewline();

      for (ParameterDescription parameter : parameters) {
        if (GlobalOptions.isGlobalOption(parameter.getLongestName())) {
          formatParameter(story, parameter, indentWidth);
          parameterCount++;
        }
      }
    }

    if (parameters.size() > parameterCount) {
      paragraph = story.addParagraph();
      paragraph.addSnippet("PARAMETERS").addStyle(AnsiStyle.BOLD);
      story.addNewline();

      ParameterDescription mainParameter = commander.getMainParameter();
      if (mainParameter != null) {
        paragraph = story.addParagraph().setIndentWidth(indentWidth);
        paragraph.addSnippet(getMainParameter().toUpperCase()).addStyle(AnsiStyle.UNDERLINE);

        paragraph = story.addParagraph().setIndentWidth(indentWidth * 2);
        paragraph.addSnippet(mainParameter.getDescription());
        story.addNewline();
      }

      for (ParameterDescription parameter : parameters) {
        if (!GlobalOptions.isGlobalOption(parameter.getLongestName())) {
          formatParameter(story, parameter, indentWidth);
        }
      }
    }

    if (!subcommands.isEmpty()) {
      int maxLen = -1;
      for (String key : subcommands.keySet()) {
        if (key.length() > maxLen) {
          maxLen = key.length();
        }
      }

      paragraph = story.addParagraph();
      paragraph.addSnippet("SUBCOMMANDS").addStyle(AnsiStyle.BOLD);
      story.addNewline();

      List<String> keys = new ArrayList<>(subcommands.keySet());
      keys.sort(String::compareTo);

      for (String key : keys) {
        paragraph = story.addParagraph().setIndentWidth(indentWidth);
        paragraph.addSnippet(key).addStyle(AnsiStyle.BOLD);

        paragraph = story.addParagraph().setIndentWidth(indentWidth * 2);
        paragraph.addSnippet(subcommands.get(key).getDescription());
        story.addNewline();
      }
    }

    AnsiPrinter.println(story.toString());
  }

  private static void formatParameter(AnsiStoryBuilder story, ParameterDescription parameter, int indentWidth) {
    AnsiParagraphBuilder paragraph = story.addParagraph().setIndentWidth(indentWidth);
    paragraph.addSnippet(parameter.getNames()).addStyle(AnsiStyle.BOLD);

    if (parameter.getDefault() != null) {
      paragraph.addSnippet("=");
      paragraph.addSnippet(parameter.getDefault().toString()).addStyle(AnsiStyle.UNDERLINE);
    }

    if (parameter.getParameter().required()) {
      paragraph.addSnippet(" (required)");
    }

    if (parameter.getParameter().password()) {
      paragraph.addSnippet(" (sensitive data - user will be prompted)");
    }

    paragraph = story.addParagraph().setIndentWidth(indentWidth * 2);
    paragraph.addSnippet(parameter.getDescription());
    story.addNewline();
  }

  public String commandCompletor() {
    JarResource completorBody = new JarResource("/hal-completor-body");
    Map<String, String> bindings = new HashMap<>();

    String body = aggregateCompletorCases();
    bindings.put("body", body);

    return completorBody.setBindings(bindings).toString();
  }

  private String aggregateCompletorCases() {
    String result = commandCompletorCase();

    return subcommands.entrySet()
        .stream()
        .map(c -> c.getValue().aggregateCompletorCases())
        .reduce(result, (a, b) -> a + b);
  }

  private String commandCompletorCase() {
    JarResource completorCase = new JarResource("/hal-completor-case");
    Map<String, String> bindings = new HashMap<>();
    String flagNames = commander.getParameters()
        .stream()
        .map(ParameterDescription::getLongestName)
        .reduce("", (a, b) -> a + " " + b);

    String subcommandNames = subcommands.entrySet()
        .stream()
        .map(Map.Entry::getKey)
        .reduce("", (a, b) -> a + " " + b);

    bindings.put("subcommands", subcommandNames);
    bindings.put("flags", flagNames);
    bindings.put("command", getCommandName());

    return completorCase.setBindings(bindings).toString();
  }

  abstract public String getDescription();
  abstract public String getCommandName();
  abstract protected void executeThis();

  private Map<String, NestableCommand> subcommands = new HashMap<>();

  protected void registerSubcommand(NestableCommand subcommand) {
    String subcommandName = subcommand.getCommandName();
    if (subcommands.containsKey(subcommandName)) {
      throw new RuntimeException("Unable to register duplicate subcommand " + subcommandName + " for command " + getCommandName());
    }
    subcommands.put(subcommandName, subcommand);
  }

  /**
   * Register all subcommands with this class's commander, and then recursively set the subcommands, configuring their
   * command names along the way.
   */
  public void configureSubcommands() {
    if (fullCommandName.isEmpty()) {
      fullCommandName = getCommandName();
    }

    for (NestableCommand subCommand: subcommands.values()) {
      subCommand.fullCommandName = fullCommandName + " " + subCommand.getCommandName();

      commander.addCommand(subCommand.getCommandName(), subCommand);

      // We need to provide the subcommand with its own commander before recursively populating its subcommands, since
      // they need to be registered with this subcommander we retrieve here.
      JCommander subCommander = commander.getCommands().get(subCommand.getCommandName());
      subCommand.setCommander(subCommander);
      subCommand.configureSubcommands();
    }
  }

  public String getMainParameter() {
    throw new RuntimeException("This command has no main-command.");
  }
}
