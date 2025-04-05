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

package com.netflix.spinnaker.igor.jenkins.service;

import static net.logstash.logback.argument.StructuredArguments.kv;
import static org.springframework.http.HttpStatus.NOT_FOUND;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.fiat.model.resources.Permissions;
import com.netflix.spinnaker.igor.build.model.GenericBuild;
import com.netflix.spinnaker.igor.build.model.GenericGitRevision;
import com.netflix.spinnaker.igor.build.model.UpdatedBuild;
import com.netflix.spinnaker.igor.exceptions.ArtifactNotFoundException;
import com.netflix.spinnaker.igor.exceptions.BuildJobError;
import com.netflix.spinnaker.igor.exceptions.QueuedJobDeterminationError;
import com.netflix.spinnaker.igor.jenkins.client.JenkinsClient;
import com.netflix.spinnaker.igor.jenkins.client.model.Build;
import com.netflix.spinnaker.igor.jenkins.client.model.BuildArtifact;
import com.netflix.spinnaker.igor.jenkins.client.model.BuildDependencies;
import com.netflix.spinnaker.igor.jenkins.client.model.JobConfig;
import com.netflix.spinnaker.igor.jenkins.client.model.JobList;
import com.netflix.spinnaker.igor.jenkins.client.model.Project;
import com.netflix.spinnaker.igor.jenkins.client.model.ProjectsList;
import com.netflix.spinnaker.igor.jenkins.client.model.QueuedJob;
import com.netflix.spinnaker.igor.jenkins.client.model.ScmDetails;
import com.netflix.spinnaker.igor.model.BuildServiceProvider;
import com.netflix.spinnaker.igor.model.Crumb;
import com.netflix.spinnaker.igor.service.BuildOperations;
import com.netflix.spinnaker.igor.service.BuildProperties;
import com.netflix.spinnaker.kork.core.RetrySupport;
import com.netflix.spinnaker.kork.retrofit.Retrofit2SyncCall;
import com.netflix.spinnaker.kork.retrofit.exceptions.SpinnakerConversionException;
import com.netflix.spinnaker.kork.retrofit.exceptions.SpinnakerHttpException;
import com.netflix.spinnaker.kork.retrofit.exceptions.SpinnakerNetworkException;
import com.netflix.spinnaker.kork.retrofit.exceptions.SpinnakerServerException;
import com.netflix.spinnaker.kork.web.exceptions.NotFoundException;
import com.netflix.spinnaker.security.AuthenticatedRequest;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import java.io.InputStream;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import okhttp3.ResponseBody;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.util.UriUtils;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import retrofit2.Response;

@Slf4j
public class JenkinsService implements BuildOperations, BuildProperties {
  private final ObjectMapper objectMapper = new ObjectMapper();
  private final String serviceName;
  private final JenkinsClient jenkinsClient;
  private final Boolean csrf;
  private final RetrySupport retrySupport = new RetrySupport();
  private final Permissions permissions;
  private final CircuitBreaker circuitBreaker;

  public JenkinsService(
      String jenkinsHostId,
      JenkinsClient jenkinsClient,
      Boolean csrf,
      Permissions permissions,
      CircuitBreakerRegistry circuitBreakerRegistry) {
    this.serviceName = jenkinsHostId;
    this.jenkinsClient = jenkinsClient;
    this.csrf = csrf;
    this.permissions = permissions;
    this.circuitBreaker =
        circuitBreakerRegistry.circuitBreaker(
            "jenkins-" + jenkinsHostId,
            CircuitBreakerConfig.custom()
                .ignoreException(
                    (e) -> {
                      return e instanceof SpinnakerHttpException
                          && ((SpinnakerHttpException) e).getResponseCode() == 404;
                    })
                .build());
  }

  @Override
  public String getName() {
    return this.serviceName;
  }

  private String encode(String uri) {
    return UriUtils.encodeFragment(uri, "UTF-8");
  }

  public ProjectsList getProjects() {
    return circuitBreaker.executeSupplier(
        () -> {
          ProjectsList projectsList =
              AuthenticatedRequest.allowAnonymous(
                  () -> Retrofit2SyncCall.execute(jenkinsClient.getProjects()));
          if (projectsList == null || projectsList.getList() == null) {
            return new ProjectsList();
          }
          List<Project> projects =
              projectsList.getList().stream()
                  .flatMap(this::recursiveGetProjects)
                  .collect(Collectors.toList());
          ProjectsList projectList = new ProjectsList();
          projectList.setList(projects);
          return projectList;
        });
  }

