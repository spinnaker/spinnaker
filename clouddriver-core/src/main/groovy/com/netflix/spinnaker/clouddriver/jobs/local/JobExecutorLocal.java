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
import com.netflix.spinnaker.clouddriver.jobs.JobResult;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.exec.*;

import java.io.*;
import java.util.UUID;

@Slf4j
public class JobExecutorLocal implements JobExecutor {
  private final long timeoutMinutes;

  public JobExecutorLocal(long timeoutMinutes) {
    this.timeoutMinutes = timeoutMinutes;
  }

  @Override
  public JobResult<String> runJob(final JobRequest jobRequest) {
    return executeWrapper(jobRequest, this::execute);
  }

  @Override
  public <T> JobResult<T> runJob(final JobRequest jobRequest, ReaderConsumer<T> readerConsumer) {
    return executeWrapper(jobRequest, request -> executeStreaming(request, readerConsumer));
  }

  private <T> JobResult<T> executeWrapper(final JobRequest jobRequest, RequestExecutor<T> requestExecutor) {
    log.debug(String.format("Starting job: '%s'...", String.join(" ", jobRequest.getTokenizedCommand())));
    final String jobId = UUID.randomUUID().toString();

    JobResult<T> jobResult;
    try {
      jobResult = requestExecutor.execute(jobRequest);
    } catch (IOException e) {
      throw new RuntimeException("Failed to execute job", e);
    }

    if (jobResult.isKilled()) {
      log.warn(String.format("Job %s timed out (after %d minutes)", jobId, timeoutMinutes));
    }

    return jobResult;
  }

  private JobResult<String> execute(JobRequest jobRequest) throws IOException {
    ByteArrayOutputStream stdOut = new ByteArrayOutputStream();
    ByteArrayOutputStream stdErr = new ByteArrayOutputStream();

    Executor executor = buildExecutor(new PumpStreamHandler(stdOut, stdErr, jobRequest.getInputStream()));
    int exitValue = executor.execute(jobRequest.getCommandLine(), jobRequest.getEnvironment());

    return JobResult.<String>builder()
      .result(exitValue == 0 ? JobResult.Result.SUCCESS : JobResult.Result.FAILURE)
      .killed(executor.getWatchdog().killedProcess())
      .output(stdOut.toString())
      .error(stdErr.toString())
      .build();
  }

  private <T> JobResult<T> executeStreaming(JobRequest jobRequest, ReaderConsumer<T> consumer) throws IOException {
    PipedOutputStream stdOut = new PipedOutputStream();
    ByteArrayOutputStream stdErr = new ByteArrayOutputStream();

    Executor executor = buildExecutor(new PumpStreamHandler(stdOut, stdErr, jobRequest.getInputStream()));
    DefaultExecuteResultHandler resultHandler = new DefaultExecuteResultHandler();
    executor.execute(jobRequest.getCommandLine(), jobRequest.getEnvironment(), resultHandler);

    T result = consumer.consume(new BufferedReader(new InputStreamReader(new PipedInputStream(stdOut))));

    try {
      resultHandler.waitFor();
    } catch (InterruptedException e) {
      executor.getWatchdog().destroyProcess();
      Thread.currentThread().interrupt();
      throw new RuntimeException(e);
    }

    return JobResult.<T>builder()
      .result(resultHandler.getExitValue() == 0 ? JobResult.Result.SUCCESS : JobResult.Result.FAILURE)
      .killed(executor.getWatchdog().killedProcess())
      .output(result)
      .error(stdErr.toString())
      .build();
  }

  private Executor buildExecutor(ExecuteStreamHandler streamHandler) {
    Executor executor = new DefaultExecutor();
    executor.setStreamHandler(streamHandler);
    executor.setWatchdog(new ExecuteWatchdog(timeoutMinutes * 60 * 1000));
    // Setting this to null causes the executor to skip verifying exit codes; we'll handle checking the exit status
    // instead of having the executor throw an exception for non-zero exit codes.
    executor.setExitValues(null);

    return executor;
  }

  interface RequestExecutor<U> {
    JobResult<U> execute(JobRequest jobRequest) throws IOException;
  }
}
