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

import com.netflix.spectator.api.NoopRegistry
import com.netflix.spinnaker.igor.IgorConfigurationProperties
import com.netflix.spinnaker.igor.config.JenkinsProperties
import com.netflix.spinnaker.igor.history.EchoService
import com.netflix.spinnaker.igor.history.model.Event
import com.netflix.spinnaker.igor.jenkins.client.model.Build
import com.netflix.spinnaker.igor.jenkins.client.model.BuildsList
import com.netflix.spinnaker.igor.jenkins.client.model.Project
import com.netflix.spinnaker.igor.jenkins.client.model.ProjectsList
import com.netflix.spinnaker.igor.jenkins.service.JenkinsService
import com.netflix.spinnaker.igor.polling.PollContext
import com.netflix.spinnaker.igor.service.BuildServices
import org.slf4j.Logger
import retrofit.RetrofitError
import rx.schedulers.Schedulers
import spock.lang.Specification
/**
 * Tests for JenkinsBuildMonitor
 */
@SuppressWarnings(['DuplicateNumberLiteral', 'PropertyName'])
class JenkinsBuildMonitorSpec extends Specification {

    JenkinsCache cache = Mock(JenkinsCache)
    JenkinsService jenkinsService = Mock(JenkinsService)
    EchoService echoService = Mock()
    IgorConfigurationProperties igorConfigurationProperties = new IgorConfigurationProperties()
    JenkinsBuildMonitor monitor

    final MASTER = 'MASTER'

    void setup() {
        def buildServices = new BuildServices()
        buildServices.addServices([MASTER: jenkinsService])
        monitor = new JenkinsBuildMonitor(
            igorConfigurationProperties,
            new NoopRegistry(),
            Optional.empty(),
            Optional.empty(),
            cache,
            buildServices,
            true,
            Optional.of(echoService),
            new JenkinsProperties()
        )

        monitor.worker = Schedulers.immediate().createWorker()
    }

    def 'should handle any failure to talk to jenkins graciously' () {
        given:
        jenkinsService.getProjects().getList() >> new Exception("failed")

        when:
        monitor.pollSingle(new PollContext(MASTER))

        then:
        notThrown(Exception)
    }

    def 'should skip a job with no builds'() {
        given:
        jenkinsService.getProjects() >> new ProjectsList(list: [new Project(name: 'job2', lastBuild: null)])

        when:
        monitor.pollSingle(new PollContext(MASTER))

        then:
        0 * cache.getLastPollCycleTimestamp(MASTER, 'job2')
        0 * cache.setLastPollCycleTimestamp(_,_,_)
    }

    def 'should process on first build'() {
        given: 'the first time a build is seen'
        Long previousCursor = null //indicating a first build
        def lastBuild = new Build(number: 1, timestamp: '1494624092610', building: false, result: 'SUCCESS')

        and:
        cache.getLastPollCycleTimestamp(MASTER, 'job') >> previousCursor
        jenkinsService.getProjects() >> new ProjectsList(list: [ new Project(name: 'job', lastBuild: lastBuild) ])
        cache.getEventPosted(_,_,_,_) >> false
        jenkinsService.getBuilds('job') >> [lastBuild ]

        when:
        monitor.pollSingle(new PollContext(MASTER))

        then:
        1 * echoService.postEvent({ it.content.project.lastBuild.number == 1 && it.content.project.lastBuild.result == 'SUCCESS'} as Event)
    }

    def 'should process on first build but not send notifications'() {
        given: 'the first time a build is seen'
        Long previousCursor = null //indicating a first build
        def lastBuild = new Build(number: 1, timestamp: '1494624092610', building: false, result: 'SUCCESS')

        and:
        cache.getLastPollCycleTimestamp(MASTER, 'job') >> previousCursor
        jenkinsService.getProjects() >> new ProjectsList(list: [ new Project(name: 'job', lastBuild: lastBuild) ])
        cache.getEventPosted(_,_,_,_) >> false
        jenkinsService.getBuilds('job') >> [lastBuild ]

        when:
        monitor.pollSingle(new PollContext(MASTER).fastForward())

        then:
        0 * echoService.postEvent(_)
    }