  private Stream<Project> recursiveGetProjects(Project project) {
    return recursiveGetProjects(project, "");
  }

  private Stream<Project> recursiveGetProjects(Project project, String prefix) {
    String projectName = prefix + project.getName();
    if (project.getList() == null || project.getList().isEmpty()) {
      project.setName(projectName);
      return Stream.of(project);
    }
    return project.getList().stream().flatMap(p -> recursiveGetProjects(p, projectName + "/job/"));
  }

  public JobList getJobs() {
    return circuitBreaker.executeSupplier(() -> Retrofit2SyncCall.execute(jenkinsClient.getJobs()));
  }

  public String getCrumb() {
    if (csrf) {
      return circuitBreaker.executeSupplier(
          () -> {
            Crumb crumb = Retrofit2SyncCall.execute(jenkinsClient.getCrumb());
            if (crumb != null) {
              return crumb.getCrumb();
            }
            return null;
          });
    }
    return null;
  }

  @Override
  public List<Build> getBuilds(String jobName) {
    return circuitBreaker.executeSupplier(
        () ->
            AuthenticatedRequest.allowAnonymous(
                    () -> Retrofit2SyncCall.execute(jenkinsClient.getBuilds(encode(jobName))))
                .getList());
  }

  public BuildDependencies getDependencies(String jobName) {
    return circuitBreaker.executeSupplier(
        () -> Retrofit2SyncCall.execute(jenkinsClient.getDependencies(encode(jobName))));
  }

  public Build getBuild(String jobName, Long buildNumber) {
    return circuitBreaker.executeSupplier(
        () -> Retrofit2SyncCall.execute(jenkinsClient.getBuild(encode(jobName), buildNumber)));
  }

  @Override
  public GenericBuild getGenericBuild(String jobName, long buildNumber) {
    return getBuild(jobName, buildNumber).genericBuild(jobName);
  }

  @Override
  public long triggerBuildWithParameters(String job, Map<String, String> queryParameters) {
    Response<ResponseBody> response = buildWithParameters(job, queryParameters);
    if (response.code() != 201) {
      throw new BuildJobError("Received a non-201 status when submitting job '" + job + "'");
    }

    log.info("Submitted build job '{}'", kv("job", job));
    String queuedLocation =
        response.headers().values("location").stream()
            .findFirst()
            .orElseThrow(
                () ->
                    new QueuedJobDeterminationError(
                        "Could not find Location header for job '" + job + "'"));

    int lastSlash = queuedLocation.lastIndexOf('/');
    return Long.parseLong(queuedLocation.substring(lastSlash + 1));
  }

  @Override
  public Permissions getPermissions() {
    return permissions;
  }

  private ScmDetails getGitDetails(String jobName, Long buildNumber) {
    return retrySupport.retry(
        () -> {
          try {
            return Retrofit2SyncCall.execute(
                jenkinsClient.getGitDetails(encode(jobName), buildNumber));
          } catch (SpinnakerConversionException e) {
            // assuming that a conversion error is unlikely to succeed on retry
            log.warn(
                "Unable to deserialize git details for build " + buildNumber + " of " + jobName, e);
            return null;
          }
        },
        10,
        Duration.ofMillis(1000),
        false);
  }

  public Build getLatestBuild(String jobName) {
    return circuitBreaker.executeSupplier(
        () -> Retrofit2SyncCall.execute(jenkinsClient.getLatestBuild(encode(jobName))));
  }

  @Override
  public QueuedJob queuedBuild(String master, long item) {
    try {
      return circuitBreaker.executeSupplier(
          () -> Retrofit2SyncCall.execute(jenkinsClient.getQueuedItem(item)));
    } catch (SpinnakerHttpException e) {
      if (e.getResponseCode() == NOT_FOUND.value()) {
        throw new NotFoundException(
            String.format("Queued job '%s' not found for master '%s'.", item, master));
      }
      throw e;
    }
  }

  public Response<ResponseBody> build(String jobName) {
    return circuitBreaker.executeSupplier(
        () -> Retrofit2SyncCall.execute(jenkinsClient.build(encode(jobName), "", getCrumb())));
  }

  public Response<ResponseBody> buildWithParameters(
      String jobName, Map<String, String> queryParams) {
    return circuitBreaker.executeSupplier(
        () ->
            Retrofit2SyncCall.execute(
                jenkinsClient.buildWithParameters(encode(jobName), queryParams, "", getCrumb())));
  }

