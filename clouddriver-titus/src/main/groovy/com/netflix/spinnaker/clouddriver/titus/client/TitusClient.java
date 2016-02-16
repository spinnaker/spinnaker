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

import java.util.List;
import java.util.Map;

public interface TitusClient {

    /**
     * @param jobId
     * @return
     */
    public Job getJob(String jobId);

    /**
     *
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
     *
     * @param submitJobRequest
     * @return
     */
    public String submitJob(SubmitJobRequest submitJobRequest);

    /**
     *
     * @param jobAttributes
     */
    public void updateJob(String jobId, Map<String, Object> jobAttributes);

    /**
     *
     * @param taskId
     * @return
     */
    public Task getTask(String taskId);

    /**
     *
     * @param jobId
     */
    public void terminateJob(String jobId);

    /**
     *
     * @param taskId
     */
    public void terminateTask(String taskId);

    /**
     * @param taskId
     * @return
     */
    public Logs getLogs(String taskId);

    /**
     *
     * @return
     */
    public TitusHealth getHealth();

    /**
     * @return
     */
    public List<Job> getAllJobs();

    /**
     * @return
     */
    public List<Job.TaskSummary> getAllTasks();
}
