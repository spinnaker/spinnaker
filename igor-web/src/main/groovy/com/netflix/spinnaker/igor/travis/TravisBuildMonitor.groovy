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

import com.netflix.spinnaker.igor.build.BuildCache
import com.netflix.spinnaker.igor.build.model.GenericProject
import com.netflix.spinnaker.igor.config.TravisProperties
import com.netflix.spinnaker.igor.history.EchoService
import com.netflix.spinnaker.igor.history.model.GenericBuildContent
import com.netflix.spinnaker.igor.history.model.GenericBuildEvent
import com.netflix.spinnaker.igor.model.BuildServiceProvider
import com.netflix.spinnaker.igor.polling.CommonPollingMonitor
import com.netflix.spinnaker.igor.service.BuildMasters
import com.netflix.spinnaker.igor.travis.client.model.Repo
import com.netflix.spinnaker.igor.travis.client.model.v3.V3Build
import com.netflix.spinnaker.igor.travis.service.TravisBuildConverter
import com.netflix.spinnaker.igor.travis.service.TravisResultConverter
import com.netflix.spinnaker.igor.travis.service.TravisService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service
import rx.Observable
import rx.Scheduler
import rx.schedulers.Schedulers

import java.util.concurrent.TimeUnit

import static net.logstash.logback.argument.StructuredArguments.kv

/**
 * Monitors new travis builds
 */
@Service
@SuppressWarnings('CatchException')
@ConditionalOnProperty('travis.enabled')
class TravisBuildMonitor extends CommonPollingMonitor {

    Scheduler.Worker worker = Schedulers.io().createWorker()

    @Autowired
    BuildCache buildCache

    @Autowired(required = false)
    EchoService echoService

    @Autowired
    BuildMasters buildMasters

    static final long BUILD_STARTED_AT_THRESHOLD = TimeUnit.SECONDS.toMillis(30)

    @Autowired
    TravisProperties travisProperties

    @Override
    void initialize() {
        setBuildCacheTTL()
        migrateToNewBuildCache()
    }

    @Override
    void poll() {
        buildMasters.filteredMap(BuildServiceProvider.TRAVIS).keySet().each { master ->
            changedBuilds(master)
        }
    }

    @Override
    String getName() {
        return "travisBuildMonitor"
    }

    List<Map> changedBuilds(String master) {
        log.info('Checking for new builds for {}', kv("master", master))
        List<String> cachedRepoSlugs = buildCache.getJobNames(master)
        List<Map> results = []
        int updatedBuilds = 0

        TravisService travisService = buildMasters.map[master] as TravisService

        def startTime = System.currentTimeMillis()
        List<Repo> repos = filterOutOldBuilds(travisService.getReposForAccounts())
        log.info("Took ${System.currentTimeMillis() - startTime}ms to retrieve ${repos.size()} repositories (master: {})", kv("master", master))
        Observable.from(repos).subscribe(
            { Repo repo ->
                List<V3Build> builds = travisService.getBuilds(repo, 5)
                for (V3Build build : builds) {
                    boolean addToCache = false
                    def cachedBuild = null
                    String branchedRepoSlug = build.branchedRepoSlug()
                    if (cachedRepoSlugs.contains(branchedRepoSlug)) {
                        cachedBuild = buildCache.getLastBuild(master, branchedRepoSlug, TravisResultConverter.running(build.state))
                        if (build.number > cachedBuild) {
                            addToCache = true
                            log.info("New build: {}: ${branchedRepoSlug} : ${build.number}", kv("master", master))
                        }
                    } else {
                        addToCache = !TravisResultConverter.running(build.state)
                    }
                    if (addToCache) {
                        updatedBuilds += 1
                        log.info("Build update [${branchedRepoSlug}:${build.number}] [status:${build.state}] [running:${TravisResultConverter.running(build.state)}]")
                        buildCache.setLastBuild(master, branchedRepoSlug, build.number, TravisResultConverter.running(build.state), buildCacheJobTTLSeconds())
                        buildCache.setLastBuild(master, build.repository.slug, build.number, TravisResultConverter.running(build.state), buildCacheJobTTLSeconds())
                        if (!build.spinnakerTriggered()) {
                            sendEventForBuild(build, branchedRepoSlug, master, travisService)
                        }
                        results << [slug: branchedRepoSlug, previous: cachedBuild, current: build.number]
                    }
                }
            }, {
            log.error("Error: ${it.message} (master: {})", kv("master", master))
        }
        )
        if (updatedBuilds) {
            log.info("Found {} new builds (master: {})", updatedBuilds, kv("master", master))
        }
        log.info("Last poll took ${System.currentTimeMillis() - startTime}ms (master: {})", kv("master", master))
        if (travisProperties.repositorySyncEnabled) {
            startTime = System.currentTimeMillis()
            travisService.syncRepos()
            log.info("repositorySync: Took ${System.currentTimeMillis() - startTime}ms to sync repositories for {}", kv("master", master))
        }
        results

    }

    private void sendEventForBuild(V3Build build, String branchedSlug, String master, TravisService travisService) {
        if (echoService) {
            log.info("pushing event for {}:${build.repository.slug}:${build.number}", kv("master", master))
            GenericProject project = new GenericProject(build.repository.slug, TravisBuildConverter.genericBuild(build, travisService.baseUrl))
            echoService.postEvent(
                new GenericBuildEvent(content: new GenericBuildContent(project: project, master: master, type: 'travis'))
            )
            log.info("pushing event for {}:${branchedSlug}:${build.number}", kv("master", master))
            project = new GenericProject(branchedSlug, TravisBuildConverter.genericBuild(build, travisService.baseUrl))
            echoService.postEvent(
                new GenericBuildEvent(content: new GenericBuildContent(project: project, master: master, type: 'travis'))
            )
        }

    }

    private void setBuildCacheTTL() {
        /*
        This method is here to help migrate to ttl usage. It can be removed in igor after some time.
         */
        buildMasters.filteredMap(BuildServiceProvider.TRAVIS).keySet().each { master ->
            log.info "Searching for cached builds without TTL."

            buildCache.getJobNames(master).each { job ->
                Long ttl = buildCache.getTTL(master, job)
                if (ttl == -1L) {
                    log.info("Found build without TTL: {}:{}:${ttl} - Setting TTL to ${buildCacheJobTTLSeconds()}", kv("master", master), kv("job", job))
                    buildCache.setTTL(master, job, buildCacheJobTTLSeconds())
                }
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
        Long threshold = new Date().getTime() - TimeUnit.DAYS.toMillis(travisProperties.cachedJobTTLDays) + BUILD_STARTED_AT_THRESHOLD
        return repos.findAll({ repo ->
            repo.lastBuildStartedAt?.getTime() > threshold
        })
    }
}
