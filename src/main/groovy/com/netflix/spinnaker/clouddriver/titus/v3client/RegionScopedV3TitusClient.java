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

package com.netflix.spinnaker.clouddriver.titus.v3client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.frigga.Names;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.clouddriver.titus.client.*;
import com.netflix.spinnaker.clouddriver.titus.client.model.*;
import com.netflix.spinnaker.clouddriver.titus.client.model.Job;
import com.netflix.spinnaker.clouddriver.titus.client.model.Task;
import com.netflix.titus.grpc.protogen.*;
import groovy.util.logging.Log;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Log
public class RegionScopedV3TitusClient implements TitusClient {
  private static final Logger LOGGER = LoggerFactory.getLogger(TitusRestAdapter.class);

  /**
   * Default connect timeout in milliseconds
   */
  private static final long DEFAULT_CONNECT_TIMEOUT = 10000;

  /**
   * Default read timeout in milliseconds
   */
  private static final long DEFAULT_READ_TIMEOUT = 20000;

  /**
   * An instance of {@link TitusRegion} that this RegionScopedTitusClient will use
   */
  private final TitusRegion titusRegion;

  private final Registry registry;

  private final List<TitusJobCustomizer> titusJobCustomizers;

  private final JobManagementServiceGrpc.JobManagementServiceStub grpc;

  private final JobManagementServiceGrpc.JobManagementServiceBlockingStub grpcBlockingStub;


  public RegionScopedV3TitusClient(TitusRegion titusRegion, Registry registry, List<TitusJobCustomizer> titusJobCustomizers) {
    this(titusRegion, DEFAULT_CONNECT_TIMEOUT, DEFAULT_READ_TIMEOUT, TitusClientObjectMapper.configure(), registry, titusJobCustomizers);
  }

  public RegionScopedV3TitusClient(TitusRegion titusRegion,
                                   long connectTimeoutMillis,
                                   long readTimeoutMillis,
                                   ObjectMapper objectMapper,
                                   Registry registry,
                                   List<TitusJobCustomizer> titusJobCustomizers) {
    this.titusRegion = titusRegion;
    this.registry = registry;
    this.titusJobCustomizers = titusJobCustomizers;
    ManagedChannel channel = ManagedChannelBuilder.forAddress("titusapi.devvpc3.us-east-1.dyntest.netflix.net", 7104).usePlaintext(true).build();
    this.grpc = JobManagementServiceGrpc.newStub(channel);
    this.grpcBlockingStub = JobManagementServiceGrpc.newBlockingStub(channel);
  }

  // APIs
  // ------------------------------------------------------------------------------------------

  @Override
  public Job getJob(String jobId) {
    return new Job(grpcBlockingStub.findJob(JobId.newBuilder().setId(jobId).build()));
  }

  @Override
  public Job findJobByName(String jobName) {
    JobQuery.Builder jobQuery = JobQuery.newBuilder()
      .putFiterlingCriteria("labels", "name=" + jobName + ",source=spinnaker");
    return getJobs(jobQuery).get(0);
  }

  @Override
  public List<Job> findJobsByApplication(String application) {
    JobQuery.Builder jobQuery = JobQuery.newBuilder().putFiterlingCriteria("appName", application);
    return getJobs(jobQuery);
  }

  @Override
  public String submitJob(SubmitJobRequest submitJobRequest) {
    JobDescription jobDescription = submitJobRequest.getJobDescription();
    if (jobDescription.getType() == null) {
      jobDescription.setType("service");
    }
    if (jobDescription.getUser() == null) {
      jobDescription.setUser("spinnaker");
    }
    if (jobDescription.getJobGroupSequence() == null && jobDescription.getType().equals("service")) {
      try {
        int sequence = Names.parseName(jobDescription.getName()).getSequence();
        jobDescription.setJobGroupSequence(String.format("v%03d", sequence));
      } catch (Exception e) {
        // fail silently if we can't get a job group sequence
      }
    }
    jobDescription.getLabels().put("name", jobDescription.getName());
    jobDescription.getLabels().put("source", "spinnaker");
    jobDescription.getLabels().put("spinnakerAccount", submitJobRequest.getCredentials());
    for (TitusJobCustomizer customizer : titusJobCustomizers) {
      customizer.customize(jobDescription);
    }
    return grpcBlockingStub.createJob(jobDescription.getGrpcJobDescriptor()).getId();
  }

  @Override
  public Task getTask(String taskId) {
    return new Task(grpcBlockingStub.findTask(com.netflix.titus.grpc.protogen.TaskId.newBuilder().setId(taskId).build()));
  }

  @Override
  public void resizeJob(ResizeJobRequest resizeJobRequest) {
    grpcBlockingStub.updateJobInstances(JobInstancesUpdate.newBuilder()
      .setJobId(resizeJobRequest.getJobId())
      .setTaskInstances(TaskInstances.newBuilder()
        .setDesired(resizeJobRequest.getInstancesDesired())
        .setMax(resizeJobRequest.getInstancesMax())
        .setMin(resizeJobRequest.getInstancesMin())
      )
      .build()
    );
  }

  @Override
  public void activateJob(ActivateJobRequest activateJobRequest) {
    grpcBlockingStub.updateJobStatus(JobStatusUpdate.newBuilder().setEnableStatus(activateJobRequest.getInService()).build());
  }

  @Override
  public void terminateJob(TerminateJobRequest terminateJobRequest) {
    grpcBlockingStub.killJob(JobId.newBuilder().setId(terminateJobRequest.getJobId()).build());
  }

  @Override
  public void terminateTasksAndShrink(TerminateTasksAndShrinkJobRequest terminateTasksAndShrinkJob) {
    grpcBlockingStub.killTasks(TaskIds.newBuilder().addAllId(terminateTasksAndShrinkJob.getTaskIds()).build());
  }

  @Override
  public Map logsDownload(String taskId) {
    // return execute("logsDownload", grpc.logsDownload(taskId));
    return null;
  }

  @Override
  public TitusHealth getHealth() {
    return new TitusHealth(HealthStatus.HEALTHY);
  }

  @Override
  public List<Job> getAllJobs() {
    JobQuery.Builder jobQuery = JobQuery.newBuilder()
      .putFiterlingCriteria("jobType", "SERVICE")
      .putFiterlingCriteria("labels", "source=spinnaker");
    return getJobs(jobQuery);
  }

  private List<Job> getJobs(JobQuery.Builder jobQuery) {
    int currentPage = 0;
    int allPages;
    List<Job> jobs = new ArrayList<>();
    do {
      jobQuery.setPage(Page.newBuilder().setPageNumber(currentPage).setPageSize(100));
      JobQuery criteria = jobQuery.build();
      JobQueryResult resultPage = grpcBlockingStub.findJobs(criteria);
      jobs.addAll(resultPage.getItemsList().stream().map(grpcJob -> {
        return new Job(grpcJob);
      }).collect(Collectors.toList()));
      allPages = resultPage.getPagination().getAllPages();
      currentPage++;
    } while (allPages > currentPage);
    return jobs;
  }


}
