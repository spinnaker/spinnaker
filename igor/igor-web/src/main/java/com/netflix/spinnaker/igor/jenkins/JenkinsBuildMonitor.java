/*
 * Copyright 2014 Netflix, Inc.
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

package com.netflix.spinnaker.igor.jenkins;

import com.netflix.spectator.api.BasicTag;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.igor.IgorConfigurationProperties;
import com.netflix.spinnaker.igor.config.JenkinsProperties;
import com.netflix.spinnaker.igor.history.EchoService;
import com.netflix.spinnaker.igor.history.model.BuildContent;
import com.netflix.spinnaker.igor.history.model.BuildEvent;
import com.netflix.spinnaker.igor.jenkins.client.model.Build;
import com.netflix.spinnaker.igor.jenkins.client.model.Project;
import com.netflix.spinnaker.igor.jenkins.service.JenkinsService;
import com.netflix.spinnaker.igor.model.BuildServiceProvider;
import com.netflix.spinnaker.igor.polling.CommonPollingMonitor;
import com.netflix.spinnaker.igor.polling.DeltaItem;
import com.netflix.spinnaker.igor.polling.LockService;
import com.netflix.spinnaker.igor.polling.PollContext;
import com.netflix.spinnaker.igor.polling.PollingDelta;
import com.netflix.spinnaker.igor.service.BuildServices;
import com.netflix.spinnaker.kork.discovery.DiscoveryStatusListener;
import com.netflix.spinnaker.kork.dynamicconfig.DynamicConfigService;
import com.netflix.spinnaker.kork.retrofit.Retrofit2SyncCall;
import com.netflix.spinnaker.kork.retrofit.exceptions.SpinnakerServerException;
import com.netflix.spinnaker.security.AuthenticatedRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static net.logstash.logback.argument.StructuredArguments.kv;
/**
 * Monitors new jenkins builds
 */
@Service
@SuppressWarnings("CatchException")
@ConditionalOnProperty("jenkins.enabled")
public class JenkinsBuildMonitor extends CommonPollingMonitor<JenkinsBuildMonitor.JobDelta, JenkinsBuildMonitor.JobPollingDelta> {

    private final JenkinsCache cache;
    private final BuildServices buildServices;
    private final boolean pollingEnabled;
    private final Optional<EchoService> echoService;
    private final JenkinsProperties jenkinsProperties;

