/*
 * Copyright 2019 Pivotal, Inc.
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

package com.netflix.spinnaker.igor.concourse.service;

import static com.google.common.base.Strings.isNullOrEmpty;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.reducing;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;

import com.netflix.spinnaker.config.OkHttp3ClientConfiguration;
import com.netflix.spinnaker.fiat.model.resources.Permissions;
import com.netflix.spinnaker.igor.build.model.GenericArtifact;
import com.netflix.spinnaker.igor.build.model.GenericBuild;
import com.netflix.spinnaker.igor.build.model.GenericGitRevision;
import com.netflix.spinnaker.igor.build.model.JobConfiguration;
import com.netflix.spinnaker.igor.concourse.client.ConcourseClient;
import com.netflix.spinnaker.igor.concourse.client.model.Build;
import com.netflix.spinnaker.igor.concourse.client.model.Event;
import com.netflix.spinnaker.igor.concourse.client.model.Job;
import com.netflix.spinnaker.igor.concourse.client.model.Pipeline;
import com.netflix.spinnaker.igor.concourse.client.model.Resource;
import com.netflix.spinnaker.igor.concourse.client.model.Team;
import com.netflix.spinnaker.igor.config.ConcourseProperties;
import com.netflix.spinnaker.igor.model.BuildServiceProvider;
import com.netflix.spinnaker.igor.service.ArtifactDecorator;
import com.netflix.spinnaker.igor.service.BuildOperations;
import com.netflix.spinnaker.igor.service.BuildProperties;
import com.netflix.spinnaker.kork.retrofit.Retrofit2SyncCall;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import lombok.extern.slf4j.Slf4j;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;

@Slf4j
public class ConcourseService implements BuildOperations, BuildProperties {
  private final ConcourseProperties.Host host;
  private final ConcourseClient client;
  private final Optional<ArtifactDecorator> artifactDecorator;
  private final Permissions permissions;

  @Nullable private final Pattern resourceFilter;

  public ConcourseService(
      ConcourseProperties.Host host,
      Optional<ArtifactDecorator> artifactDecorator,
      OkHttp3ClientConfiguration okHttpClientConfig) {
    this(
        new ConcourseClient(
            host.getUrl(), host.getUsername(), host.getPassword(), okHttpClientConfig),
        host,
        artifactDecorator);
  }

  protected ConcourseService(
      ConcourseClient client,
      ConcourseProperties.Host host,
      Optional<ArtifactDecorator> artifactDecorator) {
    this.host = host;
    this.client = client;
    this.resourceFilter =
        host.getResourceFilterRegex() == null
            ? null
            : Pattern.compile(host.getResourceFilterRegex());
    this.artifactDecorator = artifactDecorator;
    this.permissions = host.getPermissions().build();
  }

  public String getMaster() {
    return "concourse-" + host.getName();
  }

  @Override
  public String getName() {
    return getMaster();
  }

  @Override
  public BuildServiceProvider getBuildServiceProvider() {
    return BuildServiceProvider.CONCOURSE;
  }

  @Override
  public Permissions getPermissions() {
    return permissions;
  }

  public Collection<Team> teams() {
    refreshTokenIfNecessary();
    return Retrofit2SyncCall.execute(client.getTeamService().teams()).stream()
        .filter(team -> host.getTeams() == null || host.getTeams().contains(team.getName()))
        .collect(toList());
  }

  public Collection<Pipeline> pipelines() {
    refreshTokenIfNecessary();
    return Retrofit2SyncCall.execute(client.getPipelineService().pipelines()).stream()
        .filter(
            pipeline -> host.getTeams() == null || host.getTeams().contains(pipeline.getTeamName()))
        .collect(toList());
  }

  public Collection<Job> getJobs() {
    refreshTokenIfNecessary();
    return Retrofit2SyncCall.execute(client.getJobService().jobs()).stream()
        .filter(job -> host.getTeams() == null || host.getTeams().contains(job.getTeamName()))
        .collect(toList());
  }

  @Override
  public List<GenericGitRevision> getGenericGitRevisions(String jobPath, GenericBuild build) {
    return build == null ? emptyList() : build.getGenericGitRevisions();
  }

  @Override
  public Map<String, ?> getBuildProperties(String jobPath, GenericBuild build, String fileName) {
    return build == null ? emptyMap() : build.getProperties();
  }

  @Nullable
  @Override
  public GenericBuild getGenericBuild(String jobPath, long buildNumber) {
    return getBuilds(jobPath, null).stream()
        .filter(build -> build.getNumber() == buildNumber)
        .sorted()
        .findFirst()
        .map(build -> getGenericBuild(jobPath, build, true))
        .orElse(null);
  }

  public GenericBuild getGenericBuild(String jobPath, Build b, boolean fetchResources) {
    Job job = toJob(jobPath);

    GenericBuild build = new GenericBuild();
    build.setId(b.getId());
    build.setBuilding(false);
    build.setNumber(b.getNumber());
    build.setResult(b.getResult());
    build.setName(job.getName());
    build.setFullDisplayName(job.getTeamName() + "/" + job.getPipelineName() + "/" + job.getName());
    build.setUrl(
        host.getUrl()
            + "/teams/"
            + job.getTeamName()
            + "/pipelines/"
            + job.getPipelineName()
            + "/jobs/"
            + job.getName()
            + "/builds/"
            + b.getDecimalNumber());
    build.setTimestamp(Long.toString(b.getStartTime() * 1000));

    if (!fetchResources) {
      return build;
    }

    Collection<Resource> resources = getResources(b.getId());

    // merge input and output metadata into one map for each resource
    Map<String, Map<String, String>> mergedMetadataByResourceName =
        resources.stream()
            .collect(
                groupingBy(
                    Resource::getName,
                    reducing(
                        emptyMap(),
                        Resource::getMetadata,
                        (m1, m2) -> {
                          Map<String, String> m1OrEmpty = m1 == null ? emptyMap() : m1;
                          Map<String, String> m2OrEmpty = m2 == null ? emptyMap() : m2;

                          Map<String, String> merged = new HashMap<>();
                          Stream.concat(
                                  m1OrEmpty.entrySet().stream(), m2OrEmpty.entrySet().stream())
                              .forEach(me -> merged.put(me.getKey(), me.getValue()));

                          return merged;
                        })));

    // extract git information from this particular named resource type
    resources.stream()
        .filter(r -> r.getType().equals("git"))
        .map(Resource::getName)
        .findAny()
        .ifPresent(
            gitResourceName -> {
              Map<String, String> git = mergedMetadataByResourceName.remove(gitResourceName);
              if (git != null && !git.isEmpty()) {
                String sha1 = git.get("commit");
                String message = git.get("message");
                String timestamp = git.get("committer_date");
                String branch =
                    isNullOrEmpty(git.get("branch")) ? sha1.substring(0, 7) : git.get("branch");

                build.setGenericGitRevisions(
                    Collections.singletonList(
                        GenericGitRevision.builder()
                            .committer(git.get("committer"))
                            .branch(branch)
                            .name(branch)
                            .message(message == null ? null : message.trim())
                            .sha1(sha1)
                            .timestamp(
                                timestamp == null
                                    ? null
                                    : ZonedDateTime.parse(
                                            timestamp,
                                            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss Z"))
                                        .toInstant())
                            .build()));
              }
            });

    if (!mergedMetadataByResourceName.isEmpty()) {
      build.setProperties(mergedMetadataByResourceName);
    }

    parseAndDecorateArtifacts(build, resources);

    return build;
  }

  private Collection<Resource> getResources(String buildId) {
    Map<String, Resource> resources =
        Retrofit2SyncCall.execute(client.getBuildService().plan(buildId)).getResources().stream()
            .filter(
                r ->
                    resourceFilter == null
                        || "git".equals(r.getType()) // there is a place for Git revision history on
                        // GenericBuild
                        || resourceFilter.matcher(r.getType()).matches())
            .collect(toMap(Resource::getId, Function.identity()));

    if (!resources.isEmpty()) {
      setResourceMetadata(buildId, resources);
    } else {
      log.warn("No resources retrieved for buildId: {}", buildId);
    }

    return resources.values();
  }

  /** Uses Concourse's build event stream to locate and populate resource metadata */
  private void setResourceMetadata(String buildId, Map<String, Resource> resources) {
    Flux<Event> events = client.getEventService().resourceEvents(buildId);
    CountDownLatch latch = new CountDownLatch(resources.size());

    Disposable eventStream =
        events
            .doOnNext(
                event -> {
                  log.debug("Event for build {}: {}", buildId, event);
                  Resource resource = resources.get(event.getResourceId());
                  if (resource != null) {
                    resource.setMetadata(event.getData().getMetadata());
                    latch.countDown();
                  }
                })
            .doOnComplete(
                () -> {
                  // if the event stream has ended, just count down the rest of the way
                  while (latch.getCount() > 0) {
                    latch.countDown();
                  }
                })
            .subscribe();

    try {
      latch.await();
    } catch (InterruptedException e) {
      log.warn("Unable to fully read event stream", e);
    } finally {
      eventStream.dispose();
    }
  }

  private void parseAndDecorateArtifacts(GenericBuild build, Collection<Resource> resources) {
    build.setArtifacts(getArtifactsFromResources(resources));
    artifactDecorator.ifPresent(decorator -> decorator.decorate(build));
  }

  private List<GenericArtifact> getArtifactsFromResources(Collection<Resource> resources) {
    return resources.stream()
        .map(r -> r.getMetadata().get("url"))
        .filter(Objects::nonNull)
        .map(ConcourseService::translateS3HttpUrl)
        .map(url -> new GenericArtifact(url, url, url))
        .collect(Collectors.toList());
  }

  private static String translateS3HttpUrl(String url) {
    if (url.startsWith("https://s3-")) {
      url = "s3://" + url.substring(url.indexOf('/', 8) + 1);
    }
    return url;
  }

  @Override
  public long triggerBuildWithParameters(String job, Map<String, String> queryParameters) {
    throw new UnsupportedOperationException("Triggering concourse builds not supported");
  }

  @Override
  public List<GenericBuild> getBuilds(String jobPath) {
    return getBuilds(jobPath, null).stream()
        .filter(Build::isSuccessful)
        .map(build -> getGenericBuild(jobPath, build, false))
        .collect(Collectors.toList());
  }

  @Override
  public JobConfiguration getJobConfig(String jobName) {
    throw new UnsupportedOperationException("getJobConfig is not yet implemented for Concourse");
  }

  public List<Build> getBuilds(String jobPath, @Nullable Long since) {
    Job job = toJob(jobPath);

    if (host.getTeams() != null && !host.getTeams().contains(job.getTeamName())) {
      return emptyList();
    }

    return Retrofit2SyncCall.execute(
            client
                .getBuildService()
                .builds(
                    job.getTeamName(),
                    job.getPipelineName(),
                    job.getName(),
                    host.getBuildLookbackLimit(),
                    since))
        .stream()
        .sorted()
        .collect(
            Collectors.toMap(
                Build::getNumber, Function.identity(), (b1, b2) -> b1, LinkedHashMap::new))
        .values()
        .stream()
        .collect(Collectors.toList());
  }

  public List<String> getResourceNames(String team, String pipeline) {
    return Retrofit2SyncCall.execute(client.getResourceService().resources(team, pipeline)).stream()
        .map(Resource::getName)
        .collect(toList());
  }

  private Job toJob(String jobPath) {
    String[] jobParts = jobPath.split("/");
    if (jobParts.length != 3) {
      throw new IllegalArgumentException("job must be in the format teamName/pipelineName/jobName");
    }

    Job job = new Job();
    job.setTeamName(jobParts[0]);
    job.setPipelineName(jobParts[1]);
    job.setName(jobParts[2]);

    return job;
  }

  /**
   * This is necessary until this is resolved: https://github.com/concourse/concourse/issues/3558
   */
  private void refreshTokenIfNecessary() {
    // returns a 401 on expired/invalid token, which because of retry logic causes the token to be
    // refreshed.
    client.userInfo();
  }
}