    def 'should only post an event for completed builds between last poll and last build'() {
        given:
        def previousCursor = '1494624092609'
        def stamp1 = '1494624092610'
        def stamp2 = '1494624092611'
        def stamp3 = '1494624092612'
        def lastBuild = new Build(number: 40, timestamp: stamp3)
        def stamp4 = '1494624092613'

        and: 'previousCursor(lower bound) < stamp1 < stamp2 < stamp3(upper bound) < stamp4'
        assert new Date(previousCursor as Long) < new Date(stamp1 as Long)
        assert new Date(stamp1 as Long) < new Date(stamp2 as Long) &&  new Date(stamp2 as Long) < new Date(stamp3 as Long)
        assert new Date(stamp3 as Long) < new Date(stamp4 as Long)

        and:
        cache.getLastPollCycleTimestamp(MASTER, 'job') >> (previousCursor as Long)
        jenkinsService.getProjects() >> new ProjectsList(list: [ new Project(name: 'job', lastBuild: lastBuild) ])
        cache.getEventPosted(_,_,_,_) >> false
        jenkinsService.getBuilds('job') >> [
            new Build(number: 1, timestamp: stamp1, building: false, result: 'SUCCESS'),
            new Build(number: 2, timestamp: stamp1, building: true, result: null),
            new Build(number: 3, timestamp: stamp2, building: false, result: 'SUCCESS'),
            new Build(number: 4, timestamp: stamp4, building: false, result: 'SUCCESS'),
            new Build(number: 5, building: false, result: 'SUCCESS')
        ]

        when:
        monitor.pollSingle(new PollContext(MASTER))

        then: 'only builds between lowerBound(previousCursor) and upperbound(stamp3) will fire events'
        1 * echoService.postEvent({ it.content.project.lastBuild.number == 1 && it.content.project.lastBuild.result == 'SUCCESS'} as Event)
        1 * echoService.postEvent({ it.content.project.lastBuild.number == 3 && it.content.project.lastBuild.result == 'SUCCESS'} as Event)
    }

    def 'should advance the lower bound cursor when all jobs complete'() {
        given:
        def previousCursor = '1494624092609'
        def stamp1 = '1494624092610'
        def stamp2 = '1494624092611'
        def stamp3 = '1494624092612'
        def lastBuild = new Build(number: 40, timestamp: stamp3)
        def stamp4 = '1494624092613'

        and: 'previousCursor(lower bound) < stamp1 < stamp2 < stamp3(upper bound) < stamp4'
        assert new Date(previousCursor as Long) < new Date(stamp1 as Long)
        assert new Date(stamp1 as Long) < new Date(stamp2 as Long) &&  new Date(stamp2 as Long) < new Date(stamp3 as Long)
        assert new Date(stamp3 as Long) < new Date(stamp4 as Long)

        and:
        cache.getLastPollCycleTimestamp(MASTER, 'job') >> (previousCursor as Long)
        jenkinsService.getProjects() >> new ProjectsList(list: [ new Project(name: 'job', lastBuild: lastBuild) ])
        cache.getEventPosted(_,_,_,_) >> false
        jenkinsService.getBuilds('job') >> [
            new Build(number: 1, timestamp: stamp1, building: false, result: 'SUCCESS'),
            new Build(number: 2, timestamp: stamp1, building: false, result: 'FAILURE'),
            new Build(number: 3, timestamp: stamp2, building: false, result: 'SUCCESS'),
            new Build(number: 4, timestamp: stamp4, building: false, result: 'SUCCESS')
        ]

        when:
        monitor.pollSingle(new PollContext(MASTER))

        then: 'only builds between lowerBound(previousCursor) and upperbound(stamp3) will fire events'
        1 * echoService.postEvent({ it.content.project.lastBuild.number == 1 && it.content.project.lastBuild.result == 'SUCCESS'} as Event)
        1 * echoService.postEvent({ it.content.project.lastBuild.number == 2 && it.content.project.lastBuild.result == 'FAILURE'} as Event)
        1 * echoService.postEvent({ it.content.project.lastBuild.number == 3 && it.content.project.lastBuild.result == 'SUCCESS'} as Event)

        and: 'prune old markers and set new cursor'
        1 * cache.pruneOldMarkers(MASTER, 'job', 1494624092609)
        1 * cache.setLastPollCycleTimestamp(MASTER, 'job', 1494624092612)
    }


