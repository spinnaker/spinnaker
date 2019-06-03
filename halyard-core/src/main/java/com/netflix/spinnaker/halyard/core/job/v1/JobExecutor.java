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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

// TODO(lwander) unify with original job executor:
// https://github.com/spinnaker/rosco/blob/bf718907888a7d95a0da6e21ec0e00c0709c4e19/rosco-core/src/main/groovy/com/netflix/spinnaker/rosco/jobs/JobExecutor.groovy
public abstract class JobExecutor {
  public abstract String startJob(
      JobRequest jobRequest,
      Map<String, String> env,
      InputStream stdIn,
      ByteArrayOutputStream stdOut,
      ByteArrayOutputStream stdErr);

  public abstract boolean jobExists(String jobId);

  public abstract JobStatus updateJob(String jobId);

  public abstract void cancelJob(String jobId);

  public abstract void cancelAllJobs();

  public void cancelJobs(List<String> jobIds) {
    jobIds.forEach(this::cancelJob);
  }

  public String startJob(JobRequest jobRequest) {
    InputStream stdIn = new ByteArrayInputStream("".getBytes());
    ByteArrayOutputStream stdOut = new ByteArrayOutputStream();
    ByteArrayOutputStream stdErr = new ByteArrayOutputStream();
    return startJob(jobRequest, System.getenv(), stdIn, stdOut, stdErr);
  }

  public String startJobFromStandardStreams(JobRequest jobRequest) {
    InputStream stdIn = System.in;
    ByteArrayOutputStream stdOut = new TeeByteArrayOutputStream(System.out);
    ByteArrayOutputStream stdErr = new TeeByteArrayOutputStream(System.err);
    return startJob(jobRequest, System.getenv(), stdIn, stdOut, stdErr);
  }

  public JobStatus backoffWait(String jobId) throws InterruptedException {
    return backoffWait(jobId, 100, 1000);
  }

  public JobStatus backoffWait(String jobId, long minWaitMillis, long maxWaitMillis)
      throws InterruptedException {
    JobStatus result = updateJob(jobId);
    long waitTime = minWaitMillis;
    while (result == null || result.getState() == JobStatus.State.RUNNING) {
      Thread.sleep(waitTime);

      waitTime <<= 1;
      waitTime = Math.min(maxWaitMillis, waitTime);
      result = updateJob(jobId);
    }

    return result;
  }
}
