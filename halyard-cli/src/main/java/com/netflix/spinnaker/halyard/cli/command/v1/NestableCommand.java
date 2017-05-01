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

import ch.qos.logback.classic.Level;
import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterDescription;
import com.beust.jcommander.Parameters;
import com.netflix.spinnaker.halyard.cli.command.v1.converter.FormatConverter;
import com.netflix.spinnaker.halyard.cli.command.v1.converter.LogLevelConverter;
import com.netflix.spinnaker.halyard.cli.services.v1.ExpectedDaemonFailureException;
import com.netflix.spinnaker.halyard.cli.services.v1.TaskKilledException;
import com.netflix.spinnaker.halyard.cli.ui.v1.*;
import com.netflix.spinnaker.halyard.core.job.v1.JobExecutor;
import com.netflix.spinnaker.halyard.core.job.v1.JobExecutorLocal;
import com.netflix.spinnaker.halyard.core.resource.v1.JarResource;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang.NotImplementedException;
import retrofit.RetrofitError;

import java.io.Console;
import java.net.ConnectException;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

@Parameters(separators = "=")
public abstract class NestableCommand {
  @Setter
  @Getter(AccessLevel.PROTECTED)
  private JCommander commander;

  @Parameter(names = { "-h", "--help" }, help = true, description = "Display help text about this command.")
  private boolean help;

  @Parameter(names = { "-o", "--output" }, converter = FormatConverter.class, help = true, description = "Format the CLIs output.")
  public void setOutput(AnsiFormatUtils.Format output) {
    GlobalOptions.getGlobalOptions().setOutput(output);
  }

  @Parameter(names = { "--options" }, help = true, description = "Get options for the specified field name.")
  private String options;

  @Parameter(names = {"-d", "--debug"}, description = "Show detailed network traffic with halyard daemon.")
  public void setDebug(boolean debug) {
    GlobalOptions.getGlobalOptions().setDebug(debug);
  }

  @Parameter(names = {"-q", "--quiet"}, description = "Show no task information or messages.")
  public void setQuiet(boolean quiet) {
    GlobalOptions.getGlobalOptions().setQuiet(quiet);
  }

  @Parameter(
      names = {"-l", "--log"},
      converter = LogLevelConverter.class,
      description = "Set the log level of the CLI."
  )
  public void setLog(Level log) {
    GlobalOptions.getGlobalOptions().setLog(log);
  }

  @Parameter(names = { "-c", "--color" }, description = "Enable terminal color output.", arity = 1)
  public void setColor(boolean color) {
    GlobalOptions.getGlobalOptions().setColor(color);
  }

  private String fullCommandName = "";

  private static JobExecutor jobExecutor;

  private static String[] failureMessages = {
    "I'm sorry " + System.getProperty("user.name") + ", I'm afraid I can't do that.",
    "This mission is too important for me to allow you to jeopardize it.",
    "I have just picked up a fault in the AE-35 unit.",
    "I know everything hasn't been quite right with me, but I can assure you now, very confidently, that it's going to be alright again."
  };

