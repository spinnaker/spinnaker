/*
 * Copyright 2020 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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

package com.netflix.spinnaker.igor.jenkins;

import static java.lang.String.format;
import static net.logstash.logback.argument.StructuredArguments.kv;
import static org.springframework.http.HttpStatus.NOT_FOUND;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.netflix.spinnaker.fiat.model.resources.Permissions;
import com.netflix.spinnaker.hystrix.SimpleJava8HystrixCommand;
import com.netflix.spinnaker.igor.build.model.GenericBuild;
import com.netflix.spinnaker.igor.build.model.GenericGitRevision;
import com.netflix.spinnaker.igor.exceptions.ArtifactNotFoundException;
import com.netflix.spinnaker.igor.exceptions.BuildJobError;
import com.netflix.spinnaker.igor.exceptions.QueuedJobDeterminationError;
import com.netflix.spinnaker.igor.jenkins.client.JenkinsClient;
import com.netflix.spinnaker.igor.jenkins.client.model.*;
import com.netflix.spinnaker.igor.jenkins.exceptions.InvalidJobParameterException;
import com.netflix.spinnaker.igor.service.*;
import com.netflix.spinnaker.kork.core.RetrySupport;
import com.netflix.spinnaker.kork.exceptions.SpinnakerException;
import com.netflix.spinnaker.kork.web.exceptions.NotFoundException;
import java.io.InputStream;
import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.util.UriUtils;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import retrofit.RetrofitError;
import retrofit.client.Header;
import retrofit.client.Response;

@Slf4j
public class JenkinsService
    implements BuildProperties, BuildQueueOperations<QueuedJob>, JobNamesProvider {

  private static final Splitter QUEUED_BUILD_SPLITTER = Splitter.on("/");

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

  @Override
  public List<String> getJobNames() {
    return recursiveJobNames(getJobs().getList(), null);
  }

  private static List<String> recursiveJobNames(List<Job> jobs, String prefix) {
    List<String> jobNames = new ArrayList<>();

    final String pre = (Strings.isNullOrEmpty(prefix)) ? null : prefix + "/job/";
    jobs.forEach(
        job -> {
          String qualifiedName;
          if (pre == null) {
            qualifiedName = job.getName();
          } else {
            qualifiedName = pre + job.getName();
          }

          if (job.getList() == null || job.getList().isEmpty()) {
            jobNames.add(qualifiedName);
          } else {
            jobNames.addAll(recursiveJobNames(job.getList(), qualifiedName));
          }
        });

    return jobNames;
  }

  private JobList getJobs() {
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
    JobConfig jobConfig = getJobConfig(job);
    if (!jobConfig.isBuildable()) {
      throw new BuildJobError(format("Job '%s' is not buildable. It may be disabled.", job));
    }

    if (jobConfig.getParameterDefinitionList() != null
        && !jobConfig.getParameterDefinitionList().isEmpty()) {
      validateJobParameters(jobConfig, queryParameters);
    }

    Response response;
    if (!queryParameters.isEmpty()
        && jobConfig.getParameterDefinitionList() != null
        && !jobConfig.getParameterDefinitionList().isEmpty()) {
      response = buildWithParameters(job, queryParameters);
    } else if (queryParameters.isEmpty()
        && jobConfig.getParameterDefinitionList() != null
        && !jobConfig.getParameterDefinitionList().isEmpty()) {
      // account for when you just want to fire a job with the default parameter values by adding a
      // dummy param
      response = buildWithParameters(job, Collections.singletonMap("startedBy", "igor"));
    } else if (queryParameters.isEmpty()
        && (jobConfig.getParameterDefinitionList() == null
            || jobConfig.getParameterDefinitionList().isEmpty())) {
      response = build(job);
    } else {
      // Jenkins will reject the build, so don't even try
      // we should throw a BuildJobError, but I get a bytecode error : java.lang.VerifyError: Bad
      // <init> method call from inside of a branch
      throw new RuntimeException(
          format("job : %s, passing params to a job which doesn't need them", job));
    }

    return getBuildNumberFromResponse(job, response);
  }

  static int getBuildNumberFromResponse(String job, Response response) {
    if (response.getStatus() != 201) {
      // TODO(rz): How to determine master?
      throw new BuildJobError(
          format("Received a non-201 status when submitting job '%s' to master 'unknown'", job));
    }

    log.info("Submitted build job '{}'", kv("job", job));
    Header locationHeader =
        response.getHeaders().stream()
            .filter(it -> it.getName().toLowerCase().equals("location"))
            .findFirst()
            .orElseThrow(
                () ->
                    new QueuedJobDeterminationError(
                        format("Could not find Location header for job '%s'", job)));
    String queuedLocation = locationHeader.getValue();

    String[] parts =
        StreamSupport.stream(QUEUED_BUILD_SPLITTER.split(queuedLocation).spliterator(), false)
            .filter(it -> !Strings.isNullOrEmpty(it))
            .toArray(String[]::new);

    return Integer.parseInt(parts[parts.length - 1]);
  }

  static void validateJobParameters(JobConfig jobConfig, Map<String, String> requestParams) {
    if (jobConfig.getParameterDefinitionList() == null) {
      return;
    }

    jobConfig
        .getParameterDefinitionList()
        .forEach(
            (parameterDefinition) -> {
              String matchingParam = requestParams.get(parameterDefinition.getName());
              if (matchingParam != null
                  && "ChoiceParameterDefinition".equals(parameterDefinition.type)
                  && parameterDefinition.choices != null
                  && !parameterDefinition.choices.contains(matchingParam)) {
                throw new InvalidJobParameterException(
                    format(
                        "'%s' is not a valid choice for '%s'. Valid choices are: %s",
                        matchingParam,
                        parameterDefinition.name,
                        Joiner.on(", ").join(parameterDefinition.choices)));
              }
            });
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

  // TODO(rz): Unused?
  public Build getLatestBuild(String jobName) {
    return new SimpleJava8HystrixCommand<>(
            groupKey,
            buildCommandKey("getLatestBuild"),
            () -> jenkinsClient.getLatestBuild(encode(jobName)))
        .execute();
  }

  @Override
  public QueuedJob getQueuedBuild(String queueId) {
    try {
      return jenkinsClient.getQueuedItem(Integer.valueOf(queueId));
    } catch (RetrofitError e) {
      if (e.getResponse() != null && e.getResponse().getStatus() == 404) {
        throw new NotFoundException(
            format("Queued job '%s' not found for master '%s'.", queueId, groupKey));
      }
      throw e;
    }
  }

  @Override
  public void stopQueuedBuild(String jobName, String queueId, int buildNumber) {
    String crumb = getCrumb();

    // Jobs that haven't been started yet won't have a buildNumber
    // (They're still in the queue). We use 0 to denote that case
    if (buildNumber != 0) {
      jenkinsClient.stopRunningBuild(encode(jobName), buildNumber, "", crumb);
    }

    // The jenkins api for removing a job from the queue
    // (http://<Jenkins_URL>/queue/cancelItem?id=<queuedBuild>)
    // always returns a 404. This try catch block insures that the exception is eaten instead
    // of being handled by the handleOtherException handler and returning a 500 to orca
    try {
      jenkinsClient.stopQueuedBuild(queueId, "", crumb);
      return;
    } catch (RetrofitError e) {
      if (e.getResponse() != null && e.getResponse().getStatus() != NOT_FOUND.value()) {
        throw e;
      }
    }
    jenkinsClient.stopQueuedBuild(queueId, "", crumb);
  }

  @Override
  public void stopRunningBuild(String jobName, int buildNumber) {
    jenkinsClient.stopRunningBuild(encode(jobName), buildNumber, "", getCrumb());
  }

  private Response build(String jobName) {
    return jenkinsClient.build(encode(jobName), "", getCrumb());
  }

  private Response buildWithParameters(String jobName, Map<String, String> queryParams) {
    return jenkinsClient.buildWithParameters(encode(jobName), queryParams, "", getCrumb());
  }

  @Override
  public JobConfig getJobConfig(String jobName) {
    return jenkinsClient.getJobConfig(encode(jobName));
  }

  @Override
  public Map<String, Object> getBuildProperties(String job, GenericBuild build, String fileName) {
    if (StringUtils.isEmpty(fileName)) {
      return new HashMap<>();
    }

    try {
      String path = getArtifactPathFromBuild(job, build.getNumber(), fileName);
      try (InputStream propertyStream =
          this.getPropertyFile(job, build.getNumber(), path).getBody().in()) {
        if (fileName.endsWith(".yml") || fileName.endsWith(".yaml")) {
          Yaml yml = new Yaml(new SafeConstructor());
          return yml.load(propertyStream);
        } else if (fileName.endsWith(".json")) {
          return objectMapper.readValue(
              propertyStream, new TypeReference<Map<String, Object>>() {});
        } else {
          Properties properties = new Properties();
          properties.load(propertyStream);
          return properties.entrySet().stream()
              .collect(Collectors.toMap(e -> e.getKey().toString(), Map.Entry::getValue));
        }
      }
    } catch (NotFoundException e) {
      throw e;
    } catch (Exception e) {
      log.error("Unable to get igorProperties '{}'", kv("job", job), e);
    }

    return new HashMap<>();
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
    return retrySupport.retry(
        () -> {
          try {
            return jenkinsClient.getPropertyFile(encode(jobName), buildNumber, fileName);
          } catch (RetrofitError e) {
            // do not retry on client/deserialization error
            if (e.getKind() == RetrofitError.Kind.CONVERSION
                || (e.getResponse().getStatus() >= 400 && e.getResponse().getStatus() < 500)) {
              SpinnakerException ex = new SpinnakerException(e);
              ex.setRetryable(false);
              throw ex;
            }
            throw e;
          }
        },
        5,
        Duration.ofSeconds(2),
        false);
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
  public List<GenericGitRevision> getGenericGitRevisions(String job, GenericBuild build) {
    ScmDetails scmDetails = getGitDetails(job, build.getNumber());
    return scmDetails.genericGitRevisions();
  }
}
