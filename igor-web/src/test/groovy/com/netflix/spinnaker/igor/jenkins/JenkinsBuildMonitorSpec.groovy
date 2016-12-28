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

import com.netflix.spinnaker.igor.history.EchoService
import com.netflix.spinnaker.igor.history.model.BuildEvent
import com.netflix.spinnaker.igor.jenkins.client.model.Build
import com.netflix.spinnaker.igor.jenkins.client.model.BuildsList
import com.netflix.spinnaker.igor.jenkins.client.model.Project
import com.netflix.spinnaker.igor.jenkins.client.model.ProjectsList
import com.netflix.spinnaker.igor.jenkins.service.JenkinsService
import com.netflix.spinnaker.igor.service.BuildMasters
import rx.schedulers.Schedulers
import spock.lang.Specification
import spock.lang.Unroll

/**
 * Tests for JenkinsBuildMonitor
 */
@SuppressWarnings(['DuplicateNumberLiteral', 'PropertyName'])
class JenkinsBuildMonitorSpec extends Specification {

    JenkinsCache cache = Mock(JenkinsCache)
    JenkinsService jenkinsService = Mock(JenkinsService)
    JenkinsBuildMonitor monitor

    final MASTER = 'MASTER'

    void setup() {
        monitor = new JenkinsBuildMonitor(cache: cache, buildMasters: new BuildMasters(map: [MASTER: jenkinsService]))
        monitor.scheduler = Schedulers.immediate()
    }

    void 'flag a new build not found in the cache'() {
        given:
        1 * cache.getJobNames(MASTER) >> ['job1']
        1 * jenkinsService.projects >> new ProjectsList(list: [new Project(name: 'job2', lastBuild: new Build(number: 1))])

        when:
        List<Map> builds = monitor.changedBuilds(MASTER)

        then:
        0 * cache.getLastBuild(MASTER, 'job1')
        1 * cache.setLastBuild(MASTER, 'job2', 1, false)
        builds.size() == 1
        builds[0].current.name == 'job2'
        builds[0].current.lastBuild.number == 1
        builds[0].previous == null
    }

    void 'flag existing build with a higher number as changed'() {
        given:
        1 * cache.getJobNames(MASTER) >> ['job2']
        1 * jenkinsService.projects >> new ProjectsList(list: [new Project(name: 'job2', lastBuild: new Build(number: 5))])

        when:
        List<Map> builds = monitor.changedBuilds(MASTER)

        then:
        1 * cache.getLastBuild(MASTER, 'job2') >> [lastBuildLabel: 3]
        1 * cache.setLastBuild(MASTER, 'job2', 5, false)
        builds[0].current.lastBuild.number == 5
        builds[0].previous.lastBuildLabel == 3
    }

    void 'flag builds in a different state as changed'() {
        given:
        1 * cache.getJobNames(MASTER) >> ['job3']
        1 * jenkinsService.projects >> new ProjectsList(
            list: [new Project(name: 'job3', lastBuild: new Build(number: 5, building: false))]
        )

        when:
        List<Map> builds = monitor.changedBuilds(MASTER)

        then:
        1 * cache.getLastBuild(MASTER, 'job3') >> [lastBuildLabel: 5, lastBuildRunning: true]
        1 * cache.setLastBuild(MASTER, 'job3', 5, false)
        builds[0].current.lastBuild.number == 5
        builds[0].previous.lastBuildLabel == 5
        builds[0].current.lastBuild.building == false
        builds[0].previous.lastBuildRunning == true
    }

    void 'stale builds are removed'() {
        given:
        1 * cache.getJobNames(MASTER) >> ['job3', 'job4']
        1 * jenkinsService.projects >> new ProjectsList(list: [])

        when:
        monitor.changedBuilds(MASTER)

        then:
        1 * cache.remove(MASTER, 'job3')
        1 * cache.remove(MASTER, 'job4')
    }

    void 'sends an event for every intermediate build'() {
        given:
        1 * cache.getJobNames(MASTER) >> ['job']
        1 * jenkinsService.projects >> new ProjectsList(
            list: [new Project(name: 'job', lastBuild: new Build(number: 6, building: false))]
        )
        1 * jenkinsService.getBuilds('job') >> new BuildsList(
            list: [
                new Build(number: 1),
                new Build(number: 2),
                new Build(number: 3, building: false),
                new Build(number: 4),
                new Build(number: 5),
                new Build(number: 6)
            ]
        )

        monitor.echoService = Mock(EchoService)

        when:
        List<Map> builds = monitor.changedBuilds(MASTER)

        then:
        1 * cache.getLastBuild(MASTER, 'job') >> [lastBuildLabel: 3, lastBuildRunning: true]
        1 * cache.setLastBuild(MASTER, 'job', 6, false)
        1 * monitor.echoService.postEvent({ it.content.project.lastBuild.number == 3 })
        1 * monitor.echoService.postEvent({ it.content.project.lastBuild.number == 4 })
        1 * monitor.echoService.postEvent({ it.content.project.lastBuild.number == 5 })
        1 * monitor.echoService.postEvent({ it.content.project.lastBuild.number == 6 })
    }

