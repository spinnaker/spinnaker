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
import com.netflix.spinnaker.igor.polling.*;
import com.netflix.spinnaker.igor.service.BuildServices;
import com.netflix.spinnaker.igor.travis.client.model.Repo;
import com.netflix.spinnaker.igor.travis.client.model.v3.TravisBuildState;
import com.netflix.spinnaker.igor.travis.client.model.v3.V3Build;
import com.netflix.spinnaker.igor.travis.client.model.v3.V3Job;
import com.netflix.spinnaker.igor.travis.service.TravisBuildConverter;
import com.netflix.spinnaker.igor.travis.service.TravisService;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/** Monitors travis builds */
@Service
@ConditionalOnProperty("travis.enabled")
public class TravisBuildMonitor
    extends CommonPollingMonitor<
        TravisBuildMonitor.BuildDelta, TravisBuildMonitor.BuildPollingDelta> {

  private static final long BUILD_STARTED_AT_THRESHOLD = TimeUnit.SECONDS.toMillis(30);

  private final BuildCache buildCache;
  private final BuildServices buildServices;
  private final TravisProperties travisProperties;
  private final Optional<EchoService> echoService;

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
    final String master = ctx.partitionName;
    final TravisService travisService = (TravisService) buildServices.getService(master);

    List<BuildDelta> builds = new ArrayList<>();
    builds.addAll(trackedBuilds(master, travisService));
    builds.addAll(changedBuilds(master, travisService));

    BuildPollingDelta delta = new BuildPollingDelta();
    delta.setMaster(master);
    delta.setItems(builds);
    return delta;
  }

  @Override
  protected void commitDelta(BuildPollingDelta delta, boolean sendEvents) {
    final String master = delta.getMaster();
    final TravisService travisService = (TravisService) buildServices.getService(master);

    delta
        .getItems()
        .parallelStream()
        .forEach(
            item -> {
              V3Build build = item.getBuild();
              log.info(
                  "({}) Build update {} [running:{}]",
                  kv("master", master),
                  build.toString(),
                  build.getState().isRunning());
              if (build.getState().equals(TravisBuildState.passed)) {
                item.setGenericBuild(
                    travisService.getGenericBuild(
                        build, true)); // This also parses the log for artifacts
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

              if (sendEvents) {
                sendEventForBuild(item, item.getBranchedRepoSlug(), master);
              }
            });
  }

  @Override
  public String getName() {
    return "travisBuildMonitor";
  }

  public List<BuildDelta> trackedBuilds(String master, final TravisService travisService) {
    List<Map<String, String>> trackedBuilds = buildCache.getTrackedBuilds(master);
    log.info(
        "({}) Checking for updates on {} tracked builds",
        kv("master", master),
        trackedBuilds.size());

    List<V3Build> builds =
        trackedBuilds.stream()
            .map(build -> travisService.getV3Build(Integer.parseInt(build.get("buildId"))))
            .collect(Collectors.toList());

    return processBuilds(builds, master, travisService);
  }

  public List<BuildDelta> changedBuilds(final String master, final TravisService travisService) {
    log.info("({}) Checking for new builds", kv("master", master));

    long startTime = System.currentTimeMillis();
    List<Repo> repos = filterOutOldBuilds(travisService.getReposForAccounts());
    log.info(
        "({}) Took {}ms to retrieve {} repositories",
        kv("master", master),
        System.currentTimeMillis() - startTime,
        repos.size());
    List<BuildDelta> results =
        repos
            .parallelStream()
            .map(repo -> travisService.getBuilds(repo, 5))
            .flatMap(builds -> processBuilds(builds, master, travisService).stream())
            .collect(Collectors.toList());
    if (!results.isEmpty()) {
      log.info("({}) Found {} new builds", kv("master", master), results.size());
    }

    log.info(
        "({}) Last poll took {}ms", kv("master", master), System.currentTimeMillis() - startTime);
    if (travisProperties.getRepositorySyncEnabled()) {
      startTime = System.currentTimeMillis();
      travisService.syncRepos();
      log.info(
          "({}) repositorySync: Took {}ms to sync repositories",
          kv("master", master),
          System.currentTimeMillis() - startTime);
    }

    return results;
  }

  private List<BuildDelta> processBuilds(
      List<V3Build> builds, String master, TravisService travisService) {
    List<BuildDelta> results = new ArrayList<>();
    builds.stream()
        .map(build -> setTracking(build, master))
        .filter(filterNewBuildsPredicate())
        .forEach(
            build -> {
              String branchedRepoSlug = build.branchedRepoSlug();
              int cachedBuild =
                  buildCache.getLastBuild(master, branchedRepoSlug, build.getState().isRunning());
              GenericBuild genericBuild =
                  TravisBuildConverter.genericBuild(build, travisService.getBaseUrl());
              List<Integer> jobIds =
                  build.getJobs() != null
                      ? build.getJobs().stream().map(V3Job::getId).collect(Collectors.toList())
                      : Collections.emptyList();
              if (build.getNumber() > cachedBuild
                  && !build.spinnakerTriggered()
                  && travisService.isLogReady(jobIds)) {
                BuildDelta delta =
                    new BuildDelta()
                        .setBranchedRepoSlug(branchedRepoSlug)
                        .setBuild(build)
                        .setGenericBuild(genericBuild)
                        .setTravisBaseUrl(travisService.getBaseUrl())
                        .setCurrentBuildNum(build.getNumber())
                        .setPreviousBuildNum(cachedBuild);
                results.add(delta);
                buildCache.setLastBuild(
                    master,
                    branchedRepoSlug,
                    build.getNumber(),
                    build.getState().isRunning(),
                    buildCacheJobTTLSeconds());
              }
            });
    return results;
  }

  private V3Build setTracking(V3Build build, String master) {
    if (!filterNewBuildsPredicate().test(build)) {
      buildCache.setTracking(
          master, build.getRepository().getSlug(), build.getId(), getPollInterval() * 5);
      log.debug("({}) tracking set up for {}", kv("master", master), build.toString());
    } else {
      buildCache.deleteTracking(master, build.getRepository().getSlug(), build.getId());
      log.debug("({}) tracking deleted for {}", kv("master", master), build.toString());
    }
    return build;
  }

  private void sendEventForBuild(
      final BuildDelta buildDelta, final String branchedSlug, String master) {
    if (!buildDelta.getBuild().spinnakerTriggered()) {
      if (echoService.isPresent()) {
        log.info(
            "({}) pushing event for :"
                + branchedSlug
                + ":"
                + String.valueOf(buildDelta.getBuild().getNumber()),
            kv("master", master));

        GenericProject project = new GenericProject(branchedSlug, buildDelta.getGenericBuild());

        GenericBuildContent content = new GenericBuildContent();
        content.setProject(project);
        content.setMaster(master);
        content.setType("travis");

        GenericBuildEvent event = new GenericBuildEvent();
        event.setContent(content);

        echoService.get().postEvent(event);
      } else {
        log.warn("Cannot send build event notification: Echo is not configured");
        log.info(
            "({}) unable to push event for :"
                + branchedSlug
                + ":"
                + String.valueOf(buildDelta.getBuild().getNumber()),
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

  private Predicate<V3Build> filterNewBuildsPredicate() {
    /*
    NewBuildGracePeriodSeconds is here because the travis API needs some time in order to fully represent the build in
    the api. This can be overridden by travis.newBuildGracePeriod.
    */
    Instant threshold =
        Instant.now().minus(travisProperties.getNewBuildGracePeriodSeconds(), ChronoUnit.SECONDS);
    return build ->
        !build.getState().isRunning()
            || (build.getFinishedAt() != null && build.getFinishedAt().isBefore(threshold));
  }

  private List<Repo> filterOutOldBuilds(List<Repo> repos) {
    /*
    BUILD_STARTED_AT_THRESHOLD is here because the builds can be picked up by igor before lastBuildStartedAt is
    set. This means the TTL can be set in the BuildCache before lastBuildStartedAt, if that happens we need a
    grace threshold so that we don't resend the event to echo. The value of the threshold assumes that travis
    will set the lastBuildStartedAt within 30 seconds.
    */
    final Instant threshold =
        Instant.now()
            .minus(travisProperties.getCachedJobTTLDays(), ChronoUnit.DAYS)
            .plusMillis(BUILD_STARTED_AT_THRESHOLD);
    return repos.stream()
        .filter(
            repo ->
                repo.getLastBuildStartedAt() != null
                    && repo.getLastBuildStartedAt().isAfter(threshold))
        .collect(Collectors.toList());
  }

  @Override
  protected Integer getPartitionUpperThreshold(final String partition) {
    return travisProperties.getMasters().stream()
        .filter(host -> host.getName().equals(partition))
        .findAny()
        .map(TravisProperties.TravisHost::getItemUpperThreshold)
        .orElse(null);
  }

  public static class BuildPollingDelta implements PollingDelta<BuildDelta> {
    private String master;
    private List<BuildDelta> items;

    public String getMaster() {
      return master;
    }

    public BuildPollingDelta setMaster(String master) {
      this.master = master;
      return this;
    }

    public List<BuildDelta> getItems() {
      return items;
    }

    public BuildPollingDelta setItems(List<BuildDelta> items) {
      this.items = items;
      return this;
    }
  }

  public static class BuildDelta implements DeltaItem {
    private String branchedRepoSlug;
    private V3Build build;
    private GenericBuild genericBuild;
    private String travisBaseUrl;
    private int currentBuildNum;
    private int previousBuildNum;

    public String getBranchedRepoSlug() {
      return branchedRepoSlug;
    }

    public BuildDelta setBranchedRepoSlug(String branchedRepoSlug) {
      this.branchedRepoSlug = branchedRepoSlug;
      return this;
    }

    public V3Build getBuild() {
      return build;
    }

    public BuildDelta setBuild(V3Build build) {
      this.build = build;
      return this;
    }

    public GenericBuild getGenericBuild() {
      return genericBuild;
    }

    public BuildDelta setGenericBuild(GenericBuild genericBuild) {
      this.genericBuild = genericBuild;
      return this;
    }

    public String getTravisBaseUrl() {
      return travisBaseUrl;
    }

    public BuildDelta setTravisBaseUrl(String travisBaseUrl) {
      this.travisBaseUrl = travisBaseUrl;
      return this;
    }

    public int getCurrentBuildNum() {
      return currentBuildNum;
    }

    public BuildDelta setCurrentBuildNum(int currentBuildNum) {
      this.currentBuildNum = currentBuildNum;
      return this;
    }

    public int getPreviousBuildNum() {
      return previousBuildNum;
    }

    public BuildDelta setPreviousBuildNum(int previousBuildNum) {
      this.previousBuildNum = previousBuildNum;
      return this;
    }
  }
}
