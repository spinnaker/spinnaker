/*
 * Copyright 2019 Google, Inc.
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
package com.netflix.spinnaker.clouddriver.jobs.local;

import com.netflix.spinnaker.clouddriver.jobs.JobExecutor;
import com.netflix.spinnaker.clouddriver.jobs.JobRequest;
import com.netflix.spinnaker.clouddriver.jobs.JobStatus;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.exec.*;
import org.springframework.beans.factory.annotation.Value;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
public class JobExecutorLocal implements JobExecutor {
  @Setter
  @Value("${jobs.local.timeoutMinutes:10}")
  private long timeoutMinutes;

  @Override
  public JobStatus runJob(final JobRequest jobRequest, Map<String, String> environment, InputStream inputStream) {
    log.debug("Starting job: \'" + String.join(" ", jobRequest.getTokenizedCommand()) + "\'...");
    final String jobId = UUID.randomUUID().toString();
    log.debug("Executing job with tokenized command: " + String.valueOf(jobRequest.getTokenizedCommand()));

    CommandLine commandLine = createCommandLine(jobRequest.getTokenizedCommand());
    ByteArrayOutputStream stdOut = new ByteArrayOutputStream();
    ByteArrayOutputStream stdErr = new ByteArrayOutputStream();
    PumpStreamHandler pumpStreamHandler = new PumpStreamHandler(stdOut, stdErr, inputStream);
    ExecuteWatchdog watchdog = new ExecuteWatchdog(timeoutMinutes * 60 * 1000);
    Executor executor = new DefaultExecutor();
    executor.setStreamHandler(pumpStreamHandler);
    executor.setWatchdog(watchdog);
    executor.setExitValues(null);

    boolean success = false;
    try {
      int exitValue = executor.execute(commandLine, environment);
      if (watchdog.killedProcess()) {
        log.warn("Job " + jobId + " timed out (after " + String.valueOf(timeoutMinutes) + " minutes).");
      }

      if (exitValue == 0) {
        success = true;
      }
    } catch (IOException e) {
      throw new RuntimeException("Failed to execute job");
    }

    return JobStatus.builder()
      .result(success ? JobStatus.Result.SUCCESS : JobStatus.Result.FAILURE)
      .stdOut(stdOut.toString())
      .stdErr(stdErr.toString())
      .build();
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
}
