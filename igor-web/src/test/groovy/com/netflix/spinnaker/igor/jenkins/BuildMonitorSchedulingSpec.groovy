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

import com.netflix.spinnaker.igor.jenkins.client.JenkinsMasters
import com.netflix.spinnaker.igor.jenkins.client.model.ProjectsList
import com.netflix.spinnaker.igor.jenkins.service.JenkinsService
import org.springframework.context.event.ContextRefreshedEvent
import rx.schedulers.TestScheduler
import spock.lang.Specification

import java.util.concurrent.TimeUnit

/**
 * Ensures that build monitor runs periodically
 */
@SuppressWarnings(['PropertyName'])
class BuildMonitorSchedulingSpec extends Specification {

    JenkinsCache cache = Mock(JenkinsCache)
    JenkinsService jenkinsService = Mock(JenkinsService)
    BuildMonitor monitor

    final MASTER = 'MASTER'
    final PROJECTS = new ProjectsList(list: [])
    final TestScheduler scheduler = new TestScheduler()

    void 'scheduller polls periodically'() {
        given:
        cache.getJobNames(MASTER) >> []
        monitor = new BuildMonitor(cache: cache, jenkinsMasters: new JenkinsMasters(map: [MASTER: jenkinsService]))
        monitor.worker = scheduler.createWorker()
        monitor.pollInterval = 1

        when:
        monitor.onApplicationEvent(Mock(ContextRefreshedEvent))
        scheduler.advanceTimeBy(1L, TimeUnit.SECONDS.MILLISECONDS)

        then: 'initial poll'
        1 * jenkinsService.projects >> PROJECTS

        when:
        scheduler.advanceTimeBy(998L, TimeUnit.SECONDS.MILLISECONDS)

        then:
        0 * jenkinsService.projects >> PROJECTS

        when: 'poll at 1 second'
        scheduler.advanceTimeBy(2L, TimeUnit.SECONDS.MILLISECONDS)

        then:
        1 * jenkinsService.projects >> PROJECTS

        when: 'poll at 2 and 3 second'
        scheduler.advanceTimeBy(4000L, TimeUnit.SECONDS.MILLISECONDS)

        then:
        4 * jenkinsService.projects >> PROJECTS

        cleanup:
        monitor.stop()
    }
}
