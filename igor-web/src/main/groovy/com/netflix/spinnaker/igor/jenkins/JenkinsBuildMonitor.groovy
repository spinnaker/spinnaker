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

import com.netflix.appinfo.InstanceInfo
import com.netflix.discovery.DiscoveryClient
import com.netflix.spinnaker.igor.IgorConfigurationProperties
import com.netflix.spinnaker.igor.history.EchoService
import com.netflix.spinnaker.igor.history.model.BuildContent
import com.netflix.spinnaker.igor.history.model.BuildEvent
import com.netflix.spinnaker.igor.jenkins.client.model.Project
import com.netflix.spinnaker.igor.model.BuildServiceProvider
import com.netflix.spinnaker.igor.polling.PollingMonitor
import com.netflix.spinnaker.igor.service.BuildMasters
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.event.ContextRefreshedEvent
import org.springframework.core.env.Environment
import org.springframework.stereotype.Service
import rx.Observable
import rx.Scheduler
import rx.Scheduler.Worker
import rx.functions.Action0
import rx.schedulers.Schedulers

import javax.annotation.PreDestroy
import java.util.concurrent.TimeUnit

/**
 * Monitors new jenkins builds
 */
@Slf4j
@Service
@SuppressWarnings('CatchException')
@ConditionalOnProperty('jenkins.enabled')
class JenkinsBuildMonitor implements PollingMonitor {

    @Autowired
    Environment environment

    Scheduler scheduler = Schedulers.io()
    Worker worker = scheduler.createWorker()

    @Autowired
    JenkinsCache cache

    @Autowired(required = false)
    EchoService echoService

    @Autowired
    BuildMasters buildMasters

    Long lastPoll

    @Override
    Long getLastPoll() {
        lastPoll
    }

    @Autowired
    IgorConfigurationProperties igorConfigurationProperties

    @Override
    int getPollInterval() {
        igorConfigurationProperties.spinnaker.build.pollInterval
    }

    static final String BUILD_IN_PROGRESS = "BUILDING"

    @Autowired(required = false)
    DiscoveryClient discoveryClient

    @Override
    String getName() {
        "jenkinsBuildMonitor"
    }

    String lastStatus

    @Override
    boolean isInService() {
        if (discoveryClient == null) {
            log.info("no DiscoveryClient, assuming InService")
            true
        } else {
            def remoteStatus = discoveryClient.instanceRemoteStatus
            if (remoteStatus != lastStatus) {
                log.info("current remote status ${remoteStatus}")
            }
            lastStatus=remoteStatus
            remoteStatus == InstanceInfo.InstanceStatus.UP
        }
    }

    @Override
    void onApplicationEvent(ContextRefreshedEvent event) {
        log.info('Started')
        worker.schedulePeriodically(
                {
                    if (isInService()) {
                        log.info "- Polling cycle started -"
                        buildMasters.filteredMap(BuildServiceProvider.JENKINS).keySet().parallelStream().forEach(
                                { master -> changedBuilds(master) }
                        )
                        log.info "- Polling cycle done -"
                    } else {
                        log.info("not in service (lastPoll: ${lastPoll ?: 'n/a'})")
                        lastPoll = null
                    }
                } as Action0, 0, pollInterval, TimeUnit.SECONDS
        )
    }

    @PreDestroy
    void stop() {
        log.info('Stopped')
        if (!worker.isUnsubscribed()) {
            worker.unsubscribe()
        }
    }

    /*
     * retrieves a list of new builds that are different than the ones in cache and keeps track of the builds it has
     */

    List<Map> changedBuilds(String master) {

        log.info('Checking for new builds for ' + master)
        List<Map> results = []

        lastPoll = System.currentTimeMillis()
        long thisPoll = lastPoll
        try {
            List<String> cachedBuilds = cache.getJobNames(master)

            def startTime = System.currentTimeMillis()
            List<Project> builds = buildMasters.map[master].projects?.list

            log.info("finding new builds in ${master} : ${builds.size()} items")

            List<String> buildNames = builds*.name

            Observable.from(cachedBuilds).filter { String name ->
                !(name in buildNames)
            }.subscribe(
                    { String jobName ->
                        log.info "Removing ${master}:${jobName}"
                        cache.remove(master, jobName)
                    },
                    { log.error("Error: ${it.message}") }
            )

            builds.stream().forEach(
                    { Project project ->
                        try {
                            boolean addToCache = false
                            Map cachedBuild = null
                            log.debug "processing build : ${project?.name} : building? ${project?.lastBuild?.building}"
                            if (!project?.lastBuild) {
                                log.debug "no builds found for ${project.name}, skipping"
                            } else if (cachedBuilds.contains(project.name)) {
                                cachedBuild = cache.getLastBuild(master, project.name)
                                cache.setLastBuild(master, project.name, project.lastBuild.number, project.lastBuild.building)
                                if ((project.lastBuild.building != cachedBuild.lastBuildBuilding) ||
                                        (project.lastBuild.number != Integer.valueOf(cachedBuild.lastBuildLabel))) {

                                    addToCache = true

                                    log.info "Build changed: ${master}: ${project.name} : ${project.lastBuild.number} : ${project.lastBuild.building}"

                                    if (echoService) {
                                        int currentBuild = project.lastBuild.number
                                        int lastBuild = Integer.valueOf(cachedBuild.lastBuildLabel)

                                        log.info "sending build events for builds between ${lastBuild} and ${currentBuild}"

                                        try {
                                            buildMasters.map[master].getBuilds(project.name).list.sort {
                                                it.number
                                            }.each { build ->
                                                if (build.number >= lastBuild && build.number < currentBuild) {
                                                    try {
                                                        Project oldProject = new Project(name: project.name, lastBuild: build)
                                                        if (build.number != lastBuild
                                                                || (build.number == lastBuild && cachedBuild.lastBuildBuilding != build.building)) {
                                                            log.info  "sending event for intermediate build ${build.number}"
                                                            postEvent(echoService, cachedBuilds, oldProject, master)
                                                        }
                                                    } catch (e) {
                                                        log.error("An error occurred fetching ${master}:${project.name}:${build.number}", e)
                                                    }
                                                }
                                            }
                                        } catch (e) {
                                            log.error("failed getting builds for ${master}", e)
                                        }
                                    }
                                }
                            } else {
                                log.info "New Build: ${master}: ${project.name} : ${project.lastBuild.number} : " +
                                        "${project.lastBuild.result}"
                                addToCache = true
                                cache.setLastBuild(master, project.name, project.lastBuild.number, project.lastBuild.building)
                            }
                            if (addToCache) {
                                project.lastBuild.result = project?.lastBuild?.result ?: project.lastBuild.building ? BUILD_IN_PROGRESS : ""
                                log.debug "setting result to ${project.lastBuild.result}"
                                postEvent(echoService, cachedBuilds, project, master)
                                results << [previous: cachedBuild, current: project]
                            }
                        } catch (e) {
                            log.error("fail processing build : ${project?.name}", e)
                        }
                    }
            )

            log.info("Took ${System.currentTimeMillis() - startTime}ms to retrieve projects (master: ${master})")

        } catch (e) {
            log.error("failed to update master $master", e)
        }

        results
    }

    static void postEvent(EchoService echoService, List<String> cachedBuildsForMaster, Project project, String master) {
        if (!cachedBuildsForMaster) {
            // avoid publishing an event if this master has no indexed builds (protects against a flushed redis)
            return
        }

        if (!echoService) {
            // avoid publishing an event if echo is disabled
            return
        }

        echoService.postEvent(
            new BuildEvent(content: new BuildContent(project: project, master: master))
        )
    }
}
