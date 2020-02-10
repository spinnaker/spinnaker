package com.netflix.spinnaker.igor.jenkins;

import static net.logstash.logback.argument.StructuredArguments.kv;

import com.netflix.discovery.DiscoveryClient;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.igor.IgorConfigurationProperties;
import com.netflix.spinnaker.igor.config.JenkinsProperties;
import com.netflix.spinnaker.igor.history.EchoService;
import com.netflix.spinnaker.igor.history.model.JenkinsBuildContent;
import com.netflix.spinnaker.igor.history.model.JenkinsBuildEvent;
import com.netflix.spinnaker.igor.jenkins.client.model.Build;
import com.netflix.spinnaker.igor.jenkins.client.model.Project;
import com.netflix.spinnaker.igor.jenkins.client.model.ProjectsList;
import com.netflix.spinnaker.igor.polling.*;
import com.netflix.spinnaker.igor.service.BuildServiceProvider;
import com.netflix.spinnaker.igor.service.BuildServices;
import com.netflix.spinnaker.security.AuthenticatedRequest;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import retrofit.RetrofitError;

/** Monitors new jenkins builds */
@Slf4j
@Service
@SuppressWarnings("CatchException")
@ConditionalOnProperty("jenkins.enabled")
public class JenkinsBuildMonitor
    extends CommonPollingMonitor<
        JenkinsBuildMonitor.JobDelta, JenkinsBuildMonitor.JobPollingDelta> {

  private final JenkinsCache cache;
  private final BuildServices buildServices;
  private final boolean pollingEnabled;
  private final Optional<EchoService> echoService;
  private final JenkinsProperties jenkinsProperties;

  @Autowired
  public JenkinsBuildMonitor(
      IgorConfigurationProperties properties,
      Registry registry,
      Optional<DiscoveryClient> discoveryClient,
      Optional<LockService> lockService,
      JenkinsCache cache,
      BuildServices buildServices,
      @Value("${jenkins.polling.enabled:true}") boolean pollingEnabled,
      Optional<EchoService> echoService,
      JenkinsProperties jenkinsProperties) {
    super(properties, registry, discoveryClient, lockService);
    this.cache = cache;
    this.buildServices = buildServices;
    this.pollingEnabled = pollingEnabled;
    this.echoService = echoService;
    this.jenkinsProperties = jenkinsProperties;
  }

  @Override
  public String getName() {
    return "jenkinsBuildMonitor";
  }

  @Override
  public boolean isInService() {
    return pollingEnabled && super.isInService();
  }

  @Override
  public void poll(final boolean sendEvents) {
    buildServices
        .getServiceNames(BuildServiceProvider.JENKINS)
        .forEach(master -> pollSingle(new PollContext(master, !sendEvents)));
  }

  /**
   * Gets a list of jobs for this master and processes builds between last poll stamp and a sliding
   * upper bound stamp, the cursor will be used to advanced to the upper bound when all builds are
   * completed in the commit phase.
   */
  @Override
  protected JobPollingDelta generateDelta(PollContext ctx) {
    final String master = ctx.partitionName;
    log.trace("Checking for new builds in '{}'", master);

    final List<JobDelta> delta = new ArrayList<>();
    registry
        .timer("pollingMonitor.jenkins.retrieveProjects", "partition", master)
        .record(
            () -> {
              JenkinsService jenkinsService = (JenkinsService) buildServices.getService(master);
              ProjectsList projects = jenkinsService.getProjects();
              if (projects == null) {
                return;
              }
              projects
                  .getList()
                  .forEach(
                      project -> {
                        processBuildsOfProject(jenkinsService, master, project, delta);
                      });
            });

    return new JobPollingDelta(master, delta);
  }

  private void processBuildsOfProject(
      JenkinsService jenkinsService,
      final String master,
      final Project job,
      List<JobDelta> deltas) {
    if (job.getLastBuild() == null) {
      log.trace(
          "[{}:{}] has no builds skipping...", kv("master", master), kv("job", job.getName()));
      return;
    }

    try {
      Long cursor = cache.getLastPollCycleTimestamp(master, job.getName());
      Long lastBuildStamp = Long.valueOf(job.getLastBuild().getTimestamp());
      Date upperBound = new Date(lastBuildStamp);
      if (Objects.equals(cursor, lastBuildStamp)) {
        log.trace("[{}:{}] is up to date. skipping", master, job);
        return;
      }

      if (cursorIsUnset(cursor)
          && !igorProperties.getSpinnaker().getBuild().isHandleFirstBuilds()) {
        cache.setLastPollCycleTimestamp(master, job.getName(), lastBuildStamp);
        return;
      }

      List<Build> allBuilds = getBuilds(jenkinsService, master, job, cursor, lastBuildStamp);
      List<Build> currentlyBuilding =
          allBuilds.stream().filter(Build::isBuilding).collect(Collectors.toList());
      List<Build> completedBuilds =
          allBuilds.stream().filter(b -> !b.isBuilding()).collect(Collectors.toList());

      cursor = cursorIsUnset(cursor) ? lastBuildStamp : cursor;
      Date lowerBound = new Date(cursor);

      // TODO(jc): Document.
      if (!igorProperties.getSpinnaker().getBuild().isProcessBuildsOlderThanLookBackWindow()) {
        completedBuilds = onlyInLookBackWindow(completedBuilds);
      }

      JobDelta delta = new JobDelta();
      delta.setCursor(cursor);
      delta.setName(job.getName());
      delta.setLastBuildStamp(lastBuildStamp);
      delta.setUpperBound(upperBound);
      delta.setLowerBound(lowerBound);
      delta.setCompletedBuilds(completedBuilds);
      delta.setRunningBuilds(currentlyBuilding);

      deltas.add(delta);
    } catch (Exception e) {
      log.error(
          "Error processing builds for [{}:{}]", kv("master", master), kv("job", job.getName()), e);
      if (e.getCause() instanceof RetrofitError) {
        RetrofitError re = (RetrofitError) e.getCause();
        log.error(
            "Error communicating with jenkins for [{}:{}]: {}",
            kv("master", master),
            kv("job", job.getName()),
            kv("url", re.getUrl()),
            re);
      }
    }
  }

  private List<Build> getBuilds(
      JenkinsService jenkinsService,
      final String master,
      final Project job,
      final Long cursor,
      final Long lastBuildStamp) {
    if (cursorIsUnset(cursor)) {
      log.debug("[{}:{}] setting new cursor to {}", master, job.getName(), lastBuildStamp);
      final List<Build> builds = jenkinsService.getBuilds(job.getName());
      return Optional.ofNullable(builds).orElse(Collections.emptyList());
    }

    // filter between last poll and jenkins last build included
    final List<Build> builds =
        Optional.ofNullable(jenkinsService.getBuilds(job.getName()))
            .orElse(Collections.emptyList());

    return builds.stream()
        .filter(
            build -> {
              String buildTimestamp = build.getTimestamp();
              long buildStamp = Long.parseLong(buildTimestamp == null ? "0" : buildTimestamp);
              return buildStamp <= lastBuildStamp && buildStamp > cursor;
            })
        .collect(Collectors.toList());
  }

  private List<Build> onlyInLookBackWindow(final List<Build> builds) {
    Duration offsetSeconds = Duration.ofSeconds(getPollInterval());
    Duration lookBackWindowMinutes =
        Duration.ofMinutes(igorProperties.getSpinnaker().getBuild().getLookBackWindowMins());

    Instant lookBackDate = Instant.now().minus(offsetSeconds.plus(lookBackWindowMinutes));

    return builds.stream()
        .filter(
            build -> {
              Instant buildEndDate =
                  Instant.ofEpochMilli(Long.parseLong(build.getTimestamp()))
                      .plusMillis(build.getDuration());
              return buildEndDate.isAfter(lookBackDate);
            })
        .collect(Collectors.toList());
  }

  @Override
  protected void commitDelta(JobPollingDelta delta, final boolean sendEvents) {
    final String master = delta.getMaster();

    delta
        .getItems()
        .forEach(
            job -> {
              // post events for finished builds
              job.completedBuilds.forEach(
                  build -> {
                    boolean eventPosted =
                        cache.getEventPosted(master, job.name, job.cursor, build.getNumber());
                    if (!eventPosted && sendEvents) {
                      Project project = new Project();
                      project.setName(job.getName());
                      project.setLastBuild(build);
                      postEvent(project, master);
                      log.debug(
                          "[{}:{}]:{} event posted", master, job.getName(), build.getNumber());
                      cache.setEventPosted(
                          master, job.getName(), job.getCursor(), build.getNumber());
                    }
                  });

              // advance cursor when all builds have completed in the interval
              if (job.getRunningBuilds().isEmpty()) {
                log.info(
                    "[{}:{}] has no other builds between [{} - {}], advancing cursor to {}",
                    kv("master", master),
                    kv("job", job.name),
                    job.getLowerBound(),
                    job.getUpperBound(),
                    job.getLastBuildStamp());
                cache.pruneOldMarkers(master, job.getName(), job.getCursor());
                cache.setLastPollCycleTimestamp(master, job.getName(), job.getLastBuildStamp());
              }
            });
  }

  @Override
  protected Integer getPartitionUpperThreshold(final String partition) {
    return jenkinsProperties.getMasters().stream()
        .filter(it -> it.getName().equals(partition))
        .findFirst()
        .map(JenkinsProperties.JenkinsHost::getItemUpperThreshold)
        .orElse(null);
  }

  private void postEvent(final Project project, final String master) {
    if (!echoService.isPresent()) {
      log.warn("Cannot send build notification: Echo is not configured");
      registry
          .counter(missedNotificationId.withTag("monitor", getClass().getSimpleName()))
          .increment();
      return;
    }

    AuthenticatedRequest.allowAnonymous(
        () -> {
          echoService.ifPresent(
              echo ->
                  echo.postEvent(new JenkinsBuildEvent(new JenkinsBuildContent(project, master))));
          // TODO(rz): Add allowAnonymous(Runnable)
          return null;
        });
  }

  /** TODO(jc): First pass, not sure what this cursor actually is. Document. */
  private boolean cursorIsUnset(Long cursor) {
    return (cursor == null || cursor == 0);
  }

  @Data
  @AllArgsConstructor
  static class JobPollingDelta implements PollingDelta<JobDelta> {
    private String master;
    private List<JobDelta> items;
  }

  @Data
  static class JobDelta implements DeltaItem {
    private Long cursor;
    private String name;
    private Long lastBuildStamp;
    private Date lowerBound;
    private Date upperBound;
    private List<Build> completedBuilds;
    private List<Build> runningBuilds;
  }
}
