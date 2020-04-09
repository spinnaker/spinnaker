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
package com.netflix.spinnaker.clouddriver.jobs;

import com.netflix.spinnaker.clouddriver.jobs.local.ReaderConsumer;

/**
 * Executes a job defined by a JobRequest, returning the results as a JobResult.
 *
 * <p>The caller can optionally supply a ReaderConsumer, in which case the output from the job will
 * be transformed by the ReaderConsumer before being returned in JobResult.
 *
 * <p>There are two general types of errors that can occur when executing a job:
 *
 * <ul>
 *   <li>If the JobExecutor fails to start or monitor a job, or if it is unable to read the job's
 *       output, a {@link JobExecutionException} is thrown.
 *   <li>If the JobExecutor successfully starts and monitors the job, but the job itself fails, no
 *       exception is thrown but the returned {@link JobResult} will indicate that the job failed.
 * </ul>
 *
 * @see JobRequest
 * @see JobResult
 */
public interface JobExecutor {
  /**
   * Runs the specified JobRequest, returning the job's standard output in a JobResult.
   *
   * @param jobRequest The job request
   * @return The result of the job
   * @throws JobExecutionException if there is an error starting or monitoring the job
   */
  JobResult<String> runJob(JobRequest jobRequest);

  /**
   * Runs the specified JobRequest, transforming the job's standard output with the supplied
   * ReaderConsumer, and returning the transformed result in a JobResult.
   *
   * @param jobRequest The job request
   * @param readerConsumer A function that transforms the job's standard output
   * @return The result of the job
   * @throws JobExecutionException if there is an error starting or monitoring the job
   */
  <T> JobResult<T> runJob(JobRequest jobRequest, ReaderConsumer<T> readerConsumer);
}
