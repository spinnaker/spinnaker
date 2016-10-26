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

import com.netflix.appinfo.InstanceInfo
import com.netflix.discovery.DiscoveryClient
import com.netflix.spinnaker.igor.IgorConfigurationProperties
import com.netflix.spinnaker.igor.build.BuildCache
import com.netflix.spinnaker.igor.build.model.GenericProject
import com.netflix.spinnaker.igor.config.TravisProperties
import com.netflix.spinnaker.igor.history.EchoService
import com.netflix.spinnaker.igor.history.model.GenericBuildContent
import com.netflix.spinnaker.igor.history.model.GenericBuildEvent
import com.netflix.spinnaker.igor.model.BuildServiceProvider
import com.netflix.spinnaker.igor.polling.PollingMonitor
import com.netflix.spinnaker.igor.service.BuildMasters
import com.netflix.spinnaker.igor.travis.client.model.Repo
import com.netflix.spinnaker.igor.travis.client.model.v3.V3Build
import com.netflix.spinnaker.igor.travis.service.TravisBuildConverter
import com.netflix.spinnaker.igor.travis.service.TravisResultConverter
import com.netflix.spinnaker.igor.travis.service.TravisService
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.event.ContextRefreshedEvent
import org.springframework.stereotype.Service
import rx.Observable
import rx.Scheduler
import rx.functions.Action0
import rx.schedulers.Schedulers

import java.util.concurrent.TimeUnit


/**
 * Monitors new travis builds
 */
@Slf4j
@Service
@SuppressWarnings('CatchException')
@ConditionalOnProperty('travis.enabled')
class TravisBuildMonitor implements PollingMonitor{

    Scheduler.Worker worker = Schedulers.io().createWorker()

    @Autowired
    BuildCache buildCache

    @Autowired(required = false)
    EchoService echoService

    @Autowired(required = false)
    DiscoveryClient discoveryClient

    @Autowired
    BuildMasters buildMasters

    Long lastPoll

    static final int NEW_BUILD_EVENT_THRESHOLD = 1

    static final long BUILD_STARTED_AT_THRESHOLD = TimeUnit.SECONDS.toMillis(30)

    @Autowired
    IgorConfigurationProperties igorConfigurationProperties

    @Autowired
    TravisProperties travisProperties

    @Override
    void onApplicationEvent(ContextRefreshedEvent event) {
        log.info('Started')
        setBuildCacheTTL()
        worker.schedulePeriodically(
            {
                if (isInService()) {
                    buildMasters.filteredMap(BuildServiceProvider.TRAVIS).keySet().each { master ->
                        changedBuilds(master)
                    }
                } else {
                    log.info("not in service (lastPoll: ${lastPoll ?: 'n/a'})")
                    lastPoll = null
                }
            } as Action0, 0, pollInterval, TimeUnit.SECONDS
        )
    }

    @Override
    String getName() {
        return "travisBuildMonitor"
    }

    @Override
    boolean isInService() {
        if (discoveryClient == null) {
            log.info("no DiscoveryClient, assuming InService")
            true
        } else {
            def remoteStatus = discoveryClient.instanceRemoteStatus
            log.info("current remote status ${remoteStatus}")
            remoteStatus == InstanceInfo.InstanceStatus.UP
        }
    }

    @Override
    Long getLastPoll() {
        return lastPoll
    }

    @Override
    int getPollInterval() {
        return igorConfigurationProperties.spinnaker.build.pollInterval
    }

    List<Map> changedBuilds(String master) {
        log.info('Checking for new builds for ' + master)
        List<String> cachedRepoSlugs = buildCache.getJobNames(master)
        List<Map> results = []

        TravisService travisService = buildMasters.map[master]

        lastPoll = System.currentTimeMillis()
        def startTime = System.currentTimeMillis()
        List<Repo> repos = filterOutOldBuilds(travisService.getReposForAccounts())
        log.info("Took ${System.currentTimeMillis() - startTime}ms to retrieve ${repos.size()} repositories (master: ${master})")

        Observable.from(repos).subscribe(
            { Repo repo ->

                List<V3Build> builds = travisService.getBuilds(repo, 5)
                for (V3Build build : builds) {
                    boolean addToCache = false
                    Map cachedBuild = null
                    String branchedRepoSlug = build.branchedRepoSlug()
                    if (cachedRepoSlugs.contains(branchedRepoSlug)) {
                        cachedBuild = buildCache.getLastBuild(master, branchedRepoSlug)
                        if (build.number > Integer.valueOf(cachedBuild.lastBuildLabel)) {
                            addToCache = true
                            log.info "New build: ${master}: ${branchedRepoSlug} : ${build.number}"
                        }
                        if (buildStateHasChanged(build, cachedBuild)) {
                            addToCache = true
                        }
                    } else {
                        addToCache = true
                    }
                    if (addToCache) {
                        log.info("Build update [${branchedRepoSlug}:${build.number}] [status:${build.state}] [running:${TravisResultConverter.running(build.state)}]")
                        buildCache.setLastBuild(master, branchedRepoSlug, build.number, TravisResultConverter.running(build.state), buildCacheJobTTLSeconds())
                        buildCache.setLastBuild(master, build.repository.slug, build.number, TravisResultConverter.running(build.state), buildCacheJobTTLSeconds())
                        if(!build.spinnakerTriggered()) {
                            sendEventForBuild(build, branchedRepoSlug, master, travisService)
                        }
                        results << [previous: cachedBuild, current: repo]
                    }
                }
            }, {
            log.error("Error: ${it.message} (${master})")
        }
        )
        log.info("Last poll took ${System.currentTimeMillis() - lastPoll}ms (master: ${master})")
        if (travisProperties.repositorySyncEnabled) {
            startTime = System.currentTimeMillis()
            travisService.syncRepos()
            log.info("repositorySync: Took ${System.currentTimeMillis() - startTime}ms to sync repositories for ${master}")
        }
        results

    }

    private boolean buildStateHasChanged(V3Build build, Map cachedBuild) {
        (TravisResultConverter.running(build.state) != cachedBuild.lastBuildBuilding) &&
            (build.number == Integer.valueOf(cachedBuild.lastBuildLabel))
    }

    private void sendEventForBuild(V3Build build, String branchedSlug, String master, TravisService travisService) {
        if (echoService) {
            log.info "pushing event for ${master}:${build.repository.slug}:${build.number}"
            GenericProject project = new GenericProject(build.repository.slug, TravisBuildConverter.genericBuild(build, travisService.baseUrl))
            echoService.postEvent(
                new GenericBuildEvent(content: new GenericBuildContent(project: project, master: master, type: 'travis'))
            )
            log.info "pushing event for ${master}:${branchedSlug}:${build.number}"
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
                    log.info "Found build without TTL: ${master}:${job}:${ttl} - Setting TTL to ${buildCacheJobTTLSeconds()}"
                    buildCache.setTTL(master, job, buildCacheJobTTLSeconds())
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
