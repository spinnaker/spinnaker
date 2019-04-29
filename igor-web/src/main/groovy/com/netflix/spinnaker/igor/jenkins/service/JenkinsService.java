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
import com.netflix.spinnaker.hystrix.SimpleJava8HystrixCommand;
import com.netflix.spinnaker.igor.build.model.GenericBuild;
import com.netflix.spinnaker.igor.build.model.GenericGitRevision;
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
import com.netflix.spinnaker.kork.web.exceptions.NotFoundException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.util.UriUtils;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import retrofit.RetrofitError;
import retrofit.client.Header;
import retrofit.client.Response;

@Slf4j
public class JenkinsService implements BuildOperations, BuildProperties {
  private final ObjectMapper objectMapper = new ObjectMapper();
  private final String groupKey;
  private final String serviceName;
  private final JenkinsClient jenkinsClient;
  private final Boolean csrf;
  private final RetrySupport retrySupport = new RetrySupport();
  private final Permissions permissions;

  public JenkinsService(
      String jenkinsHostId, JenkinsClient jenkinsClient, Boolean csrf, Permissions permissions) {
    this.serviceName = jenkinsHostId;
    this.groupKey = "jenkins-" + jenkinsHostId;
    this.jenkinsClient = jenkinsClient;
    this.csrf = csrf;
    this.permissions = permissions;
  }

  @Override
  public String getName() {
    return this.serviceName;
  }

  private String encode(String uri) {
    return UriUtils.encodeFragment(uri, "UTF-8");
  }

  public ProjectsList getProjects() {
    ProjectsList projectsList =
        new SimpleJava8HystrixCommand<>(
                groupKey, buildCommandKey("getProjects"), jenkinsClient::getProjects)
            .execute();

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
    return new SimpleJava8HystrixCommand<>(
            groupKey, buildCommandKey("getJobs"), jenkinsClient::getJobs)
        .execute();
  }

  public String getCrumb() {
    if (csrf) {
      Crumb crumb =
          new SimpleJava8HystrixCommand<>(
                  groupKey, buildCommandKey("getCrumb"), jenkinsClient::getCrumb)
              .execute();
      if (crumb != null) {
        return crumb.getCrumb();
      }
    }
    return null;
  }

  @Override
  public List<Build> getBuilds(String jobName) {
    return new SimpleJava8HystrixCommand<>(
            groupKey,
            buildCommandKey("getBuildList"),
            () -> jenkinsClient.getBuilds(encode(jobName)).getList())
        .execute();
  }

  public BuildDependencies getDependencies(String jobName) {
    return new SimpleJava8HystrixCommand<>(
            groupKey,
            buildCommandKey("getDependencies"),
            () -> jenkinsClient.getDependencies(encode(jobName)))
        .execute();
  }

  public Build getBuild(String jobName, Integer buildNumber) {
    return jenkinsClient.getBuild(encode(jobName), buildNumber);
  }

  @Override
  public GenericBuild getGenericBuild(String jobName, int buildNumber) {
    return getBuild(jobName, buildNumber).genericBuild(jobName);
  }

  @Override
  public int triggerBuildWithParameters(String job, Map<String, String> queryParameters) {
    Response response = buildWithParameters(job, queryParameters);
    if (response.getStatus() != 201) {
      throw new BuildJobError("Received a non-201 status when submitting job '" + job + "'");
    }

    log.info("Submitted build job '{}'", kv("job", job));
    String queuedLocation =
        response.getHeaders().stream()
            .filter(h -> h.getName() != null)
            .filter(h -> h.getName().toLowerCase().equals("location"))
            .map(Header::getValue)
            .findFirst()
            .orElseThrow(
                () ->
                    new QueuedJobDeterminationError(
                        "Could not find Location header for job '" + job + "'"));

    int lastSlash = queuedLocation.lastIndexOf('/');
    return Integer.parseInt(queuedLocation.substring(lastSlash + 1));
  }

