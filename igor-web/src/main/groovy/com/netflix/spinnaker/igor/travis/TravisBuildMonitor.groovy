/*
 * Copyright 2016 Schibsted ASA.
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

package com.netflix.spinnaker.igor.travis

import com.netflix.discovery.DiscoveryClient
import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.igor.IgorConfigurationProperties
import com.netflix.spinnaker.igor.build.BuildCache
import com.netflix.spinnaker.igor.build.model.GenericBuild
import com.netflix.spinnaker.igor.build.model.GenericProject
import com.netflix.spinnaker.igor.config.TravisProperties
import com.netflix.spinnaker.igor.history.EchoService
import com.netflix.spinnaker.igor.history.model.GenericBuildContent
import com.netflix.spinnaker.igor.history.model.GenericBuildEvent
import com.netflix.spinnaker.igor.model.BuildServiceProvider
import com.netflix.spinnaker.igor.polling.CommonPollingMonitor
import com.netflix.spinnaker.igor.polling.DeltaItem
import com.netflix.spinnaker.igor.polling.PollContext
import com.netflix.spinnaker.igor.polling.PollingDelta
import com.netflix.spinnaker.igor.service.BuildMasters
import com.netflix.spinnaker.igor.travis.client.model.Repo
import com.netflix.spinnaker.igor.travis.client.model.v3.TravisBuildState
import com.netflix.spinnaker.igor.travis.client.model.v3.V3Build
import com.netflix.spinnaker.igor.travis.service.TravisBuildConverter
import com.netflix.spinnaker.igor.travis.service.TravisResultConverter
import com.netflix.spinnaker.igor.travis.service.TravisService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service
import rx.Observable

import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit

import static net.logstash.logback.argument.StructuredArguments.kv
/**
 * Monitors travis builds
 */
@Service
@SuppressWarnings('CatchException')
@ConditionalOnProperty('travis.enabled')
class TravisBuildMonitor extends CommonPollingMonitor<BuildDelta, BuildPollingDelta> {

    static final long BUILD_STARTED_AT_THRESHOLD = TimeUnit.SECONDS.toMillis(30)

    private final BuildCache buildCache
    private final BuildMasters buildMasters
    private final TravisProperties travisProperties
    private final Optional<EchoService> echoService

    @Autowired
    TravisBuildMonitor(IgorConfigurationProperties properties,
                       Registry registry,
                       Optional<DiscoveryClient> discoveryClient,
                       BuildCache buildCache,
                       BuildMasters buildMasters,
                       TravisProperties travisProperties,
                       Optional<EchoService> echoService) {
        super(properties, registry, discoveryClient)
        this.buildCache = buildCache
        this.buildMasters = buildMasters
        this.travisProperties = travisProperties
        this.echoService = echoService
    }

    @Override
    void initialize() {
        migrateToNewBuildCache()
    }

    @Override
    void poll(boolean sendEvents) {
        buildMasters.filteredMap(BuildServiceProvider.TRAVIS).keySet().each { master ->
            pollSingle(new PollContext(master, !sendEvents))
        }
    }

    @Override
    protected BuildPollingDelta generateDelta(PollContext ctx) {
        String master = ctx.partitionName
        TravisService travisService = buildMasters.map[master] as TravisService

        List<BuildDelta> builds = []
        builds.addAll(trackedBuilds(master, travisService))
        builds.addAll(changedBuilds(master, travisService))

        return new BuildPollingDelta(master: master, items: builds)
    }

    @Override
    protected void commitDelta(BuildPollingDelta delta, boolean sendEvents) {
        String master = delta.master
        TravisService travisService = buildMasters.map[master] as TravisService

        delta.items.parallelStream().forEach { item ->
            V3Build build = item.build
            log.info("({}) Build update {} [running:{}]", kv("master", master), build.toString(), TravisResultConverter.running(build.state))
            if (build.state == TravisBuildState.passed) {
                item.genericBuild = travisService.getGenericBuild(build, true) // This also parses the log for artifacts
            }
            if (build.number > buildCache.getLastBuild(master, build.repository.slug, TravisResultConverter.running(build.state))) {
                buildCache.setLastBuild(master, build.repository.slug, build.number, TravisResultConverter.running(build.state), buildCacheJobTTLSeconds())
                if (sendEvents) {
                    sendEventForBuild(item, build.repository.slug, master)
                }
            }
            if (sendEvents) {
                sendEventForBuild(item, item.branchedRepoSlug, master)
            }
        }
    }

    @Override
    String getName() {
        return "travisBuildMonitor"
    }

    List<BuildDelta> trackedBuilds(String master, TravisService travisService) {
        def trackedBuilds = buildCache.getTrackedBuilds(master)
        log.info('({}) Checking for updates on {} tracked builds', kv("master", master), trackedBuilds.size())

        List<V3Build> builds = trackedBuilds.collect {
            travisService.getV3Build((int) it.get("buildId").toInteger())
        }

        return processBuilds(builds, master, travisService)
    }

