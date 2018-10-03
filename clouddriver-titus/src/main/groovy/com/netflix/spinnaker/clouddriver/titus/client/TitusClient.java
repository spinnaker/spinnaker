/*
 * Copyright 2015 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.titus.client;

import com.netflix.spinnaker.clouddriver.titus.client.model.*;
import com.netflix.titus.grpc.protogen.JobChangeNotification;

import java.util.Iterator;
import java.util.List;
import java.util.Map;

public interface TitusClient {

  /**
   * @param jobId
   * @return
   */
  public Job getJobAndAllRunningAndCompletedTasks(String jobId);

  /**
   * @param jobName
   * @param includeTasks
   * @return
   */
  public Job findJobByName(String jobName, boolean includeTasks);

  /**
   * @param jobName
   * @return
   */
  public Job findJobByName(String jobName);

  /**
   * @param application
   * @return
   */
  public List<Job> findJobsByApplication(String application);

  /**
   * @param submitJobRequest
   * @return
   */
  public String submitJob(SubmitJobRequest submitJobRequest);

  /**
   * @param taskId
   * @return
   */
  public Task getTask(String taskId);

  /**
   * @param resizeJobRequest
   */
  public void resizeJob(ResizeJobRequest resizeJobRequest);

  /**
   * @param activateJobRequest
   */
  public void activateJob(ActivateJobRequest activateJobRequest);

  /**
   * @param shouldEnable
   */
  public void setAutoscaleEnabled(String jobId, boolean shouldEnable);

  /**
   * @param terminateJobRequest
   */
  public void terminateJob(TerminateJobRequest terminateJobRequest);

  /**
   * @param terminateTasksAndShrinkJob
   */
  public void terminateTasksAndShrink(TerminateTasksAndShrinkJobRequest terminateTasksAndShrinkJob);

  /**
   * @param taskId
   * @return
   */
  public Map logsDownload(String taskId);

  /**
   * @return
   */
  public TitusHealth getHealth();

  /**
   * @return
   */
  public List<Job> getAllJobsWithTasks();

  /**
   * For use in TitusV2ClusterCachingAgent
   * @return all jobs w/o task detail that are managed by Spinnaker
   */
  public List<Job> getAllJobsWithoutTasks();

  /**
   * For use in TitusInstanceCachingAgent
   * @return all tasks managed by Spinnaker
   */
  public List<Task> getAllTasks();

  public Map<String, String> getAllJobNames();

  public Map<String, List<String>> getTaskIdsForJobIds();

  public Iterator<JobChangeNotification> observeJobs();

}