  @Override
  public Permissions getPermissions() {
    return permissions;
  }

  private ScmDetails getGitDetails(String jobName, Integer buildNumber) {
    return retrySupport.retry(
        () ->
            new SimpleJava8HystrixCommand<>(
                    groupKey,
                    buildCommandKey("getGitDetails"),
                    () -> {
                      try {
                        return jenkinsClient.getGitDetails(encode(jobName), buildNumber);
                      } catch (RetrofitError e) {
                        // assuming that a conversion error is unlikely to succeed on retry
                        if (e.getKind() == RetrofitError.Kind.CONVERSION) {
                          log.warn(
                              "Unable to deserialize git details for build "
                                  + buildNumber
                                  + " of "
                                  + jobName,
                              e);
                          return null;
                        } else {
                          throw e;
                        }
                      }
                    })
                .execute(),
        10,
        1000,
        false);
  }

  public Build getLatestBuild(String jobName) {
    return new SimpleJava8HystrixCommand<>(
            groupKey,
            buildCommandKey("getLatestBuild"),
            () -> jenkinsClient.getLatestBuild(encode(jobName)))
        .execute();
  }

  public QueuedJob queuedBuild(Integer item) {
    try {
      return jenkinsClient.getQueuedItem(item);
    } catch (RetrofitError e) {
      if (e.getResponse() != null && e.getResponse().getStatus() == NOT_FOUND.value()) {
        throw new NotFoundException("Queued job '${item}' not found for master '${master}'.");
      }
      throw e;
    }
  }

  public Response build(String jobName) {
    return jenkinsClient.build(encode(jobName), "", getCrumb());
  }

  public Response buildWithParameters(String jobName, Map<String, String> queryParams) {
    return jenkinsClient.buildWithParameters(encode(jobName), queryParams, "", getCrumb());
  }

  public JobConfig getJobConfig(String jobName) {
    return jenkinsClient.getJobConfig(encode(jobName));
  }

  @Override
  @SuppressWarnings("unchecked")
  public Map<String, Object> getBuildProperties(String job, int buildNumber, String fileName) {
    if (StringUtils.isEmpty(fileName)) {
      return new HashMap<>();
    }
    Map<String, Object> map = new HashMap<>();
    try {
      String path = getArtifactPathFromBuild(job, buildNumber, fileName);
      try (InputStream propertyStream =
          this.getPropertyFile(job, buildNumber, path).getBody().in()) {
        if (fileName.endsWith(".yml") || fileName.endsWith(".yaml")) {
          Yaml yml = new Yaml(new SafeConstructor());
          map = (Map<String, Object>) yml.load(propertyStream);
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

  private String getArtifactPathFromBuild(String job, int buildNumber, String fileName) {
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
        2000,
        false);
  }

  private Response getPropertyFile(String jobName, Integer buildNumber, String fileName) {
    return jenkinsClient.getPropertyFile(encode(jobName), buildNumber, fileName);
  }

  public Response stopRunningBuild(String jobName, Integer buildNumber) {
    return jenkinsClient.stopRunningBuild(encode(jobName), buildNumber, "", getCrumb());
  }

  public Response stopQueuedBuild(String queuedBuild) {
    return jenkinsClient.stopQueuedBuild(queuedBuild, "", getCrumb());
  }

  /**
   * A CommandKey should be unique per group (to ensure broken circuits do not span Jenkins masters)
   */
  private String buildCommandKey(String id) {
    return groupKey + "-" + id;
  }

  @Override
  public BuildServiceProvider getBuildServiceProvider() {
    return BuildServiceProvider.JENKINS;
  }

  @Override
  public List<GenericGitRevision> getGenericGitRevisions(String job, int buildNumber) {
    ScmDetails scmDetails = getGitDetails(job, buildNumber);
    return scmDetails.genericGitRevisions();
  }
}
