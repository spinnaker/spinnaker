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

import com.netflix.spinnaker.igor.jenkins.client.JenkinsClient
import com.netflix.spinnaker.igor.jenkins.client.JenkinsMasters
import com.netflix.spinnaker.igor.jenkins.client.model.Build
import com.netflix.spinnaker.igor.jenkins.client.model.Project
import com.netflix.spinnaker.igor.jenkins.client.model.ProjectsList
import spock.lang.Specification

/**
 * Tests for BuildMonitor
 */
@SuppressWarnings(['DuplicateNumberLiteral', 'PropertyName'])
class BuildMonitorSpec extends Specification {

    JenkinsCache cache = Mock(JenkinsCache)
    JenkinsClient client = Mock(JenkinsClient)
    BuildMonitor monitor

    final MASTER = 'MASTER'

    void setup() {
        monitor = new BuildMonitor(cache: cache, jenkinsMasters: new JenkinsMasters(map: [MASTER: client]))
    }

    void 'flag a new build not found in the cache'() {
        given:
        1 * cache.getJobNames(MASTER) >> ['job1']
        1 * client.projects >> new ProjectsList(list: [new Project(name: 'job2', lastBuild : new Build(number: 1))])

        when:
        List<Map> builds = monitor.changedBuilds(MASTER)

        then:
        0 * cache.getLastBuild(MASTER, 'job1')
        1 * cache.setLastBuild(MASTER, 'job2', 1, "")
        builds.size() == 1
        builds[0].current.name == 'job2'
        builds[0].current.lastBuild.number == 1
        builds[0].previous == null
    }

    void 'flag existing build with a higher number as changed'() {
        given:
        1 * cache.getJobNames(MASTER) >> ['job2']
        1 * client.projects >> new ProjectsList(list: [new Project(name: 'job2',  lastBuild : new Build(number: 5))])

        when:
        List<Map> builds = monitor.changedBuilds(MASTER)

        then:
        1 * cache.getLastBuild(MASTER, 'job2') >> [lastBuildLabel: 3]
        1 * cache.setLastBuild(MASTER, 'job2', 5, "")
        builds[0].current.lastBuild.number == 5
        builds[0].previous.lastBuildLabel == 3
    }

    void 'flag builds in a different state as changed'() {
        given:
        1 * cache.getJobNames(MASTER) >> ['job3']
        1 * client.projects >> new ProjectsList(
            list: [new Project(name: 'job3', lastBuild : new Build(number: 5, result: 'FAILED'))]
        )

        when:
        List<Map> builds = monitor.changedBuilds(MASTER)

        then:
        1 * cache.getLastBuild(MASTER, 'job3') >> [lastBuildLabel: 5, lastBuildStatus: 'RUNNING']
        1 * cache.setLastBuild(MASTER, 'job3', 5, 'FAILED')
        builds[0].current.lastBuild.number == 5
        builds[0].previous.lastBuildLabel == 5
        builds[0].current.lastBuild.result == 'FAILED'
        builds[0].previous.lastBuildStatus == 'RUNNING'
    }

    void 'stale builds are removed'() {
        given:
        1 * cache.getJobNames(MASTER) >> ['job3', 'job4']
        1 * client.projects >> new ProjectsList(list: [])

        when:
        monitor.changedBuilds(MASTER)

        then:
        1 * cache.remove(MASTER, 'job3')
        1 * cache.remove(MASTER, 'job4')
    }

}