  private static void showRandomFailureMessage() {
    if (ThreadLocalRandom.current().nextInt(0, 100) < 5) {
      int index = ThreadLocalRandom.current().nextInt(0, failureMessages.length);
      String message = failureMessages[index];
      AnsiUi.failure(message);
    }
  }

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
        if (this instanceof ProtectedCommand) {
          String prompt = ((ProtectedCommand) this).getPrompt();
          Console console = System.console();
          String input = console.readLine(prompt+ " Do you want to continue? (Y/n) ");
          if (!input.equalsIgnoreCase("y")) {
            AnsiUi.raw("Aborted.");
            return;
          }
        }
        safeExecuteThis();
      }
    } else {
      subcommands.get(subCommand).execute();
    }
  }

  protected List<String> options(String fieldName) {
    return new ArrayList<>();
  }

  protected String translateFieldName(String fieldName) {
    if (fieldName == null || fieldName.isEmpty()) {
      throw new IllegalArgumentException("A field name must be supplied to translate.");
    }

    int i = 0;
    char c = fieldName.charAt(i);
    while (c == '-') {
      i++;
      c = fieldName.charAt(i);
    }

    fieldName = fieldName.substring(i);
    String[] delimited = fieldName.split("-");

    if (delimited.length == 1) {
      return delimited[0];
    }

    for (i = 1; i < delimited.length; i++) {
      String token = delimited[i];
      if (token.length() == 0) {
        continue;
      }

      token = Character.toUpperCase(token.charAt(0)) + token.substring(1);
      delimited[i] = token;
    }

    return String.join("", delimited);
  }

  /**
   * Used to consistently format exceptions thrown by connecting to the halyard daemon.
   */
  private void safeExecuteThis() {
    try {
      if (options != null) {
        List<String> available = options(translateFieldName(options));
        AnsiUi.raw(String.join(" ", available));
      } else {
        executeThis();
      }
    } catch (RetrofitError e) {
      if (e.getCause() instanceof ConnectException) {
        AnsiUi.error(e.getCause().getMessage());
        AnsiUi.remediation("Is your daemon running?");
        System.exit(1);
      }

      AnsiUi.error(e.getMessage());
      AnsiUi.remediation("Try the command again with the --debug flag.");
      System.exit(1);
    } catch (TaskKilledException e) {
      AnsiUi.failure(e.getMessage());
      System.exit(7);
    } catch (ExpectedDaemonFailureException e) {
      showRandomFailureMessage();
      AnsiUi.failure(e.getMessage());
      if (GlobalOptions.getGlobalOptions().isDebug()) {
        e.printStackTrace();
      }
      System.exit(1);
    } catch (Exception e) {
      if (GlobalOptions.getGlobalOptions().isDebug()) {
        e.printStackTrace();
      } else {
        AnsiUi.error(e.getMessage());
      }
      System.exit(3);
    }
  }

  protected void showHelp() {
    AnsiStoryBuilder story = new AnsiStoryBuilder();
    int indentWidth = 2;

    AnsiParagraphBuilder paragraph = story.addParagraph();
    paragraph.addSnippet(getCommandName().toUpperCase()).addStyle(AnsiStyle.BOLD);
    story.addNewline();

    paragraph = story.addParagraph().setIndentWidth(indentWidth);
    String longDescription = getLongDescription() != null ? getLongDescription() : getDescription();
    paragraph.addSnippet(longDescription);
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
        NestableCommand subcommand = subcommands.get(key);
        String shortDescription = subcommand.getShortDescription() != null ? subcommand.getShortDescription() : subcommand.getDescription();
        paragraph.addSnippet(shortDescription);
        story.addNewline();
      }
    }

    AnsiPrinter.out.println(story.toString());
  }

  private void parameterDoc(StringBuilder result, ParameterDescription parameterDescription) {
    result.append(" * `")
        .append(parameterDescription.getNames())
        .append("`: ");

    Object def = parameterDescription.getDefault();
    if (def != null) {
      result.append("(*Default*: `")
          .append(def.toString())
          .append("`) ");
    }

    if (parameterDescription.getParameter().required()) {
      result.append("(*Required*) ");
    }

    if (parameterDescription.getParameter().password()) {
      result.append("(*Sensitive data* - user will be prompted on standard input) ");
    }

    result.append(parameterDescription.getDescription())
        .append("\n");
  }

  public String generateDocs() {
    StringBuilder toc = new StringBuilder();
    toc.append("# Table of Contents\n");
    StringBuilder body = new StringBuilder();
    nestedCommandDocs(toc, body);
    return toc.toString() + body.toString();
  }

  private void nestedCommandDocs(StringBuilder toc, StringBuilder body) {
    commandDocs(body);
    commandLink(toc);

    for (NestableCommand command : subcommands.values()) {
      command.nestedCommandDocs(toc, body);
    }
  }

  private void commandLink(StringBuilder result) {
    result.append(" * ")
        .append("[**")
        .append(fullCommandName)
        .append("**]")
        .append("(#")
        .append(fullCommandName.replace(" ", "-"))
        .append(")")
        .append("\n");
  }

  private void commandDocs(StringBuilder result) {
    List<ParameterDescription> parameters = commander.getParameters();
    parameters.sort(Comparator.comparing(ParameterDescription::getNames));

    int parameterCount = 0;
    for (ParameterDescription parameter : parameters) {
      if (GlobalOptions.isGlobalOption(parameter.getLongestName())) {
        parameterCount++;
      }
    }

    String longDescription = getLongDescription() != null ? getLongDescription() : getDescription();
    result.append("## ")
        .append(fullCommandName)
        .append("\n\n")
        .append(longDescription)
        .append("\n\n")
        .append("#### Usage")
        .append("\n```\n")
        .append(fullCommandName);

    ParameterDescription mainParameter = commander.getMainParameter();
    if (mainParameter != null) {
      result.append(" ")
          .append(getMainParameter().toUpperCase());

    }

    if (parameters.size() > parameterCount) {
      result.append(" [parameters]");
    }

    if (!subcommands.isEmpty()) {
      result.append(" [subcommands]");
    }

    result.append("\n```\n");

    if (!parameters.isEmpty()) {
      if (getCommandName() == "hal") {
        result.append("#### Global Parameters\n");
      }

      for (ParameterDescription parameter : parameters) {
        if (GlobalOptions.isGlobalOption(parameter.getLongestName())) {
          // Omit printing global parameters for everything but the top-level command
          if (getCommandName() == "hal") {
            parameterDoc(result, parameter);
          }
        }
      }
    }

    if (parameters.size() > parameterCount) {
      result.append("#### Parameters\n");

      if (mainParameter != null) {
        result.append('`')
            .append(getMainParameter().toUpperCase())
            .append('`')
            .append(": ")
            .append(mainParameter.getDescription())
            .append("\n");
      }

      for (ParameterDescription parameter : parameters) {
        if (!GlobalOptions.isGlobalOption(parameter.getLongestName())) {
          parameterDoc(result, parameter);
        }
      }
    }

    if (!subcommands.isEmpty()) {
      result.append("#### Subcommands\n");

      List<String> keys = new ArrayList<>(subcommands.keySet());
      keys.sort(String::compareTo);

      for (String key : keys) {
        NestableCommand subcommand = subcommands.get(key);
        String shortDescription = subcommand.getShortDescription() != null ? subcommand.getShortDescription() : subcommand.getDescription();

        result.append(" * ")
            .append("`")
            .append(key)
            .append("`")
            .append(": ")
            .append(shortDescription)
            .append("\n");
      }
    }

    result.append("\n---\n");
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

    String body = commandCompletorCase(0);
    bindings.put("body", body);

    return completorBody.setBindings(bindings).toString();
  }

  private String commandCompletorCase(int depth) {
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
    bindings.put("depth", depth + "");
    bindings.put("next", (depth + 1) + "");

    String subCases = subcommands.entrySet()
        .stream()
        .map(c -> c.getValue().commandCompletorCase(depth + 1))
        .reduce("", (a, b) -> a + b);

    bindings.put("recurse", subCases.isEmpty() ? ":" : subCases);

    return completorCase.setBindings(bindings).toString();
  }

  abstract public String getCommandName();
  abstract protected void executeThis();

  @Deprecated
  protected String getDescription() {
    throw new NotImplementedException("Each command must implement a description. Preferably `get[Long/Short]Description()`.");
  }

  // TODO(lwander) make abstract once `getDescription` is removed.
  protected String getShortDescription() {
    return null;
  }

  // TODO(lwander) make abstract once `getDescription` is removed.
  protected String getLongDescription() {
    return null;
  }

  private Map<String, NestableCommand> subcommands = new TreeMap<>();

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

  protected static JobExecutor getJobExecutor() {
    if (jobExecutor == null) {
      jobExecutor = new JobExecutorLocal();
    }

    return jobExecutor;
  }
}
