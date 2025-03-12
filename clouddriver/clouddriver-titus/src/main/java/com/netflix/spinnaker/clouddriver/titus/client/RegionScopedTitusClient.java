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

package com.netflix.spinnaker.clouddriver.titus.client;

import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.toList;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.protobuf.Empty;
import com.netflix.frigga.Names;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.clouddriver.titus.TitusException;
import com.netflix.spinnaker.clouddriver.titus.client.model.ActivateJobRequest;
import com.netflix.spinnaker.clouddriver.titus.client.model.DisruptionBudgetHelper;
import com.netflix.spinnaker.clouddriver.titus.client.model.GrpcChannelFactory;
import com.netflix.spinnaker.clouddriver.titus.client.model.HealthStatus;
import com.netflix.spinnaker.clouddriver.titus.client.model.Job;
import com.netflix.spinnaker.clouddriver.titus.client.model.JobDescription;
import com.netflix.spinnaker.clouddriver.titus.client.model.JobDisruptionBudgetUpdateRequest;
import com.netflix.spinnaker.clouddriver.titus.client.model.ResizeJobRequest;
import com.netflix.spinnaker.clouddriver.titus.client.model.SubmitJobRequest;
import com.netflix.spinnaker.clouddriver.titus.client.model.Task;
import com.netflix.spinnaker.clouddriver.titus.client.model.TerminateJobRequest;
import com.netflix.spinnaker.clouddriver.titus.client.model.TerminateTasksAndShrinkJobRequest;
import com.netflix.spinnaker.clouddriver.titus.client.model.TitusHealth;
import com.netflix.spinnaker.clouddriver.titus.deploy.description.ServiceJobProcessesRequest;
import com.netflix.spinnaker.kork.core.RetrySupport;
import com.netflix.titus.grpc.protogen.Capacity;
import com.netflix.titus.grpc.protogen.JobCapacityUpdate;
import com.netflix.titus.grpc.protogen.JobChangeNotification;
import com.netflix.titus.grpc.protogen.JobDisruptionBudget;
import com.netflix.titus.grpc.protogen.JobDisruptionBudgetUpdate;
import com.netflix.titus.grpc.protogen.JobId;
import com.netflix.titus.grpc.protogen.JobManagementServiceGrpc;
import com.netflix.titus.grpc.protogen.JobProcessesUpdate;
import com.netflix.titus.grpc.protogen.JobQuery;
import com.netflix.titus.grpc.protogen.JobQueryResult;
import com.netflix.titus.grpc.protogen.JobStatusUpdate;
import com.netflix.titus.grpc.protogen.ObserveJobsQuery;
import com.netflix.titus.grpc.protogen.Page;
import com.netflix.titus.grpc.protogen.ServiceJobSpec;
import com.netflix.titus.grpc.protogen.TaskKillRequest;
import com.netflix.titus.grpc.protogen.TaskQuery;
import com.netflix.titus.grpc.protogen.TaskQueryResult;
import io.grpc.Status;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RegionScopedTitusClient implements TitusClient {

  private final Logger log = LoggerFactory.getLogger(getClass());

  /** Default connect timeout in milliseconds */
  private static final long DEFAULT_CONNECT_TIMEOUT = TimeUnit.SECONDS.toMillis(60);

  /** Default read timeout in milliseconds */
  private static final long DEFAULT_READ_TIMEOUT = 20000;

  /** Default find tasks deadline in milliseconds */
  private static final long FIND_TASKS_DEADLINE = 30000;

  /** An instance of {@link TitusRegion} that this RegionScopedTitusClient will use */
  private final TitusRegion titusRegion;

  private final Registry registry;

  private final List<TitusJobCustomizer> titusJobCustomizers;

  private final String environment;

  private final ObjectMapper objectMapper;

  private final JobManagementServiceGrpc.JobManagementServiceBlockingStub grpcBlockingStub;

  private final JobManagementServiceGrpc.JobManagementServiceBlockingStub grpcNoDeadlineStub;

  private final RetrySupport retrySupport;

  public RegionScopedTitusClient(
      TitusRegion titusRegion,
      Registry registry,
      List<TitusJobCustomizer> titusJobCustomizers,
      String environment,
      String eurekaName,
      GrpcChannelFactory grpcChannelFactory,
      RetrySupport retrySupport) {
    this(
        titusRegion,
        DEFAULT_CONNECT_TIMEOUT,
        DEFAULT_READ_TIMEOUT,
        TitusClientObjectMapper.configure(),
        registry,
        titusJobCustomizers,
        environment,
        eurekaName,
        grpcChannelFactory,
        retrySupport);
  }

  public RegionScopedTitusClient(
      TitusRegion titusRegion,
      long connectTimeoutMillis,
      long readTimeoutMillis,
      ObjectMapper objectMapper,
      Registry registry,
      List<TitusJobCustomizer> titusJobCustomizers,
      String environment,
      String eurekaName,
      GrpcChannelFactory channelFactory,
      RetrySupport retrySupport) {
    this.titusRegion = titusRegion;
    this.registry = registry;
    this.titusJobCustomizers = titusJobCustomizers;
    this.environment = environment;
    this.objectMapper = objectMapper;
    this.retrySupport = retrySupport;

    String titusHost = "";
    try {
      URL titusUrl = new URL(titusRegion.getEndpoint());
      titusHost = titusUrl.getHost();
    } catch (Exception e) {

    }

    this.grpcBlockingStub =
        JobManagementServiceGrpc.newBlockingStub(
            channelFactory.build(
                titusRegion, environment, eurekaName, connectTimeoutMillis, registry));

    this.grpcNoDeadlineStub =
        JobManagementServiceGrpc.newBlockingStub(
            channelFactory.build(titusRegion, environment, eurekaName, 0, registry));

    if (!titusRegion.getFeatureFlags().isEmpty()) {
      log.info(
          "Experimental Titus V3 client feature flags {} enabled for account {} and region {}",
          StringUtils.join(titusRegion.getFeatureFlags(), ","),
          titusRegion.getAccount(),
          titusRegion.getName());
    }
  }

  // APIs
  // ------------------------------------------------------------------------------------------

  @Override
  public Job getJobAndAllRunningAndCompletedTasks(String jobId) {
    return new Job(
        grpcBlockingStub.findJob(JobId.newBuilder().setId(jobId).build()),
        getTasks(Arrays.asList(jobId), true).get(jobId));
  }

  @Override
  public Job findJobById(String jobId, boolean includeTasks) {
    return new Job(
        grpcBlockingStub.findJob(JobId.newBuilder().setId(jobId).build()),
        includeTasks ? getTasks(List.of(jobId), false).get(jobId) : Collections.emptyList());
  }

  @Override
  public Job findJobByName(String jobName, boolean includeTasks) {
    JobQuery.Builder jobQuery =
        JobQuery.newBuilder()
            .putFilteringCriteria("jobType", "SERVICE")
            .putFilteringCriteria("attributes", "source:spinnaker,name:" + jobName)
            .putFilteringCriteria("attributes.op", "and");

    List<Job> results = getJobs(jobQuery, includeTasks);
    return results.isEmpty() ? null : results.get(0);
  }

  @Override
  public Job findJobByName(String jobName) {
    return findJobByName(jobName, false);
  }

  @Override
  public List<Job> findJobsByApplication(String application) {
    JobQuery.Builder jobQuery =
        JobQuery.newBuilder()
            .putFilteringCriteria("appName", application)
            .putFilteringCriteria("jobType", "SERVICE");
    return getJobs(jobQuery, false);
  }

  @Override
  public String submitJob(SubmitJobRequest submitJobRequest) {
    JobDescription jobDescription = submitJobRequest.getJobDescription();
    if (jobDescription.getType() == null) {
      jobDescription.setType("service");
    }
    if (jobDescription.getUser() == null) {
      jobDescription.setUser("spinnaker@netflix.com");
    } else if (!jobDescription.getUser().contains("@")) {
      jobDescription.setUser(jobDescription.getUser() + "@netflix.com");
    }
    if (jobDescription.getJobGroupSequence() == null
        && "service".equals(jobDescription.getType())) {
      try {
        int sequence = Names.parseName(jobDescription.getName()).getSequence();
        jobDescription.setJobGroupSequence(String.format("v%03d", sequence));
      } catch (Exception e) {
        // fail silently if we can't get a job group sequence: This is normal if no prior jobs
        // exist.
      }
    }
    jobDescription.getLabels().put("name", jobDescription.getName());
    jobDescription.getLabels().put("source", "spinnaker");
    jobDescription.getLabels().put("spinnakerAccount", submitJobRequest.getCredentials());
    for (TitusJobCustomizer customizer : titusJobCustomizers) {
      customizer.customize(jobDescription);
    }
    return TitusClientAuthenticationUtil.attachCaller(grpcBlockingStub)
        .createJob(jobDescription.getGrpcJobDescriptor())
        .getId();
  }

  @Override
  public Task getTask(String taskId) {
    // return new
    // Task(grpcBlockingStub.findTask(com.netflix.titus.grpc.protogen.TaskId.newBuilder().setId(taskId).build()));
    return null;
  }

  @Override
  public void updateDisruptionBudget(JobDisruptionBudgetUpdateRequest request) {
    JobDisruptionBudget disruptionBudget =
        DisruptionBudgetHelper.convertJobDisruptionBudget(request.getDisruptionBudget());
    TitusClientAuthenticationUtil.attachCaller(grpcBlockingStub)
        .updateJobDisruptionBudget(
            JobDisruptionBudgetUpdate.newBuilder()
                .setDisruptionBudget(disruptionBudget)
                .setJobId(request.getJobId())
                .build());
  }

  @Override
  public void updateScalingProcesses(ServiceJobProcessesRequest serviceJobProcessesRequest) {
    TitusClientAuthenticationUtil.attachCaller(grpcBlockingStub)
        .updateJobProcesses(
            JobProcessesUpdate.newBuilder()
                .setServiceJobProcesses(
                    ServiceJobSpec.ServiceJobProcesses.newBuilder()
                        .setDisableDecreaseDesired(
                            serviceJobProcessesRequest
                                .getServiceJobProcesses()
                                .isDisableDecreaseDesired())
                        .setDisableIncreaseDesired(
                            serviceJobProcessesRequest
                                .getServiceJobProcesses()
                                .isDisableIncreaseDesired()))
                .setJobId(serviceJobProcessesRequest.getJobId())
                .build());
  }

  @Override
  public void resizeJob(ResizeJobRequest resizeJobRequest) {
    TitusClientAuthenticationUtil.attachCaller(grpcBlockingStub)
        .updateJobCapacity(
            JobCapacityUpdate.newBuilder()
                .setJobId(resizeJobRequest.getJobId())
                .setCapacity(
                    Capacity.newBuilder()
                        .setDesired(resizeJobRequest.getInstancesDesired())
                        .setMax(resizeJobRequest.getInstancesMax())
                        .setMin(resizeJobRequest.getInstancesMin()))
                .build());
  }

  @Override
  public void activateJob(ActivateJobRequest activateJobRequest) {
    TitusClientAuthenticationUtil.attachCaller(grpcBlockingStub)
        .updateJobStatus(
            JobStatusUpdate.newBuilder()
                .setId(activateJobRequest.getJobId())
                .setEnableStatus(activateJobRequest.getInService())
                .build());
  }

  @Override
  public void setAutoscaleEnabled(String jobId, boolean shouldEnable) {
    TitusClientAuthenticationUtil.attachCaller(grpcBlockingStub)
        .updateJobProcesses(
            JobProcessesUpdate.newBuilder()
                .setServiceJobProcesses(
                    ServiceJobSpec.ServiceJobProcesses.newBuilder()
                        .setDisableDecreaseDesired(!shouldEnable)
                        .setDisableIncreaseDesired(!shouldEnable)
                        .build())
                .setJobId(jobId)
                .build());
  }

  @Override
  public void terminateJob(TerminateJobRequest terminateJobRequest) {
    TitusClientAuthenticationUtil.attachCaller(grpcBlockingStub)
        .killJob(JobId.newBuilder().setId(terminateJobRequest.getJobId()).build());
  }

  @Override
  public void terminateTasksAndShrink(
      TerminateTasksAndShrinkJobRequest terminateTasksAndShrinkJob) {
    List<String> failedTasks = new ArrayList<>();
    terminateTasksAndShrinkJob
        .getTaskIds()
        .forEach(
            id -> {
              try {
                killTaskWithRetry(id, terminateTasksAndShrinkJob);
              } catch (Exception e) {
                failedTasks.add(id);
                log.error(
                    "Failed to terminate and shrink titus task {} in account {} and region {}",
                    id,
                    titusRegion.getAccount(),
                    titusRegion.getName(),
                    e);
              }
            });
    if (!failedTasks.isEmpty()) {
      throw new TitusException(
          "Failed to terminate and shrink titus tasks: " + StringUtils.join(failedTasks, ","));
    }
  }

  private void killTaskWithRetry(
      String id, TerminateTasksAndShrinkJobRequest terminateTasksAndShrinkJob) {
    retrySupport.retry(
        () -> {
          try {
            return TitusClientAuthenticationUtil.attachCaller(grpcBlockingStub)
                .killTask(
                    TaskKillRequest.newBuilder()
                        .setTaskId(id)
                        .setShrink(terminateTasksAndShrinkJob.isShrink())
                        .build());
          } catch (io.grpc.StatusRuntimeException e) {
            if (e.getStatus().getCode() == Status.Code.NOT_FOUND) {
              log.warn(
                  "Titus task {} not found, continuing with terminate tasks and shrink job request.",
                  id);
              return Empty.newBuilder().build();
            }
            throw e;
          }
        },
        3,
        1000,
        false);
  }

  @Override
  public Map logsDownload(String taskId) {
    return null;
  }

  @Override
  public TitusHealth getHealth() {
    return new TitusHealth(HealthStatus.HEALTHY);
  }

  private List<Job> getJobs(JobQuery.Builder jobQuery) {
    return getJobs(jobQuery, true);
  }

  private List<Job> getJobs(JobQuery.Builder jobQuery, boolean includeTasks) {
    List<com.netflix.titus.grpc.protogen.Job> grpcJobs = getJobsWithFilter(jobQuery);
    final Map<String, List<com.netflix.titus.grpc.protogen.Task>> tasks;

    if (includeTasks) {
      List<String> jobIds =
          grpcJobs.stream()
              .map(com.netflix.titus.grpc.protogen.Job::getId)
              .collect(Collectors.toList());
      tasks = getTasks(jobIds, false);
    } else {
      tasks = Collections.emptyMap();
    }
    return grpcJobs.stream()
        .map(grpcJob -> new Job(grpcJob, tasks.get(grpcJob.getId())))
        .collect(Collectors.toList());
  }

  @Override
  public List<Job> getAllJobsWithoutTasks() {
    JobQuery.Builder jobQuery =
        JobQuery.newBuilder()
            .putFilteringCriteria("jobType", "SERVICE")
            .putFilteringCriteria("attributes", "source:spinnaker");

    return getJobs(jobQuery, false);
  }

  @Override
  public Map<String, String> getAllJobNames() {
    JobQuery.Builder jobQuery =
        JobQuery.newBuilder()
            .putFilteringCriteria("jobType", "SERVICE")
            .putFilteringCriteria("attributes", "source:spinnaker")
            .addFields("id")
            .addFields("jobDescriptor.attributes.name");

    List<com.netflix.titus.grpc.protogen.Job> grpcJobs = getJobsWithFilter(jobQuery, 10000);

    return grpcJobs.stream()
        .collect(
            Collectors.toMap(
                com.netflix.titus.grpc.protogen.Job::getId,
                it -> it.getJobDescriptor().getAttributesOrDefault("name", "")));
  }

  @Override
  public Map<String, List<String>> getTaskIdsForJobIds() {
    String filterByStates = "Launched,StartInitiated,Started";

    TaskQuery.Builder taskQueryBuilder = TaskQuery.newBuilder();
    taskQueryBuilder
        .putFilteringCriteria("attributes", "source:spinnaker")
        .putFilteringCriteria("taskStates", filterByStates)
        .addFields("id")
        .addFields("jobId");

    List<com.netflix.titus.grpc.protogen.Task> grpcTasks = getTasksWithFilter(taskQueryBuilder);
    return grpcTasks.stream()
        .collect(
            Collectors.groupingBy(
                com.netflix.titus.grpc.protogen.Task::getJobId,
                mapping(com.netflix.titus.grpc.protogen.Task::getId, toList())));
  }

  @Override
  public Iterator<JobChangeNotification> observeJobs(ObserveJobsQuery observeJobsQuery) {
    return grpcNoDeadlineStub.observeJobs(observeJobsQuery);
  }

  private Map<String, List<com.netflix.titus.grpc.protogen.Task>> getTasks(
      List<String> jobIds, boolean includeDoneJobs) {
    TaskQuery.Builder taskQueryBuilder = TaskQuery.newBuilder();
    if (!jobIds.isEmpty()) {
      taskQueryBuilder.putFilteringCriteria(
          "jobIds", jobIds.stream().collect(Collectors.joining(",")));
    }
    taskQueryBuilder.putFilteringCriteria("attributes", "source:spinnaker");
    String filterByStates = "Launched,StartInitiated,Started";
    if (includeDoneJobs) {
      filterByStates = filterByStates + ",KillInitiated,Finished";
    }
    taskQueryBuilder.putFilteringCriteria("taskStates", filterByStates);

    List<com.netflix.titus.grpc.protogen.Task> tasks = getTasksWithFilter(taskQueryBuilder);
    return tasks.stream()
        .collect(Collectors.groupingBy(com.netflix.titus.grpc.protogen.Task::getJobId));
  }

  @Override
  public List<Task> getAllTasks() {
    TaskQuery.Builder taskQueryBuilder = TaskQuery.newBuilder();
    taskQueryBuilder.putFilteringCriteria("attributes", "source:spinnaker");
    String filterByStates = "Launched,StartInitiated,Started";
    taskQueryBuilder.putFilteringCriteria("taskStates", filterByStates);

    List<com.netflix.titus.grpc.protogen.Task> tasks = getTasksWithFilter(taskQueryBuilder);
    return tasks.stream().map(Task::new).collect(toList());
  }

  private List<com.netflix.titus.grpc.protogen.Job> getJobsWithFilter(
      JobQuery.Builder jobQueryBuilder) {
    return getJobsWithFilter(jobQueryBuilder, 1000);
  }

  private List<com.netflix.titus.grpc.protogen.Job> getJobsWithFilter(
      JobQuery.Builder jobQueryBuilder, Integer pageSize) {
    List<com.netflix.titus.grpc.protogen.Job> grpcJobs = new ArrayList<>();
    String cursor = "";
    boolean hasMore;
    do {
      if (cursor.isEmpty()) {
        jobQueryBuilder.setPage(Page.newBuilder().setPageSize(pageSize));
      } else {
        jobQueryBuilder.setPage(Page.newBuilder().setCursor(cursor).setPageSize(pageSize));
      }

      JobQuery criteria = jobQueryBuilder.build();
      JobQueryResult resultPage =
          TitusClientCompressionUtil.attachCaller(grpcBlockingStub).findJobs(criteria);
      grpcJobs.addAll(resultPage.getItemsList());
      cursor = resultPage.getPagination().getCursor();
      hasMore = resultPage.getPagination().getHasMore();
    } while (hasMore);
    return grpcJobs;
  }

  private List<com.netflix.titus.grpc.protogen.Task> getTasksWithFilter(
      TaskQuery.Builder taskQueryBuilder) {

    final int pageSize = 1000;
    List<com.netflix.titus.grpc.protogen.Task> grpcTasks = new ArrayList<>();

    TaskQueryResult taskResults;
    String cursor = "";
    boolean hasMore;

    do {
      if (cursor.isEmpty()) {
        taskQueryBuilder.setPage(Page.newBuilder().setPageSize(pageSize));
      } else {
        taskQueryBuilder.setPage(Page.newBuilder().setCursor(cursor).setPageSize(pageSize));
      }
      taskResults =
          TitusClientCompressionUtil.attachCaller(
                  grpcBlockingStub.withDeadlineAfter(FIND_TASKS_DEADLINE, TimeUnit.MILLISECONDS))
              .findTasks(taskQueryBuilder.build());
      grpcTasks.addAll(taskResults.getItemsList());
      cursor = taskResults.getPagination().getCursor();
      hasMore = taskResults.getPagination().getHasMore();
    } while (hasMore);
    return grpcTasks;
  }
}
