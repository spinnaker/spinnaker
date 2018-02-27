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

package com.netflix.spinnaker.igor.jenkins

import com.netflix.discovery.DiscoveryClient
import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.igor.IgorConfigurationProperties
import com.netflix.spinnaker.igor.config.JenkinsProperties
import com.netflix.spinnaker.igor.history.EchoService
import com.netflix.spinnaker.igor.history.model.BuildContent
import com.netflix.spinnaker.igor.history.model.BuildEvent
import com.netflix.spinnaker.igor.jenkins.client.model.Build
import com.netflix.spinnaker.igor.jenkins.client.model.Project
import com.netflix.spinnaker.igor.jenkins.service.JenkinsService
import com.netflix.spinnaker.igor.model.BuildServiceProvider
import com.netflix.spinnaker.igor.polling.CommonPollingMonitor
import com.netflix.spinnaker.igor.polling.DeltaItem
import com.netflix.spinnaker.igor.polling.PollContext
import com.netflix.spinnaker.igor.polling.PollingDelta
import com.netflix.spinnaker.igor.service.BuildMasters
import groovy.time.TimeCategory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service
import retrofit.RetrofitError

import javax.annotation.PreDestroy

import static net.logstash.logback.argument.StructuredArguments.kv
/**
 * Monitors new jenkins builds
 */
@Service
@SuppressWarnings('CatchException')
@ConditionalOnProperty('jenkins.enabled')
class JenkinsBuildMonitor extends CommonPollingMonitor<JobDelta, JobPollingDelta> {

    private final JenkinsCache cache
    private final BuildMasters buildMasters
    private final boolean pollingEnabled
    private final Optional<EchoService> echoService
    private final JenkinsProperties jenkinsProperties

    @Autowired
    JenkinsBuildMonitor(IgorConfigurationProperties properties,
                        Registry registry,
                        Optional<DiscoveryClient> discoveryClient,
                        JenkinsCache cache,
                        BuildMasters buildMasters,
                        @Value('${jenkins.polling.enabled:true}') boolean pollingEnabled,
                        Optional<EchoService> echoService,
                        JenkinsProperties jenkinsProperties) {
        super(properties, registry, discoveryClient)
        this.cache = cache
        this.buildMasters = buildMasters
        this.pollingEnabled = pollingEnabled
        this.echoService = echoService
        this.jenkinsProperties = jenkinsProperties
    }

    @Override
    String getName() {
        "jenkinsBuildMonitor"
    }

    @Override
    boolean isInService() {
        pollingEnabled && super.isInService()
    }

    @Override
    void initialize() {
    }

    @Override
    void poll() {
        long startTime = System.currentTimeMillis()
        log.info "Polling cycle started: ${new Date()}"
        buildMasters.filteredMap(BuildServiceProvider.JENKINS).keySet().parallelStream().forEach(
            { master -> internalPoll(new PollContext(master)) }
        )
        log.info "Polling cycle done in ${System.currentTimeMillis() - startTime}ms"
    }

    @PreDestroy
    void stop() {
        log.info('Stopped')
        if (!worker.isUnsubscribed()) {
            worker.unsubscribe()
        }
    }