    @Autowired
    public JenkinsBuildMonitor(IgorConfigurationProperties properties,
                        Registry registry,
                        DynamicConfigService dynamicConfigService,
                        DiscoveryStatusListener discoveryStatusListener,
                        Optional<LockService> lockService,
                        JenkinsCache cache,
                        BuildServices buildServices,
                        @Value("${jenkins.polling.enabled:true}") boolean pollingEnabled,
                        Optional<EchoService> echoService,
                        JenkinsProperties jenkinsProperties,
                        TaskScheduler taskScheduler) {
        super(properties, registry, dynamicConfigService, discoveryStatusListener, lockService, taskScheduler);
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
    public void poll(boolean sendEvents) {
        buildServices.getServiceNames(BuildServiceProvider.JENKINS).stream().forEach(
            master -> pollSingle(new PollContext(master, !sendEvents))
        );
    }

    /**
     * Gets a list of jobs for this master & processes builds between last poll stamp and a sliding upper bound stamp,
     * the cursor will be used to advanced to the upper bound when all builds are completed in the commit phase.
     */
    @Override
    protected JobPollingDelta generateDelta(PollContext ctx) {
        String master = ctx.getPartitionName();
        log.trace("Checking for new builds for {}", master);

        final List<JobDelta> delta = new ArrayList<>();
        registry.timer("pollingMonitor.jenkins.retrieveProjects", List.of(new BasicTag("partition", master))).record(() -> {
            JenkinsService jenkinsService = (JenkinsService) buildServices.getService(master);
            List<Project> jobs = jenkinsService.getProjects() != null && jenkinsService.getProjects().getList() != null
                ? jenkinsService.getProjects().getList() : new ArrayList<>();
            jobs.forEach(job -> processBuildsOfProject(jenkinsService, master, job, delta));
        });
        return new JobPollingDelta(master, delta);
    }

    private void processBuildsOfProject(JenkinsService jenkinsService, String master, Project job, List<JobDelta> delta) {
        if (job.getLastBuild() == null) {
            log.trace("[{}:{}] has no builds skipping...", kv("master", master), kv("job", job.getName()));
            return;
        }

        try {
            Long cursor = cache.getLastPollCycleTimestamp(master, job.getName());
            Long lastBuildStamp = (Long) job.getLastBuild().getTimestamp();
            Date upperBound = new Date(lastBuildStamp);
            if (cursor != null && cursor.equals(lastBuildStamp)) {
                log.trace("[{}:{}] is up to date. skipping", master, job.getName());
                return;
            }

            if (cursor == null && !igorProperties.getSpinnaker().getBuild().isHandleFirstBuilds()) {
                cache.setLastPollCycleTimestamp(master, job.getName(), lastBuildStamp);
                return;
            }

            List<Build> allBuilds = getBuilds(jenkinsService, master, job, cursor, lastBuildStamp);
            List<Build> currentlyBuilding = allBuilds.stream().filter(Build::isBuilding).collect(Collectors.toList());
            List<Build> completedBuilds = allBuilds.stream().filter(b -> !b.isBuilding()).collect(Collectors.toList());
            cursor = cursor != null ? cursor : lastBuildStamp;
            Date lowerBound = new Date(cursor);

            if (!igorProperties.getSpinnaker().getBuild().isProcessBuildsOlderThanLookBackWindow()) {
                completedBuilds = onlyInLookBackWindow(completedBuilds);
            }

            delta.add(new JobDelta(
                cursor,
                job.getName(),
                lastBuildStamp,
                upperBound,
                lowerBound,
                completedBuilds,
                currentlyBuilding
            ));

        } catch (Exception e) {
            log.error("Error processing builds for [{}:{}]", kv("master", master), kv("job", job.getName()), e);
            if (e instanceof SpinnakerServerException) {
                log.error("Error communicating with jenkins for [{}:{}]: {}", kv("master", master), kv("job", job.getName()), kv("url", ((SpinnakerServerException) e).getUrl()), e);
            }
        }
    }

    private List<Build> getBuilds(JenkinsService jenkinsService, String master, Project job, Long cursor, Long lastBuildStamp) {
        if (cursor == null) {
            log.debug("[{}:{}] setting new cursor to {}", master, job.getName(), lastBuildStamp);
            return jenkinsService.getBuilds(job.getName()) != null ? jenkinsService.getBuilds(job.getName()) : new ArrayList<>();
        }

        // filter between last poll and jenkins last build included
        List<Build> builds = jenkinsService.getBuilds(job.getName());
        if (builds == null) {
            return new ArrayList<>();
        }
        return builds.stream().filter(build -> {
            Long buildStamp = (Long) build.getTimestamp();
            return buildStamp <= lastBuildStamp && buildStamp > cursor;
        }).collect(Collectors.toList());
    }

    private List<Build> onlyInLookBackWindow(List<Build> builds) {
        long offsetMillis = getPollInterval() * 1000;
        long lookBackWindowMillis = igorProperties.getSpinnaker().getBuild().getLookBackWindowMins() * 60 * 1000;
        Date lookBackDate = new Date(System.currentTimeMillis() - (offsetMillis + lookBackWindowMillis));

        return builds.stream().filter(build -> {
            Date buildEndDate = new Date((Long) build.getTimestamp() + build.getDuration());
            return buildEndDate.after(lookBackDate);
        }).collect(Collectors.toList());
    }

    @Override
    protected void commitDelta(JobPollingDelta delta, boolean sendEvents) {
        String master = delta.master;

        delta.items.stream().forEach(job -> {
            // post events for finished builds
            job.completedBuilds.forEach(build -> {
                Boolean eventPosted = cache.getEventPosted(master, job.name, job.cursor, build.getNumber());
                if (eventPosted == null || !eventPosted) {
                    if (sendEvents) {
                        Project project = new Project();
                        project.setName(job.name);
                        project.setLastBuild(build);
                        postEvent(project, master);
                        log.debug("[{}:{}]:{} event posted", master, job.name, build.getNumber());
                    } else {
                      registry.counter(missedNotificationId.withTags("monitor", getName(), "reason", "fastForward")).increment();
                    }

                    cache.setEventPosted(master, job.name, job.cursor, build.getNumber());
                }
            });

            // advance cursor when all builds have completed in the interval
            if (job.runningBuilds.isEmpty()) {
                log.info("[{}:{}] has no other builds between [{} - {}], " +
                    "advancing cursor to {}", kv("master", master), kv("job", job.name), job.lowerBound, job.upperBound, job.lastBuildStamp);
                cache.pruneOldMarkers(master, job.name, job.cursor);
                cache.setLastPollCycleTimestamp(master, job.name, job.lastBuildStamp);
            }
        });
    }

    @Override
    protected Integer getPartitionUpperThreshold(String partition) {
        JenkinsProperties.JenkinsHost host = jenkinsProperties.getMasters().stream()
            .filter(m -> partition.equals(m.getName()))
            .findFirst()
            .orElse(null);
        return host != null ? host.getItemUpperThreshold() : null;
    }

    private void postEvent(Project project, String master) {
        if (!echoService.isPresent()) {
            log.warn("Cannot send build notification: Echo is not configured");
            registry.counter(missedNotificationId.withTag("monitor", getName())).increment();
            return;
        }
        AuthenticatedRequest.allowAnonymous(() -> {
            BuildEvent event = new BuildEvent();
            BuildContent content = new BuildContent();
            content.setProject(project);
            content.setMaster(master);
            event.setContent(content);
            Retrofit2SyncCall.execute(echoService.get().postEvent(event));
        });
    }

    public static class JobPollingDelta implements PollingDelta<JobDelta> {
        private String master;
        private List<JobDelta> items;

        public JobPollingDelta(String master, List<JobDelta> items) {
            this.master = master;
            this.items = items;
        }

        @Override
        public List<JobDelta> getItems() {
            return items;
        }
    }

    public static class JobDelta implements DeltaItem {
        private Long cursor;
        private String name;
        private Long lastBuildStamp;
        private Date lowerBound;
        private Date upperBound;
        private List<Build> completedBuilds;
        private List<Build> runningBuilds;

        public JobDelta(Long cursor, String name, Long lastBuildStamp, Date upperBound, Date lowerBound,
                       List<Build> completedBuilds, List<Build> runningBuilds) {
            this.cursor = cursor;
            this.name = name;
            this.lastBuildStamp = lastBuildStamp;
            this.upperBound = upperBound;
            this.lowerBound = lowerBound;
            this.completedBuilds = completedBuilds;
            this.runningBuilds = runningBuilds;
        }
    }
}
