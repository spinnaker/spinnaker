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
 *
 */

package com.netflix.spinnaker.halyard.core.job.v1;

import com.netflix.spinnaker.halyard.core.tasks.v1.DaemonTaskHandler;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DaemonLocalJobExecutor extends JobExecutorLocal {
  @Override
  public String startJob(
      JobRequest jobRequest,
      Map<String, String> env,
      InputStream stdIn,
      ByteArrayOutputStream stdOut,
      ByteArrayOutputStream stdErr) {
    String jobId = super.startJob(jobRequest, env, stdIn, stdOut, stdErr);
    log.info("Starting daemon job " + jobId);
    DaemonTaskHandler.getTask().getRunningJobs().add(jobId);
    return jobId;
  }

  @Override
  public void cancelJob(String jobId) {
    super.cancelJob(jobId);
    DaemonTaskHandler.getTask().getRunningJobs().remove(jobId);
  }

  @Override
  public void cancelAllJobs() {
    super.cancelAllJobs();
    DaemonTaskHandler.getTask().getRunningJobs().clear();
  }
}
