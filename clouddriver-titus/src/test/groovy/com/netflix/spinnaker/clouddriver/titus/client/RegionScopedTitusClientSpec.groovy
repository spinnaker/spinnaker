/*
 * Copyright 2016 Netflix, Inc.
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

package com.netflix.spinnaker.clouddriver.titus.client

import com.netflix.spinnaker.clouddriver.titus.client.model.Job
import com.netflix.spinnaker.clouddriver.titus.client.model.SubmitJobRequest
import com.netflix.spinnaker.clouddriver.titus.client.model.TaskState
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import spock.lang.Ignore
import spock.lang.Specification

class RegionScopedTitusClientSpec extends Specification {

  // this isn't really a unit test..
  @Ignore
  void 'job creation lifecycle'() {
    setup:
    Logger logger = LoggerFactory.getLogger(TitusClient)
    TitusRegion titusRegion = new TitusRegion(
      "us-east-1", "test", "http://titusapi.main.us-east-1.dyntest.netflix.net:7101/"
    );
    TitusClient titusClient = new RegionScopedTitusClient(titusRegion);

    // ******************************************************************************************************************

    Map<String, String> env = new HashMap<>();
    env.put("environment", "test");
    env.put("debug", "true");

    SubmitJobRequest submitJobRequest = new SubmitJobRequest()
      .withJobName("helix_hello_world_server-main-test-v001")
      .withDockerImageName("edge.helix-hello-world-server")
      .withApplication("helix_hello_world_server")
      .withStack("main")
      .withDetail("test")
      .withUser("spinnaker")
      .withConstraint(SubmitJobRequest.Constraint.soft(SubmitJobRequest.Constraint.ZONE_BALANCE))
      .withDockerImageVersion("master-201508032132-trusty-5be2a2f")
      .withInstances(1)
      .withCpu(1)
      .withMemory(4096)
      .withDisk(2000)
      .withPorts([7001] as int[])
      .withEnv(env)
      .withAllocateIpAddress(true);

    when:
    String jobId = titusClient.submitJob(submitJobRequest);

    then:
    jobId != null

    logger.info("jobId: {}", jobId);

    when:
    Job job = titusClient.getJob(jobId);

    then:
    logger.info("job {}", job);
    job != null

    when:
    job = titusClient.findJobByName(submitJobRequest.getJobName());

    then:
    logger.info("job by name {}", job);
    job != null

    // ******************************************************************************************************************


    logger.info("Tasks request at {}", new Date());
    List<Job.TaskSummary> tasks = titusClient.getAllTasks();
    logger.info("Tasks response at {}", new Date());
    logger.info("Tasks");
    logger.info("-----------------------------------------------------------------------------------------------");
    logger.info("Task count: {}", tasks.size());

    logger.info("Jobs request: {}", new Date());
    List<Job> jobs = titusClient.getAllJobs();
    logger.info("Jobs response: {}", new Date());
    logger.info("Jobs");
    logger.info("-----------------------------------------------------------------------------------------------");
    logger.info("Jobs count: {}", jobs.size());

    // ******************************************************************************************************************
    when:
    int i = 7;
    boolean found = false;
    while (--i > 0) {
      Job queriedJob = titusClient.getAllJobs().find { it.id == jobId }
      if (queriedJob) {
        found = true;
        break;
      }
      Thread.sleep(15 * 1000L);
    }

    then:
    found

    // ******************************************************************************************************************

    when:
    job = titusClient.getJob(jobId);
    logger.info("Found submitted job in the list of jobs");

    logger.info("Terminating Job {}", jobId);
    titusClient.terminateJob(jobId);

    int j = 14;
    boolean terminated = false;
    Job terminatedJob = null;
    while (--j > 0) {
      terminatedJob = titusClient.getJob(jobId);
      Job.TaskSummary task = terminatedJob.getTasks().get(0);
      if (task.getState() == TaskState.DEAD ||
        task.getState() == TaskState.STOPPED ||
        task.getState() == TaskState.FINISHED) {
        terminated = true;
        break;
      }
      Thread.sleep(10 * 1000L);
    }

    then:
    terminated


    when:
    logger.info("Successfully terminated job {}" + terminatedJob);

    int k = 14;
    boolean foundAfterTermination = true;
    while (--k > 0) {
      List<Job> queriedJobs = titusClient.getAllJobs();
      if (!queriedJobs.contains(job)) {
        foundAfterTermination = false;
        logger.info("Did NOT find job {} in the list of jobs. Terminate successful.", jobId);
        break;
      }
      Thread.sleep(10 * 1000L);
    }

    if (foundAfterTermination) {
      System.err.println("ERROR: Even after terminate, job was FOUND in the list of jobs: " + jobId);
    }

    then:
    !foundAfterTermination

  }
}
