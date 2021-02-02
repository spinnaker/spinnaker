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
package com.netflix.spinnaker.clouddriver.jobs;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import org.apache.commons.exec.CommandLine;

@Getter
public class JobRequest {
  private final List<String> tokenizedCommand;
  private final CommandLine commandLine;
  private final Map<String, String> environment;
  private final InputStream inputStream;
  private final File workingDir;

  public JobRequest(List<String> tokenizedCommand) {
    this(tokenizedCommand, System.getenv(), new ByteArrayInputStream(new byte[0]));
  }

  public JobRequest(List<String> tokenizedCommand, InputStream inputStream) {
    this(tokenizedCommand, System.getenv(), inputStream);
  }

  public JobRequest(
      List<String> tokenizedCommand, Map<String, String> environment, InputStream inputStream) {
    this(tokenizedCommand, environment, inputStream, null);
  }

  public JobRequest(
      List<String> tokenizedCommand, Map<String, String> environment, File workingDir) {
    this(tokenizedCommand, environment, new ByteArrayInputStream(new byte[0]), workingDir);
  }

  public JobRequest(List<String> tokenizedCommand, InputStream inputStream, File workingDir) {
    this(tokenizedCommand, System.getenv(), inputStream, workingDir);
  }

  public JobRequest(List<String> tokenizedCommand, File workingDor) {
    this(tokenizedCommand, System.getenv(), new ByteArrayInputStream(new byte[0]), workingDor);
  }

  public JobRequest(
      List<String> tokenizedCommand,
      Map<String, String> environment,
      InputStream inputStream,
      File workingDir) {
    this.tokenizedCommand = tokenizedCommand;
    this.commandLine = createCommandLine(tokenizedCommand);
    this.environment = environment;
    this.inputStream = inputStream;
    this.workingDir = workingDir;
  }

  private CommandLine createCommandLine(List<String> tokenizedCommand) {
    if (tokenizedCommand == null || tokenizedCommand.size() == 0) {
      throw new IllegalArgumentException("No tokenizedCommand specified.");
    }

    // Grab the first element as the command.
    CommandLine commandLine = new CommandLine(tokenizedCommand.get(0));

    int size = tokenizedCommand.size();
    String[] arguments = tokenizedCommand.subList(1, size).toArray(new String[size - 1]);
    commandLine.addArguments(arguments, false);
    return commandLine;
  }

  @Override
  public String toString() {
    return String.join(" ", tokenizedCommand);
  }
}