    List<BuildDelta> changedBuilds(String master, TravisService travisService) {
        log.info('({}) Checking for new builds', kv("master", master))

        List<BuildDelta> results = []

        def startTime = System.currentTimeMillis()
        List<Repo> repos = filterOutOldBuilds(travisService.getReposForAccounts())
        log.info("({}) Took {}ms to retrieve {} repositories", kv("master", master), System.currentTimeMillis() - startTime, repos.size())
        Observable.from(repos).subscribe(
            { Repo repo ->
                List<V3Build> builds = travisService.getBuilds(repo, 5)
                results.addAll(processBuilds(builds, master, travisService))
            }, {
                log.error("({}) Error: ${it.message}", kv("master", master))
            }
        )
        if (!results.isEmpty()) {
            log.info("({}) Found {} new builds", kv("master", master), results.size())
        }
        log.info("({}) Last poll took {}ms", kv("master", master), System.currentTimeMillis() - startTime)
        if (travisProperties.repositorySyncEnabled) {
            startTime = System.currentTimeMillis()
            travisService.syncRepos()
            log.info("({}) repositorySync: Took {}ms to sync repositories", kv("master", master), System.currentTimeMillis() - startTime)
        }

        return results
    }

    private List<BuildDelta> processBuilds(List<V3Build> builds, String master, TravisService travisService) {
        List<BuildDelta> results = []
        for (V3Build build : builds) {
            String branchedRepoSlug = build.branchedRepoSlug()
            def cachedBuild = buildCache.getLastBuild(master, branchedRepoSlug, TravisResultConverter.running(build.state))
            def genericBuild = TravisBuildConverter.genericBuild(build, travisService.baseUrl)
            if (build.number > cachedBuild && !build.spinnakerTriggered()) {
                results.add(new BuildDelta(
                    branchedRepoSlug: branchedRepoSlug,
                    build: build,
                    genericBuild: genericBuild,
                    travisBaseUrl: travisService.baseUrl,
                    currentBuildNum: build.number,
                    previousBuildNum: cachedBuild
                ))
                buildCache.setLastBuild(master, branchedRepoSlug, build.number, TravisResultConverter.running(build.state), buildCacheJobTTLSeconds())
            }
            setTracking(build, master)
        }
        return results
    }

    private void setTracking(V3Build build, String master) {
        if (TravisResultConverter.running(build.state)) {
            buildCache.setTracking(master, build.repository.slug, build.id, getPollInterval() * 5)
            log.debug("({}) tracking set up for {}", kv("master", master), build.toString())
        } else {
            buildCache.deleteTracking(master, build.repository.slug, build.id)
            log.debug("({}) tracking deleted for {}", kv("master", master), build.toString())
        }
    }

    private void sendEventForBuild(BuildDelta buildDelta, String branchedSlug, String master) {
        if (!buildDelta.build.spinnakerTriggered()) {
            if (echoService.isPresent()) {
                log.info("({}) pushing event for :${branchedSlug}:${buildDelta.build.number}", kv("master", master))
                GenericProject project = new GenericProject(branchedSlug, buildDelta.genericBuild)
                echoService.get().postEvent(
                    new GenericBuildEvent(content: new GenericBuildContent(project: project, master: master, type: 'travis'))
                )
            } else {
                log.warn("Cannot send build event notification: Echo is not configured")
                registry.counter(missedNotificationId.withTag("monitor", getClass().simpleName)).increment()
            }
        }
    }

    private void migrateToNewBuildCache(){
        buildMasters.filteredMap(BuildServiceProvider.TRAVIS).keySet().each { master ->
            log.info "Getting all builds from old cache representation"
            buildCache.getDeprecatedJobNames(master).each { job ->
                Map oldBuild = buildCache.getDeprecatedLastBuild(master, job)
                if (oldBuild) {
                    int oldBuildNumber = (int) oldBuild.get("lastBuildLabel")

                    boolean oldBuildBuilding = (boolean) oldBuild.get("lastBuildBuilding")
                    int currentBuild = buildCache.getLastBuild(master, job, oldBuildBuilding)
                    if (currentBuild < oldBuildNumber) {
                        log.info("BuildCache migration {}:{}:{}:{}", kv("master", master), kv("job", job), kv("building", oldBuildBuilding), kv("buildNumber", oldBuildNumber))
                        buildCache.setLastBuild(master, job, oldBuildNumber, oldBuildBuilding, buildCacheJobTTLSeconds())
                    }
                }
            }
        }
    }

    private int buildCacheJobTTLSeconds() {
        return TimeUnit.DAYS.toSeconds(travisProperties.cachedJobTTLDays)
    }

    private List<Repo> filterOutOldBuilds(List<Repo> repos){
        /*
        BUILD_STARTED_AT_THRESHOLD is here because the builds can be picked up by igor before lastBuildStartedAt is
        set. This means the TTL can be set in the BuildCache before lastBuildStartedAt, if that happens we need a
        grace threshold so that we don't resend the event to echo. The value of the threshold assumes that travis
        will set the lastBuildStartedAt within 30 seconds.
         */
        Instant threshold = Instant.now().minus(travisProperties.cachedJobTTLDays, ChronoUnit.DAYS).plusMillis(BUILD_STARTED_AT_THRESHOLD)
        return repos.findAll({ repo ->
            repo.lastBuildStartedAt?.isAfter(threshold)
        })
    }

    @Override
    protected Integer getPartitionUpperThreshold(String partition) {
        return travisProperties.masters.find { it.name == partition }?.itemUpperThreshold
    }

    static class BuildPollingDelta implements PollingDelta<BuildDelta> {
        String master
        List<BuildDelta> items
    }

    static class BuildDelta implements DeltaItem {
        String branchedRepoSlug
        V3Build build
        GenericBuild genericBuild
        String travisBaseUrl
        int currentBuildNum
        int previousBuildNum
    }
}
