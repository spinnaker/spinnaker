/*
 * Copyright 2017 Netflix, Inc.
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

import com.netflix.spectator.api.NoopRegistry
import com.netflix.spinnaker.clouddriver.titus.client.model.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import spock.lang.Ignore
import spock.lang.Specification

@Ignore
class RegionScopedTitusClientSpec extends Specification {

  // this isn't really a unit test..
  void 'job creation lifecycle'() {
    setup:
    Logger logger = LoggerFactory.getLogger(TitusClient)
    TitusRegion titusRegion = new TitusRegion(
      "us-east-1", "test", "http://titusapi.mainvpc.us-east-1.dyntest.netflix.net:7001/", "blah", "blah", 7104, []
    );
    TitusClient titusClient = new RegionScopedTitusClient(titusRegion, new NoopRegistry(), Collections.emptyList(), "test", "titusapigrpc-mcetest-mainvpc");

    // ******************************************************************************************************************

    Map<String, String> env = new HashMap<>();
    env.put("debug", "true");

    Map<String, String> labels = new HashMap<>();
    labels.put("label1", "~3948");
    labels.put("lable2", "jksdljsfdl");

    SubmitJobRequest submitJobRequest = new SubmitJobRequest()
      .withJobName("helix_hello_world_server-main-test-v001")
      .withDockerImageName("titusops/nodehelloworld")
      .withApplication("helix_hello_world_server")
      .withStack("main")
      .withDetail("test")
      .withUser("spinnaker@netflix.com")
      .withConstraint(SubmitJobRequest.Constraint.soft(SubmitJobRequest.Constraint.ZONE_BALANCE))
      .withDockerImageVersion("master-h3.192cce6")
      .withInstancesMin(1)
      .withInstancesMax(5)
      .withInstancesDesired(2)
      .withCpu(1)
      .withMemory(4096)
      .withNetworkMbps(128)
      .withDisk(2000)
      .withEntryPoint("ls -la")
      .withIamProfile("TitusContainerRole")
      .withSecurityGroups([
      'sg-f0f19494',
      'sg-6321d91b'
    ])
      .withPorts([7001] as int[])
      .withEnv(env)
      .withLabels(labels)
      .withAllocateIpAddress(true)
      .withInService(false)
      .withCredentials('titusdevvpc');

    when:

    titusClient.findJobsByApplication("helix_hello_world_server").each { job ->
      titusClient.terminateJob(new TerminateJobRequest().withJobId(job.getId()));
    }

    String jobId = titusClient.submitJob(submitJobRequest);

    then:
    jobId != null

    logger.info("jobId: {}", jobId);

    when:
    Job job = titusClient.getJobAndAllRunningAndCompletedTasks(jobId);

    then:
    logger.info("job {}", job);
    job != null
    job.capacityGroup == "helix_hello_world_server";

    when:
    job = titusClient.findJobByName(submitJobRequest.getJobName());


    then:
    logger.info("job by name {}", job);
    job != null

    logger.info("Jobs request: {}", new Date());
    List<Job> jobs = titusClient.getAllJobsWithTasks();
    logger.info("Jobs response: {}", new Date());
    logger.info("Jobs");
    logger.info("-----------------------------------------------------------------------------------------------");
    logger.info("Jobs count: {}", jobs.size());

    // ******************************************************************************************************************
    when:
    int i = 7;
    boolean found = false;
    while (--i > 0) {
      Job queriedJob = titusClient.getAllJobsWithTasks().find { it.id == jobId }
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
    job = titusClient.getJobAndAllRunningAndCompletedTasks(jobId);
    logger.info("Found submitted job in the list of jobs");

    then:


    then:
    job.instancesDesired == 2
    job.instancesMax == 5
    job.instancesMin == 1

    when:
    logger.info("Resizing Job {}", jobId);
    titusClient.resizeJob(new ResizeJobRequest()
      .withJobId(job.id)
      .withUser('spinnaker')
      .withInstancesDesired(1)
      .withInstancesMin(1)
      .withInstancesMax(10)
    )

    job = titusClient.getJobAndAllRunningAndCompletedTasks(jobId);

    then:
    job.instancesDesired == 1
    job.instancesMax == 10
    job.instancesMin == 1

    when:

    logger.info("Terminating Job {}", jobId);
    titusClient.terminateJob(new TerminateJobRequest().withJobId(jobId));

    int j = 14;
    boolean terminated = false;
    Job terminatedJob = null;
    while (--j > 0) {
      terminatedJob = titusClient.getJobAndAllRunningAndCompletedTasks(jobId);
      Task task = terminatedJob.getTasks().get(0);
      if (task.getState() == TaskState.DEAD ||
        task.getState() == TaskState.STOPPED ||
        task.getState() == TaskState.FAILED ||
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
      List<Job> queriedJobs = titusClient.getAllJobsWithTasks();
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