    /**
     * Gets a list of jobs for this master & processes builds between last poll stamp and a sliding upper bound stamp,
     * the cursor will be used to advanced to the upper bound when all builds are completed in the commit phase.
     */
    @Override
    protected JobPollingDelta generateDelta(PollContext ctx) {
        String master = ctx.partitionName
        log.debug("Checking for new builds for $master")
        def startTime = System.currentTimeMillis()

        List<JobDelta> delta = []

        JenkinsService jenkinsService = buildMasters.map[master] as JenkinsService
        List<Project> jobs = jenkinsService.getProjects()?.getList() ?:[]
        for (Project job : jobs) {
            if (!job.lastBuild) {
                log.debug("[{}:{}] has no builds skipping...", kv("master", master), kv("job", job.name))
                continue
            }

            try {
                Long cursor = cache.getLastPollCycleTimestamp(master, job.name)
                Long lastBuildStamp = job.lastBuild.timestamp as Long
                Date upperBound = new Date(lastBuildStamp)
                if (cursor == lastBuildStamp) {
                    log.debug("[${master}:${job.name}] is up to date. skipping")
                } else {
                    if (!cursor && !igorProperties.spinnaker.build.handleFirstBuilds) {
                        cache.setLastPollCycleTimestamp(master, job.name, lastBuildStamp)
                        continue
                    }

                    List<Build> allBuilds = (jenkinsService.getBuilds(job.name).getList() ?: [])
                    if (!cursor) {
                        log.debug("[${master}:${job.name}] setting new cursor to ${lastBuildStamp}")
                        cursor = lastBuildStamp
                    } else {
                        // filter between last poll and jenkins last build included
                        allBuilds = (jenkinsService.getBuilds(job.name).getList() ?: []).findAll { build ->
                            Long buildStamp = build.timestamp as Long
                            return buildStamp <= lastBuildStamp && buildStamp > cursor
                        }
                    }

                    List<Build> currentlyBuilding = allBuilds.findAll { it.building }
                    List<Build> completedBuilds = allBuilds.findAll { !it.building }
                    Date lowerBound = new Date(cursor)

                    filterOldBuilds(master, job, completedBuilds)

                    delta.add(new JobDelta(
                        cursor: cursor,
                        name: job.name,
                        lastBuildStamp: lastBuildStamp,
                        upperBound: upperBound,
                        lowerBound: lowerBound,
                        completedBuilds: completedBuilds,
                        runningBuilds: currentlyBuilding
                    ))
                }
            } catch (e) {
                log.error("Error processing builds for [{}:{}]", kv("master", master), kv("job", job.name), e)
                if (e.cause instanceof RetrofitError) {
                    def re = (RetrofitError) e.cause
                    log.error("Error communicating with jenkins for [{}:{}]: {}", kv("master", master), kv("job", job.name), kv("url", re.url), re);
                }
            }
        }

        log.debug("Took ${System.currentTimeMillis() - startTime}ms to retrieve projects (master: {})", kv("master", master))

        return new JobPollingDelta(master: master, items: delta)
    }

    private void filterOldBuilds(String master, Project job, List<Build> completedBuilds) {
        if (!igorProperties.spinnaker.build.processBuildsOlderThanLookBackWindow) {
            use(TimeCategory) {
                def offsetSeconds = pollInterval.seconds
                def lookBackWindowMins = igorProperties.spinnaker.build.lookBackWindowMins.minutes
                Date lookBackDate = (offsetSeconds + lookBackWindowMins).ago

                def tooOldBuilds = completedBuilds.findAll {
                    Date buildEndDate = new Date((it.timestamp as Long) + it.duration)
                    return buildEndDate.before(lookBackDate)
                }
                log.debug("Filtering out builds older than {} from {} {}: build numbers{}",
                    lookBackDate,
                    kv("master", master),
                    kv("job", job.name),
                    tooOldBuilds.collect { it.number }
                )
                completedBuilds.removeAll(tooOldBuilds)
            }
        }
    }

    @Override
    protected void commitDelta(JobPollingDelta delta) {
        String master = delta.master

        delta.items.parallelStream().forEach { job ->
            // post events for finished builds
            job.completedBuilds.forEach { build ->
                Boolean eventPosted = cache.getEventPosted(master, job.name, job.cursor, build.number)
                if (!eventPosted) {
                    log.debug("[${master}:${job.name}]:${build.number} event posted")
                    postEvent(new Project(name: job.name, lastBuild: build), master)
                    cache.setEventPosted(master, job.name, job.cursor, build.number)
                }
            }

            // advance cursor when all builds have completed in the interval
            if (job.runningBuilds.isEmpty()) {
                log.info("[{}:{}] has no other builds between [${job.lowerBound} - ${job.upperBound}], advancing cursor to ${job.lastBuildStamp}", kv("master", master), kv("job", job.name))
                cache.pruneOldMarkers(master, job.name, job.cursor)
                cache.setLastPollCycleTimestamp(master, job.name, job.lastBuildStamp)
            }
        }
    }

    @Override
    protected Integer getPartitionUpperThreshold(String partition) {
        return jenkinsProperties.masters.find { partition == it.name }?.itemUpperThreshold
    }

    void postEvent(Project project, String master) {
        if (!echoService.isPresent()) {
            log.warn("Cannot send build notification: Echo is not configured")
            registry.counter(missedNotificationId.withTag("monitor", getClass().simpleName)).increment()
            return
        }
        echoService.get().postEvent(new BuildEvent(content: new BuildContent(project: project, master: master)))
    }

    private static class JobPollingDelta implements PollingDelta<JobDelta> {
        String master
        List<JobDelta> items
    }

    private static class JobDelta implements DeltaItem {
        Long cursor
        String name
        Long lastBuildStamp
        Date lowerBound
        Date upperBound
        List<Build> completedBuilds
        List<Build> runningBuilds
    }
}
