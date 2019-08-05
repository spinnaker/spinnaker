/*
 * Copyright 2018 Schibsted ASA.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 *
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

package com.netflix.spinnaker.igor.travis;

import static net.logstash.logback.argument.StructuredArguments.kv;

import com.netflix.discovery.DiscoveryClient;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.igor.IgorConfigurationProperties;
import com.netflix.spinnaker.igor.build.BuildCache;
import com.netflix.spinnaker.igor.build.model.GenericBuild;
import com.netflix.spinnaker.igor.build.model.GenericProject;
import com.netflix.spinnaker.igor.config.TravisProperties;
import com.netflix.spinnaker.igor.history.EchoService;
import com.netflix.spinnaker.igor.history.model.GenericBuildContent;
import com.netflix.spinnaker.igor.history.model.GenericBuildEvent;
import com.netflix.spinnaker.igor.model.BuildServiceProvider;
import com.netflix.spinnaker.igor.polling.CommonPollingMonitor;
import com.netflix.spinnaker.igor.polling.DeltaItem;
import com.netflix.spinnaker.igor.polling.LockService;
import com.netflix.spinnaker.igor.polling.PollContext;
import com.netflix.spinnaker.igor.polling.PollingDelta;
import com.netflix.spinnaker.igor.service.BuildServices;
import com.netflix.spinnaker.igor.travis.client.model.v3.TravisBuildState;
import com.netflix.spinnaker.igor.travis.client.model.v3.V3Build;
import com.netflix.spinnaker.igor.travis.service.TravisService;
import com.netflix.spinnaker.security.AuthenticatedRequest;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.Builder;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/** Monitors travis builds */
@Service
@ConditionalOnProperty("travis.enabled")
public class TravisBuildMonitor
    extends CommonPollingMonitor<
        TravisBuildMonitor.BuildDelta, TravisBuildMonitor.BuildPollingDelta> {

  private final BuildCache buildCache;
  private final BuildServices buildServices;
  private final TravisProperties travisProperties;
  private final Optional<EchoService> echoService;

  public static final int TRACKING_TTL = (int) TimeUnit.HOURS.toMinutes(5);

  @Autowired
  public TravisBuildMonitor(
      IgorConfigurationProperties properties,
      Registry registry,
      Optional<DiscoveryClient> discoveryClient,
      BuildCache buildCache,
      BuildServices buildServices,
      TravisProperties travisProperties,
      Optional<EchoService> echoService,
      Optional<LockService> lockService) {
    super(properties, registry, discoveryClient, lockService);
    this.buildCache = buildCache;
    this.buildServices = buildServices;
    this.travisProperties = travisProperties;
    this.echoService = echoService;
  }

  @Override
  public void initialize() {
    migrateToNewBuildCache();
  }

  @Override
  public void poll(boolean sendEvents) {
    buildServices
        .getServiceNames(BuildServiceProvider.TRAVIS)
        .forEach(master -> pollSingle(new PollContext(master, !sendEvents)));
  }

  @Override
  protected BuildPollingDelta generateDelta(PollContext ctx) {
    final Instant startTime = Instant.now();
    final String master = ctx.partitionName;
    final TravisService travisService = (TravisService) buildServices.getService(master);

    List<BuildDelta> builds =
        travisService.getLatestBuilds().stream()
            .flatMap(build -> createBuildDelta(master, travisService, build))
            .collect(Collectors.toList());

    log.info(
        "({}) generateDelta: Took {}ms to generate polling delta",
        kv("master", master),
        Duration.between(startTime, Instant.now()).toMillis());

    return BuildPollingDelta.builder().master(master).items(builds).build();
  }

  @Override
  protected void commitDelta(BuildPollingDelta delta, boolean sendEvents) {
    Instant startTime = Instant.now();
    final String master = delta.getMaster();
    final TravisService travisService = (TravisService) buildServices.getService(master);

    delta.getItems().forEach(item -> processBuild(sendEvents, master, travisService, item));

    // Find id of processed builds
    Set<Integer> processedBuilds =
        delta.getItems().stream()
            .map(BuildDelta::getBuild)
            .map(V3Build::getId)
            .collect(Collectors.toSet());

    // Check for tracked builds that have fallen out of the tracking window (can happen for long
    // running Travis jobs)
    buildCache.getTrackedBuilds(master).stream()
        .mapToInt(build -> Integer.parseInt(build.get("buildId")))
        .filter(id -> !processedBuilds.contains(id))
        .mapToObj(travisService::getV3Build)
        .filter(
            build ->
                !build.getState().isRunning()
                    && (build.getState() != TravisBuildState.passed
                        || travisService.isLogReady(build)))
        .peek(
            build ->
                log.info(
                    "(master={}) Found tracked build missing from the API: {}:{}:{}. If you see this message a lot, "
                        + "consider increasing the 'travis.{}.numberOfJobs' property.",
                    master,
                    build.branchedRepoSlug(),
                    build.getNumber(),
                    build.getState(),
                    master))
        .flatMap(build -> createBuildDelta(master, travisService, build))
        .forEach(buildDelta -> processBuild(sendEvents, master, travisService, buildDelta));

    log.info(
        "({}) commitDelta: Took {}ms to commit polling delta",
        kv("master", master),
        Duration.between(startTime, Instant.now()).toMillis());

    if (travisProperties.isRepositorySyncEnabled()) {
      startTime = Instant.now();
      travisService.syncRepos();
      log.info(
          "({}) repositorySync: Took {}ms to sync repositories",
          kv("master", master),
          Duration.between(startTime, Instant.now()).toMillis());
    }
  }

  private Stream<? extends BuildDelta> createBuildDelta(
      String master, TravisService travisService, V3Build v3Build) {
    int lastBuild =
        buildCache.getLastBuild(master, v3Build.branchedRepoSlug(), v3Build.getState().isRunning());
    return Stream.of(v3Build)
        .filter(build -> !build.spinnakerTriggered())
        .filter(build -> build.getNumber() > lastBuild)
        .map(
            build ->
                BuildDelta.builder()
                    .branchedRepoSlug(build.branchedRepoSlug())
                    .build(build)
                    .genericBuild(
                        travisService.getGenericBuild(
                            build,
                            build.getState() == TravisBuildState.passed
                                && travisService.isLogReady(build)))
                    .travisBaseUrl(travisService.getBaseUrl())
                    .currentBuildNum(build.getNumber())
                    .previousBuildNum(lastBuild)
                    .build());
  }

  private void processBuild(
      boolean sendEvents, String master, TravisService travisService, BuildDelta item) {
    V3Build build = item.getBuild();
    switch (build.getState()) {
      case created:
      case started:
        buildCache.setTracking(
            master, build.getRepository().getSlug(), build.getId(), TRACKING_TTL);
        break;
      case passed:
        if (!travisService.isLogReady(build)) {
          break;
        }
        if (build.getNumber()
            > buildCache.getLastBuild(
                master, build.getRepository().getSlug(), build.getState().isRunning())) {
          buildCache.setLastBuild(
              master,
              build.getRepository().getSlug(),
              build.getNumber(),
              build.getState().isRunning(),
              buildCacheJobTTLSeconds());
          if (sendEvents) {
            sendEventForBuild(item, build.getRepository().getSlug(), master);
          }
        }

        if (build.getNumber() > item.previousBuildNum) {
          buildCache.setLastBuild(
              master,
              build.branchedRepoSlug(),
              build.getNumber(),
              build.getState().isRunning(),
              buildCacheJobTTLSeconds());
        }

        if (sendEvents) {
          sendEventForBuild(item, build.branchedRepoSlug(), master);
        }
        // Fall through
      case failed:
      case errored:
      case canceled:
        buildCache.deleteTracking(master, build.getRepository().getSlug(), build.getId());
    }
  }

  @Override
  public String getName() {
    return "travisBuildMonitor";
  }

  private void sendEventForBuild(
      final BuildDelta buildDelta, final String branchedSlug, String master) {
    if (!buildDelta.getBuild().spinnakerTriggered()) {
      if (echoService.isPresent()) {
        log.info(
            "({}) pushing event for: " + branchedSlug + ":" + buildDelta.getBuild().getNumber(),
            kv("master", master));

        GenericProject project = new GenericProject(branchedSlug, buildDelta.getGenericBuild());

        GenericBuildContent content = new GenericBuildContent();
        content.setProject(project);
        content.setMaster(master);
        content.setType("travis");

        GenericBuildEvent event = new GenericBuildEvent();
        event.setContent(content);

        AuthenticatedRequest.allowAnonymous(() -> echoService.get().postEvent(event));
      } else {
        log.warn("Cannot send build event notification: Echo is not configured");
        log.info(
            "({}) unable to push event for :"
                + branchedSlug
                + ":"
                + buildDelta.getBuild().getNumber(),
            kv("master", master));
        registry
            .counter(missedNotificationId.withTag("monitor", getClass().getSimpleName()))
            .increment();
      }
    }
  }

  private void migrateToNewBuildCache() {
    buildServices
        .getServiceNames(BuildServiceProvider.TRAVIS)
        .forEach(
            master ->
                buildCache
                    .getDeprecatedJobNames(master)
                    .forEach(
                        job -> {
                          Map<String, Object> oldBuild =
                              buildCache.getDeprecatedLastBuild(master, job);
                          if (!oldBuild.isEmpty()) {
                            int oldBuildNumber = (int) oldBuild.get("lastBuildLabel");
                            boolean oldBuildBuilding = (boolean) oldBuild.get("lastBuildBuilding");
                            int currentBuild =
                                buildCache.getLastBuild(master, job, oldBuildBuilding);
                            if (currentBuild < oldBuildNumber) {
                              log.info(
                                  "BuildCache migration {}:{}:{}:{}",
                                  kv("master", master),
                                  kv("job", job),
                                  kv("building", oldBuildBuilding),
                                  kv("buildNumber", oldBuildNumber));
                              buildCache.setLastBuild(
                                  master,
                                  job,
                                  oldBuildNumber,
                                  oldBuildBuilding,
                                  buildCacheJobTTLSeconds());
                            }
                          }
                        }));
  }

  private int buildCacheJobTTLSeconds() {
    return (int) TimeUnit.DAYS.toSeconds(travisProperties.getCachedJobTTLDays());
  }

  @Override
  protected Integer getPartitionUpperThreshold(final String partition) {
    return travisProperties.getMasters().stream()
        .filter(host -> host.getName().equals(partition))
        .findAny()
        .map(TravisProperties.TravisHost::getItemUpperThreshold)
        .orElse(null);
  }

  @Getter
  @Builder
  public static class BuildPollingDelta implements PollingDelta<BuildDelta> {
    private String master;
    private List<BuildDelta> items;
  }

  @Getter
  @Builder
  public static class BuildDelta implements DeltaItem {
    private String branchedRepoSlug;
    private V3Build build;
    private GenericBuild genericBuild;
    private String travisBaseUrl;
    private int currentBuildNum;
    private int previousBuildNum;
  }
}