  @Override
  public void updateBuild(String jobName, Long buildNumber, UpdatedBuild updatedBuild) {
    if (updatedBuild.getDescription() != null) {
      circuitBreaker.executeRunnable(
          () ->
              Retrofit2SyncCall.execute(
                  jenkinsClient.submitDescription(
                      encode(jobName), buildNumber, updatedBuild.getDescription(), getCrumb())));
    }
  }

  @Override
  public JobConfig getJobConfig(String jobName) {
    return circuitBreaker.executeSupplier(
        () -> Retrofit2SyncCall.execute(jenkinsClient.getJobConfig(encode(jobName))));
  }

  @Override
  public Map<String, Object> getBuildProperties(String job, GenericBuild build, String fileName) {
    if (StringUtils.isEmpty(fileName)) {
      return new HashMap<>();
    }
    Map<String, Object> map = new HashMap<>();
    try {
      String path = getArtifactPathFromBuild(job, build.getNumber(), fileName);
      try (InputStream propertyStream =
          this.getPropertyFile(job, build.getNumber(), path).byteStream()) {
        if (fileName.endsWith(".yml") || fileName.endsWith(".yaml")) {
          Yaml yml = new Yaml(new SafeConstructor());
          map = yml.load(propertyStream);
        } else if (fileName.endsWith(".json")) {
          map = objectMapper.readValue(propertyStream, new TypeReference<Map<String, Object>>() {});
        } else {
          Properties properties = new Properties();
          properties.load(propertyStream);
          map =
              properties.entrySet().stream()
                  .collect(Collectors.toMap(e -> e.getKey().toString(), Map.Entry::getValue));
        }
      }
    } catch (NotFoundException e) {
      throw e;
    } catch (Exception e) {
      log.error("Unable to get igorProperties '{}'", kv("job", job), e);
    }
    return map;
  }

  private String getArtifactPathFromBuild(String job, long buildNumber, String fileName) {
    return retrySupport.retry(
        () ->
            this.getBuild(job, buildNumber).getArtifacts().stream()
                .filter(a -> a.getFileName().equals(fileName))
                .map(BuildArtifact::getRelativePath)
                .findFirst()
                .orElseThrow(
                    () -> {
                      log.error(
                          "Unable to get igorProperties: Could not find build artifact matching requested filename '{}' on '{}' build '{}",
                          kv("fileName", fileName),
                          kv("master", serviceName),
                          kv("buildNumber", buildNumber));
                      return new ArtifactNotFoundException(serviceName, job, buildNumber, fileName);
                    }),
        5,
        Duration.ofMillis(2000),
        false);
  }

  private ResponseBody getPropertyFile(String jobName, Long buildNumber, String fileName) {
    return retrySupport.retry(
        () -> {
          try {
            return Retrofit2SyncCall.execute(
                jenkinsClient.getPropertyFile(encode(jobName), buildNumber, fileName));
          } catch (SpinnakerHttpException e) {
            if (e.getResponseCode() == 404 || e.getResponseCode() >= 500) {
              e.setRetryable(true); // 404 not retryable by default
              throw e; // retry on 404 and 5XX
            }
            e.setRetryable(false); // disable retry
            throw e;
          } catch (SpinnakerNetworkException e) {
            throw e; // retry on network issue
          } catch (SpinnakerServerException e) {
            e.setRetryable(false); // disable retry
            throw e;
          }
        },
        5,
        Duration.ofSeconds(2),
        false);
  }

  public ResponseBody stopRunningBuild(String jobName, Long buildNumber) {
    return circuitBreaker.executeSupplier(
        () ->
            Retrofit2SyncCall.execute(
                jenkinsClient.stopRunningBuild(encode(jobName), buildNumber, "", getCrumb())));
  }

  public ResponseBody stopQueuedBuild(String queuedBuild) {
    return circuitBreaker.executeSupplier(
        () ->
            Retrofit2SyncCall.execute(jenkinsClient.stopQueuedBuild(queuedBuild, "", getCrumb())));
  }

  @Override
  public BuildServiceProvider getBuildServiceProvider() {
    return BuildServiceProvider.JENKINS;
  }

  @Override
  public List<GenericGitRevision> getGenericGitRevisions(String job, GenericBuild build) {
    ScmDetails scmDetails = getGitDetails(job, build.getNumber());
    return scmDetails.genericGitRevisions();
  }
}
