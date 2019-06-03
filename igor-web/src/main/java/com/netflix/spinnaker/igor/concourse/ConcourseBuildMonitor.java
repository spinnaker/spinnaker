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

package com.netflix.spinnaker.igor.concourse;

import com.netflix.discovery.DiscoveryClient;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.igor.IgorConfigurationProperties;
import com.netflix.spinnaker.igor.build.model.GenericBuild;
import com.netflix.spinnaker.igor.build.model.GenericProject;
import com.netflix.spinnaker.igor.concourse.client.model.Build;
import com.netflix.spinnaker.igor.concourse.client.model.Job;
import com.netflix.spinnaker.igor.concourse.service.ConcourseService;
import com.netflix.spinnaker.igor.config.ConcourseProperties;
import com.netflix.spinnaker.igor.history.EchoService;
import com.netflix.spinnaker.igor.history.model.GenericBuildContent;
import com.netflix.spinnaker.igor.history.model.GenericBuildEvent;
import com.netflix.spinnaker.igor.polling.*;
import com.netflix.spinnaker.igor.service.BuildServices;
import com.netflix.spinnaker.security.AuthenticatedRequest;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty("concourse.enabled")
@Slf4j
public class ConcourseBuildMonitor
    extends CommonPollingMonitor<
        ConcourseBuildMonitor.JobDelta, ConcourseBuildMonitor.JobPollingDelta> {
  private final BuildServices buildServices;
  private final ConcourseCache cache;
  private final ConcourseProperties concourseProperties;
  private final Optional<EchoService> echoService;

  public ConcourseBuildMonitor(
      IgorConfigurationProperties properties,
      Registry registry,
      Optional<DiscoveryClient> discoveryClient,
      Optional<LockService> lockService,
      Optional<EchoService> echoService,
      BuildServices buildServices,
      ConcourseCache cache,
      ConcourseProperties concourseProperties) {
    super(properties, registry, discoveryClient, lockService);
    this.buildServices = buildServices;
    this.cache = cache;
    this.concourseProperties = concourseProperties;
    this.echoService = echoService;
  }

  @Override
  protected void initialize() {}

  @Override
  protected JobPollingDelta generateDelta(PollContext ctx) {
    ConcourseProperties.Host host =
        concourseProperties.getMasters().stream()
            .filter(h -> h.getName().equals(ctx.partitionName))
            .findFirst()
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "Unable to find concourse host with name '" + ctx.partitionName + "'"));

    ConcourseService concourseService = getService(host);
    return new JobPollingDelta(
        host.getName(),
        concourseService.getJobs().stream()
            .map(job -> jobDelta(host, job))
            .filter(Objects::nonNull)
            .collect(Collectors.toList()));
  }

  @Nullable
  private JobDelta jobDelta(ConcourseProperties.Host host, Job job) {
    String jobPath = job.toPath();
    ConcourseService concourseService = getService(host);

    final Long lastPollTs = cache.getLastPollCycleTimestamp(host, job);

    List<Build> builds =
        concourseService.getBuilds(jobPath, lastPollTs).stream()
            .filter(Build::isSuccessful)
            .collect(Collectors.toList());

    if (builds.isEmpty()) {
      return null;
    }

    long lastBuildStamp = builds.iterator().next().getStartTime();

    if (lastPollTs == null && !igorProperties.getSpinnaker().getBuild().isHandleFirstBuilds()) {
      cache.setLastPollCycleTimestamp(host, job, lastBuildStamp);
      return null;
    }

    Date upperBound = new Date(lastBuildStamp);
    long cursor = lastPollTs == null ? lastBuildStamp : lastPollTs;
    Date lowerBound = new Date(cursor);

    if (!igorProperties.getSpinnaker().getBuild().isProcessBuildsOlderThanLookBackWindow()) {
      builds = onlyInLookBackWindow(builds);
    }

    List<GenericBuild> genericBuilds =
        builds.stream()
            .map(build -> concourseService.getGenericBuild(jobPath, build, false))
            .collect(Collectors.toList());

    return new JobDelta(host, job, cursor, lowerBound, upperBound, genericBuilds);
  }

  private ConcourseService getService(ConcourseProperties.Host host) {
    return (ConcourseService) buildServices.getService("concourse-" + host.getName());
  }

  private List<Build> onlyInLookBackWindow(List<Build> builds) {
    long lookbackDate =
        System.currentTimeMillis()
            - (getPollInterval()
                + (igorProperties.getSpinnaker().getBuild().getLookBackWindowMins() * 60) * 1000);
    return builds.stream()
        .filter(b -> b.getStartTime() > lookbackDate)
        .collect(Collectors.toList());
  }

  @Override
  protected void commitDelta(JobPollingDelta delta, boolean sendEvents) {
    for (JobDelta jobDelta : delta.items) {
      for (GenericBuild build : jobDelta.getBuilds()) {
        boolean eventPosted =
            cache.getEventPosted(
                jobDelta.getHost(), jobDelta.getJob(), jobDelta.getCursor(), build.getNumber());
        if (!eventPosted && sendEvents) {
          sendEventForBuild(jobDelta.getHost(), jobDelta.getJob(), build);
          cache.setEventPosted(
              jobDelta.getHost(), jobDelta.getJob(), jobDelta.getCursor(), build.getNumber());
        }
      }
      cache.setLastPollCycleTimestamp(jobDelta.getHost(), jobDelta.getJob(), jobDelta.getCursor());
    }
  }

  private void sendEventForBuild(ConcourseProperties.Host host, Job job, GenericBuild build) {
    if (echoService.isPresent()) {
      log.info("({}) pushing event for : {}", host.getName(), build.getFullDisplayName());

      GenericProject project =
          new GenericProject(
              job.getTeamName() + "/" + job.getPipelineName() + "/" + job.getName(), build);

      GenericBuildContent content = new GenericBuildContent();
      content.setProject(project);
      content.setMaster("concourse-" + host.getName());
      content.setType("concourse");

      GenericBuildEvent event = new GenericBuildEvent();
      event.setContent(content);

      AuthenticatedRequest.allowAnonymous(() -> echoService.get().postEvent(event));
    } else {
      log.warn("Cannot send build event notification: Echo is not configured");
      log.info("({}) unable to push event for :" + build.getFullDisplayName());
      registry
          .counter(missedNotificationId.withTag("monitor", getClass().getSimpleName()))
          .increment();
    }
  }

  @Override
  public void poll(boolean sendEvents) {
    for (ConcourseProperties.Host host : concourseProperties.getMasters()) {
      pollSingle(new PollContext(host.getName(), !sendEvents));
    }
  }

  @Override
  public String getName() {
    return "concourseBuildMonitor";
  }

  @RequiredArgsConstructor
  @Getter
  static class JobDelta implements DeltaItem {
    private final ConcourseProperties.Host host;
    private final Job job;
    private final Long cursor;
    private final Date lowerBound;
    private final Date upperBound;
    private final List<GenericBuild> builds;
  }

  @RequiredArgsConstructor
  @Getter
  static class JobPollingDelta implements PollingDelta<JobDelta> {
    private final String name;
    private final List<JobDelta> items;
  }
}
