/*
 * Copyright 2017 Netflix, Inc.
 * Copyright 2022 Redbox Entertainment, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.spinnaker.igor.gitlabci.service;

import static net.logstash.logback.argument.StructuredArguments.kv;

import com.netflix.spinnaker.fiat.model.resources.Permissions;
import com.netflix.spinnaker.igor.build.model.GenericBuild;
import com.netflix.spinnaker.igor.build.model.GenericGitRevision;
import com.netflix.spinnaker.igor.build.model.JobConfiguration;
import com.netflix.spinnaker.igor.build.model.Result;
import com.netflix.spinnaker.igor.config.GitlabCiProperties;
import com.netflix.spinnaker.igor.gitlabci.client.GitlabCiClient;
import com.netflix.spinnaker.igor.gitlabci.client.model.*;
import com.netflix.spinnaker.igor.model.BuildServiceProvider;
import com.netflix.spinnaker.igor.service.BuildOperations;
import com.netflix.spinnaker.igor.service.BuildProperties;
import com.netflix.spinnaker.igor.service.StoppableBuildService;
import com.netflix.spinnaker.igor.travis.client.logparser.PropertyParser;
import com.netflix.spinnaker.kork.core.RetrySupport;
import com.netflix.spinnaker.kork.retrofit.Retrofit2SyncCall;
import com.netflix.spinnaker.kork.retrofit.exceptions.SpinnakerHttpException;
import com.netflix.spinnaker.kork.retrofit.exceptions.SpinnakerNetworkException;
import com.netflix.spinnaker.kork.retrofit.exceptions.SpinnakerServerException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@Slf4j
public class GitlabCiService implements BuildOperations, BuildProperties, StoppableBuildService {
  private final String name;
  private final GitlabCiClient client;
  private final GitlabCiProperties.GitlabCiHost hostConfig;
  private final Permissions permissions;
  private final RetrySupport retrySupport = new RetrySupport();

  public GitlabCiService(
      GitlabCiClient client,
      String name,
      GitlabCiProperties.GitlabCiHost hostConfig,
      Permissions permissions) {
    this.client = client;
    this.name = name;
    this.hostConfig = hostConfig;
    this.permissions = permissions;
  }

  @Override
  public String getName() {
    return name;
  }

  @Override
  public BuildServiceProvider getBuildServiceProvider() {
    return BuildServiceProvider.GITLAB_CI;
  }

  @Override
  public List<GenericGitRevision> getGenericGitRevisions(String job, GenericBuild build) {
    throw new UnsupportedOperationException();
  }

  @Override
  public GenericBuild getGenericBuild(String projectId, long pipelineId) {
    Project project = Retrofit2SyncCall.execute(client.getProject(projectId));
    if (project == null) {
      log.error("Could not find Gitlab CI Project with projectId={}", projectId);
      return null;
    }
    Pipeline pipeline = Retrofit2SyncCall.execute(client.getPipeline(projectId, pipelineId));
    if (pipeline == null) {
      return null;
    }
    return GitlabCiPipelineUtils.genericBuild(
        pipeline, this.hostConfig.getAddress(), project.getPathWithNamespace());
  }

  @Override
  public List<Pipeline> getBuilds(String job) {
    return Retrofit2SyncCall.execute(
        this.client.getPipelineSummaries(job, this.hostConfig.getDefaultHttpPageLength()));
  }

  @Override
  public JobConfiguration getJobConfig(String jobName) {
    throw new UnsupportedOperationException("getJobConfig is not yet implemented for Gitlab CI");
  }

  /**
   * For GitLab CI, there is no queue concept. The trigger returns the pipeline ID directly, so we
   * simply return it as the build number to satisfy MonitorQueuedJenkinsJobTask.
   */
  @Override
  public Object queuedBuild(String master, long item) {
    return Map.of("number", item);
  }

  @Override
  public long triggerBuildWithParameters(String job, Map<String, String> queryParameters) {
    String ref = queryParameters.getOrDefault("ref", "main");
    String triggerToken = this.hostConfig.getTriggerToken();
    if (triggerToken == null || triggerToken.isEmpty()) {
      throw new IllegalStateException(
          "Cannot trigger GitLab CI pipeline: triggerToken is not configured for master '"
              + this.name
              + "'");
    }
    Pipeline pipeline = Retrofit2SyncCall.execute(client.triggerPipeline(job, triggerToken, ref));
    log.info(
        "Triggered GitLab CI pipeline {} for {} on ref {}",
        kv("pipelineId", pipeline.getId()),
        kv("projectId", job),
        kv("ref", ref));
    return pipeline.getId();
  }

  /**
   * Cancel a running GitLab CI pipeline.
   *
   * @param projectId The GitLab project ID
   * @param pipelineId The pipeline ID to cancel
   * @return The pipeline object after cancellation
   */
  public Pipeline cancelPipeline(String projectId, long pipelineId) {
    Pipeline pipeline = Retrofit2SyncCall.execute(client.cancelPipeline(projectId, pipelineId));
    log.info(
        "Cancelled GitLab CI pipeline {} for {}",
        kv("pipelineId", pipelineId),
        kv("projectId", projectId));
    return pipeline;
  }

  /**
   * Implements {@link StoppableBuildService#stopRunningBuild(String, long)} by cancelling the
   * GitLab CI pipeline. In the GitLab CI context, jobName maps to the project ID and buildNumber
   * maps to the pipeline ID.
   */
  @Override
  public void stopRunningBuild(String jobName, long buildNumber) {
    cancelPipeline(jobName, buildNumber);
  }

  @Override
  public Permissions getPermissions() {
    return permissions;
  }

  public List<Project> getProjects() {
    return getProjectsRec(new ArrayList<>(), 1).parallelStream()
        // Ignore projects that don't have Gitlab CI enabled.  It is not possible to filter this
        // using the GitLab
        // API. We need to filter it after retrieving all projects
        .filter(project -> project.getBuildsAccessLevel().equals("enabled"))
        .collect(Collectors.toList());
  }

  @Override
  public Map<String, Object> getBuildProperties(String job, GenericBuild build, String fileName) {
    if (StringUtils.isEmpty(fileName)) {
      return new HashMap<>();
    }

    return this.getPropertyFileFromLog(job, build.getNumber());
  }

  // Gets a pipeline's jobs along with any child pipeline jobs (bridges)
  private List<Job> getJobsWithBridges(String projectId, Long pipelineId) {
    List<Job> jobs = Retrofit2SyncCall.execute(this.client.getJobs(projectId, pipelineId));
    List<Bridge> bridges = Retrofit2SyncCall.execute(this.client.getBridges(projectId, pipelineId));
    bridges.parallelStream()
        .filter(
            bridge -> {
              // Filter out any child pipelines that failed or are still in-progress
              Pipeline parent = bridge.getDownstreamPipeline();
              return parent != null
                  && GitlabCiResultConverter.getResultFromGitlabCiState(parent.getStatus())
                      == Result.SUCCESS;
            })
        .forEach(
            bridge -> {
              jobs.addAll(
                  Retrofit2SyncCall.execute(
                      this.client.getJobs(projectId, bridge.getDownstreamPipeline().getId())));
            });
    return jobs;
  }

  private Map<String, Object> getPropertyFileFromLog(String projectId, Long pipelineId) {
    Map<String, Object> properties = new HashMap<>();
    return retrySupport.retry(
        () -> {
          try {
            Pipeline pipeline =
                Retrofit2SyncCall.execute(this.client.getPipeline(projectId, pipelineId));
            PipelineStatus status = pipeline.getStatus();
            if (status != PipelineStatus.running) {
              log.error(
                  "Unable to get GitLab build properties, pipeline '{}' in project '{}' has status {}",
                  kv("pipeline", pipelineId),
                  kv("project", projectId),
                  kv("status", status));
            }
            // Pipelines logs are stored within each stage (job), loop all jobs of this pipeline
            // and any jobs of child pipeline's to parse all logs for the pipeline
            List<Job> jobs = getJobsWithBridges(projectId, pipelineId);
            for (Job job : jobs) {
              InputStream logStream =
                  Retrofit2SyncCall.execute(this.client.getJobLog(projectId, job.getId()))
                      .source()
                      .inputStream();
              String log = new String(logStream.readAllBytes(), StandardCharsets.UTF_8);
              Map<String, Object> jobProperties = PropertyParser.extractPropertiesFromLog(log);
              properties.putAll(jobProperties);
            }

            return properties;

          } catch (SpinnakerNetworkException e) {
            // retry on network issue
            throw e;
          } catch (SpinnakerHttpException e) {
            // retry on 404 and 5XX
            if (e.getResponseCode() == 404 || e.getResponseCode() >= 500) {
              e.setRetryable(true); // 404 not retryable by default
              throw e;
            }
            e.setRetryable(false);
            throw e;
          } catch (SpinnakerServerException e) {
            // do not retry
            e.setRetryable(false);
            throw e;
          } catch (IOException e) {
            log.error("Error while parsing GitLab CI log to build properties", e);
            return properties;
          }
        },
        this.hostConfig.getHttpRetryMaxAttempts(),
        Duration.ofSeconds(this.hostConfig.getHttpRetryWaitSeconds()),
        this.hostConfig.getHttpRetryExponentialBackoff());
  }

  public List<Pipeline> getPipelines(final Project project, int pageSize) {
    return Retrofit2SyncCall.execute(
        client.getPipelineSummaries(String.valueOf(project.getId()), pageSize));
  }

  public List<Pipeline> getPipelines(final Project project) {
    return getPipelines(project, this.hostConfig.getDefaultHttpPageLength());
  }

  public String getAddress() {
    return this.hostConfig.getAddress();
  }

  private List<Project> getProjectsRec(List<Project> projects, int page) {
    List<Project> slice =
        Retrofit2SyncCall.execute(
            client.getProjects(
                hostConfig.getLimitByMembership(),
                hostConfig.getLimitByOwnership(),
                page,
                hostConfig.getDefaultHttpPageLength()));
    if (slice.isEmpty()) {
      return projects;
    } else {
      projects.addAll(slice);
      return getProjectsRec(projects, page + 1);
    }
  }
}