    def 'should filter out builds older than look back window'() {
        given:
        long now = System.currentTimeMillis()
        long nowMinus30min = now - (30 * 60 * 1000) // 30 minutes ago
        long nowMinus10min = now - (10 * 60 * 1000) // 10 minutes ago
        long nowMinus5min = now - (5 * 60 * 1000)  // 5  minutes ago
        long durationOf1min = 60000

        and: 'a 6 minutes total lookBack window'
        igorConfigurationProperties.spinnaker.build.pollInterval = 60
        igorConfigurationProperties.spinnaker.build.lookBackWindowMins = 5
        igorConfigurationProperties.spinnaker.build.processBuildsOlderThanLookBackWindow = false

        and: 'Three projects'
        jenkinsService.getProjects() >> new ProjectsList(list: [ new Project(name: 'job1', lastBuild: new Build(number: 3, timestamp: now)) ])
        jenkinsService.getProjects() >> new ProjectsList(list: [ new Project(name: 'job2', lastBuild: new Build(number: 3, timestamp: now)) ])
        jenkinsService.getProjects() >> new ProjectsList(list: [ new Project(name: 'job3', lastBuild: new Build(number: 3, timestamp: now)) ])

        jenkinsService.getBuilds('job1') >> [
            new Build(number: 1, timestamp: nowMinus30min, building: false, result: 'SUCCESS', duration: durationOf1min),
            new Build(number: 2, timestamp: nowMinus10min, building: false, result: 'FAILURE', duration: durationOf1min),
            new Build(number: 3, timestamp: nowMinus5min, building: false, result: 'SUCCESS', duration: durationOf1min)
        ]

        jenkinsService.getBuilds('job3') >> [
            new Build(number: 1, timestamp: nowMinus30min, building: false, result: 'SUCCESS', duration: durationOf1min),
            new Build(number: 2, timestamp: nowMinus10min, building: false, result: 'FAILURE', duration: durationOf1min),
            new Build(number: 3, timestamp: nowMinus5min, building: false, result: 'SUCCESS', duration: durationOf1min)
        ]

        when:
        monitor.pollSingle(new PollContext(MASTER))

        then: 'build #3 only will be processed'
        0 * echoService.postEvent({ it.content.project.lastBuild.number == 1 && it.content.project.lastBuild.result == 'SUCCESS'} as Event)
        0 * echoService.postEvent({ it.content.project.lastBuild.number == 2 && it.content.project.lastBuild.result == 'FAILURE'} as Event)
        1 * echoService.postEvent({ it.content.project.lastBuild.number == 3 && it.content.project.lastBuild.result == 'SUCCESS'} as Event)
    }

    def 'should continue processing other builds from a master even if one or more build fetches fail'() {
        given:
        long now = System.currentTimeMillis()
        long nowMinus30min = now - (30 * 60 * 1000) // 30 minutes ago
        long durationOf1min = 60000

        igorConfigurationProperties.spinnaker.build.processBuildsOlderThanLookBackWindow = true

        and: 'three jobs in a master'
        jenkinsService.getProjects() >> new ProjectsList(list: [
            new Project(name: 'job1', lastBuild: new Build(number: 1, timestamp: now)),
            new Project(name: 'job2', lastBuild: new Build(number: 2, timestamp: now)),
            new Project(name: 'job3', lastBuild: new Build(number: 3, timestamp: now))
        ])

        and: 'one failed getBuilds() and two successful'
        jenkinsService.getBuilds('job1') >> [
            new Build(number: 1, timestamp: nowMinus30min, building: false, result: 'SUCCESS', duration: durationOf1min)
        ]

        def retrofitEx = RetrofitError.unexpectedError("http://retro.fit/mock/error", new Exception('mock root cause'));
        jenkinsService.getBuilds('job2') >> { throw new RuntimeException ("Mocked failure while fetching 'job2'", retrofitEx) }

        jenkinsService.getBuilds('job3') >> [
            new Build(number: 3, timestamp: nowMinus30min, building: false, result: 'SUCCESS', duration: durationOf1min)
        ]

        and:
        monitor.log = Mock(Logger);

        when:
        monitor.pollSingle(new PollContext(MASTER))

        then: 'Builds are processed for job1'
        1 * echoService.postEvent({ it.content.project.name == 'job1'} as Event)

        and: 'Errors are logged for job2; no builds are processed'
        1 * monitor.log.error('Error communicating with jenkins for [{}:{}]: {}', _)
        1 * monitor.log.error('Error processing builds for [{}:{}]', _)
        0 * echoService.postEvent({ it.content.project.name == 'job2'} as Event)

        and: 'Builds are not processed for job3'
        1 * echoService.postEvent({ it.content.project.name == 'job3'} as Event)
    }
}
