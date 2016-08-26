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

package com.netflix.spinnaker.clouddriver.jobs.local

import com.netflix.spinnaker.clouddriver.jobs.JobExecutor
import com.netflix.spinnaker.clouddriver.jobs.JobRequest
import com.netflix.spinnaker.clouddriver.jobs.JobStatus
import groovy.util.logging.Slf4j
import org.apache.commons.exec.*
import org.springframework.beans.factory.annotation.Value
import rx.Scheduler
import rx.functions.Action0
import rx.schedulers.Schedulers

import java.util.concurrent.ConcurrentHashMap

@Slf4j
class JobExecutorLocal implements JobExecutor {

  @Value('${jobs.local.timeoutMinutes:10}')
  long timeoutMinutes

  Scheduler scheduler = Schedulers.computation()
  Map<String, Map> jobIdToHandlerMap = new ConcurrentHashMap<String, Map>()

  @Override
  String startJob(JobRequest jobRequest) {
    log.info("Starting job: $jobRequest.tokenizedCommand...")

    String jobId = UUID.randomUUID().toString()

    scheduler.createWorker().schedule(
      new Action0() {
        @Override
        public void call() {
          ByteArrayOutputStream stdOutAndErr = new ByteArrayOutputStream()
          PumpStreamHandler pumpStreamHandler = new PumpStreamHandler(stdOutAndErr)
          CommandLine commandLine

          if (jobRequest.tokenizedCommand) {
            log.info("Executing $jobId with tokenized command: $jobRequest.tokenizedCommand")

            // Grab the first element as the command.
            commandLine = new CommandLine(jobRequest.tokenizedCommand[0])

            // Treat the rest as arguments.
            String[] arguments = Arrays.copyOfRange(jobRequest.tokenizedCommand.toArray(), 1, jobRequest.tokenizedCommand.size())

            commandLine.addArguments(arguments, false)
          } else {
            throw new IllegalArgumentException("No tokenizedCommand specified for $jobId.")
          }

          DefaultExecuteResultHandler resultHandler = new DefaultExecuteResultHandler()
          ExecuteWatchdog watchdog = new ExecuteWatchdog(timeoutMinutes * 60 * 1000){
            @Override
            void timeoutOccured(Watchdog w) {
              // If a watchdog is passed in, this was an actual time-out. Otherwise, it is likely
              // the result of calling watchdog.destroyProcess().
              if (w) {
                log.warn("Job $jobId timed-out (after $timeoutMinutes minutes).")

                cancelJob(jobId)
              }

              super.timeoutOccured(w)
            }
          }
          Executor executor = new DefaultExecutor()
          executor.setStreamHandler(pumpStreamHandler)
          executor.setWatchdog(watchdog)
          executor.execute(commandLine, resultHandler)

          // Give the job some time to spin up.
          sleep(500)

          jobIdToHandlerMap.put(jobId, [
            handler: resultHandler,
            watchdog: watchdog,
            stdOutAndErr: stdOutAndErr
          ])
        }
      }
    )

    return jobId
  }

  @Override
  JobStatus updateJob(String jobId) {
    try {
      log.info("Polling state for $jobId...")

      if (jobIdToHandlerMap[jobId]) {
        JobStatus jobStatus = new JobStatus(id: jobId)

        DefaultExecuteResultHandler resultHandler
        ByteArrayOutputStream stdOutAndErr

        jobIdToHandlerMap[jobId].with {
          resultHandler = it.handler
          stdOutAndErr = it.stdOutAndErr
        }

        String logsContent = new String(stdOutAndErr.toByteArray())

        if (resultHandler.hasResult()) {
          log.info("State for $jobId changed with exit code $resultHandler.exitValue.")

          if (!logsContent) {
            logsContent = resultHandler.exception ? resultHandler.exception.message : "No output from command."
          }

          if (resultHandler.exitValue == 0) {
            jobStatus.state = JobStatus.State.COMPLETED
            jobStatus.result = JobStatus.Result.SUCCESS
          } else {
            jobStatus.state = JobStatus.State.COMPLETED
            jobStatus.result = JobStatus.Result.FAILURE
          }

          jobIdToHandlerMap.remove(jobId)
        } else {
          jobStatus.state = JobStatus.State.RUNNING
        }

        if (logsContent) {
          jobStatus.logsContent = logsContent
        }

        return jobStatus
      } else {
        // This instance is not managing the job
        return null
      }
    } catch (Exception e) {
      log.error("Failed to update $jobId", e)

      return null
    }

  }

  @Override
  void cancelJob(String jobId) {
    log.info("Canceling job $jobId...")

    // Remove the job from this rosco instance's handler map.
    def canceledJob = jobIdToHandlerMap.remove(jobId)

    // Terminate the process.
    canceledJob?.watchdog?.destroyProcess()

    // The next polling interval will be unable to retrieve the job status and will mark it as canceled.
  }
}