    void 'emits events only for builds in list'() {
        given:
        1 * cache.getJobNames(MASTER) >> ['job']
        1 * jenkinsService.projects >> new ProjectsList(
            list: [new Project(name: 'job', lastBuild: new Build(number: 6, building: false))]
        )
        1 * jenkinsService.getBuilds('job') >> new BuildsList(
            list: [
                new Build(number: 1),
                new Build(number: 3, building: false),
                new Build(number: 6)
            ]
        )

        monitor.echoService = Mock(EchoService)

        when:
        List<Map> builds = monitor.changedBuilds(MASTER)

        then:
        1 * cache.getLastBuild(MASTER, 'job') >> [lastBuildLabel: 3, lastBuildRunning: true]
        1 * cache.setLastBuild(MASTER, 'job', 6, false)
        1 * monitor.echoService.postEvent({ it.content.project.lastBuild.number == 3 })
        1 * monitor.echoService.postEvent({ it.content.project.lastBuild.number == 6 })
    }

    void 'does not send event for current unchanged build'() {
        given:
        1 * cache.getJobNames(MASTER) >> ['job']
        1 * jenkinsService.projects >> new ProjectsList(
            list: [new Project(name: 'job', lastBuild: new Build(number: 3, building: true))]
        )

        monitor.echoService = Mock(EchoService)

        when:
        List<Map> builds = monitor.changedBuilds(MASTER)

        then:
        1 * cache.getLastBuild(MASTER, 'job') >> [lastBuildLabel: 3, lastBuildBuilding: true]
        0 * jenkinsService.getBuilds('job')
        0 * monitor.echoService.postEvent(_)
    }

    void 'does not send event for past build with already sent event'() {
        given:
        1 * cache.getJobNames(MASTER) >> ['job']
        1 * jenkinsService.projects >> new ProjectsList(
            list: [new Project(name: 'job', lastBuild: new Build(number: 6, building: true))]
        )
        1 * jenkinsService.getBuilds('job') >> new BuildsList(
            list: [
                new Build(number: 5, building: false),
                new Build(number: 6)
            ]
        )

        monitor.echoService = Mock(EchoService)

        when:
        List<Map> builds = monitor.changedBuilds(MASTER)

        then:
        1 * cache.getLastBuild(MASTER, 'job') >> [lastBuildLabel: 5, lastBuildBuilding: false]
        1 * cache.setLastBuild(MASTER, 'job', 6, true)
        0 * monitor.echoService.postEvent({ it.content.project.lastBuild.number == 5 })
        1 * monitor.echoService.postEvent({ it.content.project.lastBuild.number == 6 })
    }

    void 'does not send event for same build'() {
        given:
        1 * cache.getJobNames(MASTER) >> ['job']
        1 * jenkinsService.projects >> new ProjectsList(
            list: [new Project(name: 'job', lastBuild: new Build(number: 6, building: true))]
        )

        monitor.echoService = Mock(EchoService)

        when:
        List<Map> builds = monitor.changedBuilds(MASTER)

        then:
        1 * cache.getLastBuild(MASTER, 'job') >> [lastBuildLabel: 6, lastBuildBuilding: true]
        0 * jenkinsService.getBuilds('job')
        0 * monitor.echoService.postEvent({ _ })
    }


    void 'sends event for same build that has finished'() {
        given:
        1 * cache.getJobNames(MASTER) >> ['job']
        1 * jenkinsService.projects >> new ProjectsList(
            list: [new Project(name: 'job', lastBuild: new Build(number: 6, building: true))]
        )

        monitor.echoService = Mock(EchoService)

        when:
        List<Map> builds = monitor.changedBuilds(MASTER)

        then:
        1 * cache.getLastBuild(MASTER, 'job') >> [lastBuildLabel: 6, lastBuildBuilding: false]
        1 * monitor.echoService.postEvent({ it.content.project.lastBuild.number == 6 })
    }

    @Unroll
    void 'should only publish events if build has been previously seen'() {
        def echoService = Mock(EchoService)
        def project = new Project(name: "project")
        def master = "master"

        when:
        monitor.postEvent(
            echoService, cachedBuilds, project, master
        )

        then:
        echoServiceCallCount * echoService.postEvent({ BuildEvent event ->
            assert event.content.project == project
            assert event.content.master == master
            return true
        })

        when: "should short circuit if `echoService` is not available"
        monitor.postEvent(null, ["job1"], project, master)

        then:
        notThrown(NullPointerException)

        where:
        cachedBuilds || echoServiceCallCount
        null         || 0
        []           || 0
        ["job1"]     || 1

    }
}
