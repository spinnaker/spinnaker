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

package com.netflix.spinnaker.halyard.core.job.v1;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.exec.*;
import rx.Scheduler;
import rx.functions.Action0;
import rx.schedulers.Schedulers;

@Slf4j
public class JobExecutorLocal extends JobExecutor {
  private Scheduler scheduler = Schedulers.computation();

  private Map<String, ExecutionHandler> jobIdToHandlerMap = new ConcurrentHashMap<>();

  private Set<String> pendingJobSet = new ConcurrentSkipListSet<>();

  @Override
  public String startJob(
      JobRequest jobRequest,
      Map<String, String> env,
      InputStream stdIn,
      ByteArrayOutputStream stdOut,
      ByteArrayOutputStream stdErr) {
    List<String> tokenizedCommand = jobRequest.getTokenizedCommand();
    if (tokenizedCommand == null || tokenizedCommand.isEmpty()) {
      throw new IllegalArgumentException("JobRequest must include a tokenized command to run");
    }

    final long timeoutMillis =
        jobRequest.getTimeoutMillis() == null
            ? ExecuteWatchdog.INFINITE_TIMEOUT
            : jobRequest.getTimeoutMillis();

    String jobId = UUID.randomUUID().toString();

    pendingJobSet.add(jobId);

    log.info("Scheduling job " + jobRequest.getTokenizedCommand() + " with id " + jobId);

    scheduler
        .createWorker()
        .schedule(
            new Action0() {
              @Override
              public void call() {
                PumpStreamHandler pumpStreamHandler = new PumpStreamHandler(stdOut, stdErr, stdIn);
                CommandLine commandLine;

                log.info("Executing " + jobId + "with tokenized command: " + tokenizedCommand);

                // Grab the first element as the command.
                commandLine = new CommandLine(jobRequest.getTokenizedCommand().get(0));

                // Treat the rest as arguments.
                String[] arguments =
                    Arrays.copyOfRange(
                        tokenizedCommand.toArray(new String[0]), 1, tokenizedCommand.size());

                commandLine.addArguments(arguments, false);

                DefaultExecuteResultHandler resultHandler = new DefaultExecuteResultHandler();
                ExecuteWatchdog watchdog =
                    new ExecuteWatchdog(timeoutMillis) {
                      @Override
                      public void timeoutOccured(Watchdog w) {
                        // If a watchdog is passed in, this was an actual time-out. Otherwise, it is
                        // likely
                        // the result of calling watchdog.destroyProcess().
                        if (w != null) {
                          log.warn("Job " + jobId + " timed-out after " + timeoutMillis + "ms.");

                          cancelJob(jobId);
                        }

                        super.timeoutOccured(w);
                      }
                    };

                Executor executor = new DefaultExecutor();
                executor.setStreamHandler(pumpStreamHandler);
                executor.setWatchdog(watchdog);
                try {
                  executor.execute(commandLine, env, resultHandler);
                } catch (IOException e) {
                  throw new RuntimeException("Execution of " + jobId + " failed ", e);
                }

                // Give the job some time to spin up.
                try {
                  Thread.sleep(500);
                } catch (InterruptedException ignored) {
                }

                jobIdToHandlerMap.put(
                    jobId,
                    new ExecutionHandler()
                        .setResultHandler(resultHandler)
                        .setWatchdog(watchdog)
                        .setStdOut(stdOut)
                        .setStdErr(stdErr));

                if (pendingJobSet.contains(jobId)) {
                  pendingJobSet.remove(jobId);
                } else {
                  // If the job was removed from the set of pending jobs by someone else, its
                  // deletion was requested
                  jobIdToHandlerMap.remove(jobId);
                  watchdog.destroyProcess();
                }
              }
            });

    return jobId;
  }

  @Data
  private class ExecutionHandler {
    DefaultExecuteResultHandler resultHandler;
    ExecuteWatchdog watchdog;
    ByteArrayOutputStream stdOut;
    ByteArrayOutputStream stdErr;
  }

  @Override
  public boolean jobExists(String jobId) {
    return jobIdToHandlerMap.containsKey(jobId) || pendingJobSet.contains(jobId);
  }

  @Override
  public JobStatus updateJob(String jobId) {
    try {
      log.debug("Polling state for " + jobId + "...");
      ExecutionHandler handler = jobIdToHandlerMap.get(jobId);

      if (handler == null) {
        return null;
      }

      JobStatus jobStatus = new JobStatus().setId(jobId);

      DefaultExecuteResultHandler resultHandler;
      ByteArrayOutputStream stdOutStream;
      ByteArrayOutputStream stdErrStream;

      stdOutStream = handler.getStdOut();
      stdErrStream = handler.getStdErr();
      resultHandler = handler.getResultHandler();

      stdOutStream.flush();
      stdErrStream.flush();

      jobStatus.setStdOut(new String(stdOutStream.toByteArray()));
      jobStatus.setStdErr(new String(stdErrStream.toByteArray()));

      if (resultHandler.hasResult()) {
        jobStatus.setState(JobStatus.State.COMPLETED);

        int exitValue = resultHandler.getExitValue();
        log.info(jobId + " has terminated with exit code " + exitValue);

        if (exitValue == 0) {
          jobStatus.setResult(JobStatus.Result.SUCCESS);
        } else {
          jobStatus.setResult(JobStatus.Result.FAILURE);
        }

        jobIdToHandlerMap.remove(jobId);
      } else {
        jobStatus.setState(JobStatus.State.RUNNING);
      }

      return jobStatus;
    } catch (Exception e) {
      log.warn("Failed to retrieve status of " + jobId);
      return null;
    }
  }

  @Override
  public void cancelJob(String jobId) {
    log.info("Canceling job " + jobId + "...");

    if (pendingJobSet.contains(jobId)) {
      pendingJobSet.remove(jobId);
    }

    // Remove the job from this executors's handler map.
    ExecutionHandler canceledJobHander = jobIdToHandlerMap.remove(jobId);
    if (canceledJobHander == null) {
      return;
    }

    ExecuteWatchdog watchdog = canceledJobHander.getWatchdog();
    if (watchdog == null) {
      return;
    }

    watchdog.destroyProcess();
  }

  @Override
  public void cancelAllJobs() {
    for (Map.Entry<String, ExecutionHandler> job : jobIdToHandlerMap.entrySet()) {
      cancelJob(job.getKey());
    }
  }
}
