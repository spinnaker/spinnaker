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

import com.netflix.spectator.api.NoopRegistry
import com.netflix.spinnaker.igor.IgorConfigurationProperties
import com.netflix.spinnaker.igor.build.BuildCache
import com.netflix.spinnaker.igor.history.EchoService
import com.netflix.spinnaker.igor.polling.PollContext
import com.netflix.spinnaker.igor.service.BuildServices
import com.netflix.spinnaker.igor.travis.client.model.v3.TravisBuildState
import com.netflix.spinnaker.igor.travis.client.model.v3.V3Branch
import com.netflix.spinnaker.igor.travis.client.model.v3.V3Build
import com.netflix.spinnaker.igor.travis.client.model.v3.V3Commit
import com.netflix.spinnaker.igor.travis.client.model.v3.V3Repository
import com.netflix.spinnaker.igor.travis.config.TravisProperties
import com.netflix.spinnaker.igor.travis.service.TravisBuildConverter
import com.netflix.spinnaker.igor.travis.service.TravisService
import com.netflix.spinnaker.kork.discovery.DiscoveryStatusListener
import com.netflix.spinnaker.kork.dynamicconfig.DynamicConfigService
import org.springframework.scheduling.TaskScheduler
import retrofit2.mock.Calls
import spock.lang.Specification

class TravisBuildMonitorSpec extends Specification {
    BuildCache buildCache = Mock(BuildCache)
    TravisService travisService = Mock(TravisService)
    EchoService echoService = Mock()
    TravisBuildMonitor travisBuildMonitor

    String MASTER = "MASTER"
    int CACHED_JOB_TTL_SECONDS = 172800
    int CACHED_JOB_TTL_DAYS = 2

    void setup() {
        def travisProperties = new TravisProperties(cachedJobTTLDays: CACHED_JOB_TTL_DAYS)
        def buildServices = new BuildServices()
        buildServices.addServices([MASTER: travisService])
        travisBuildMonitor = new TravisBuildMonitor(
            new IgorConfigurationProperties(),
            new NoopRegistry(),
            new DynamicConfigService.NoopDynamicConfig(),
            new DiscoveryStatusListener(true),
            buildCache,
            buildServices,
            travisProperties,
            Optional.of(echoService),
            Optional.empty(),
            Mock(TaskScheduler)
        )
        travisService.isLogReady(_) >> true
        buildCache.getTrackedBuilds(MASTER) >> []
    }

    void 'flag a new build on master, but do not send event on repo if a newer build is present at repo level'() {
        V3Build build = Mock(V3Build)
        V3Repository repository = Mock(V3Repository)
        echoService.postEvent(_) >> Calls.response(null)

        when:
        TravisBuildMonitor.BuildPollingDelta buildPollingDelta = travisBuildMonitor.generateDelta(new PollContext(MASTER))
        travisBuildMonitor.commitDelta(buildPollingDelta, true)

        then:
        1 * travisService.getLatestBuilds() >> [ build ]
        build.branchedRepoSlug() >> "test-org/test-repo/master"
        build.jobs >> []
        build.getNumber() >> 4
        build.getState() >> TravisBuildState.passed
        build.repository >> repository
        repository.slug >> 'test-org/test-repo'

        1 * travisService.getGenericBuild(build, true) >> TravisBuildConverter.genericBuild(build, MASTER)
        1 * buildCache.getLastBuild(MASTER, 'test-org/test-repo/master', false) >> 3
        1 * buildCache.getLastBuild(MASTER, 'test-org/test-repo', false) >> 5
        1 * buildCache.setLastBuild(MASTER, 'test-org/test-repo/master', 4, false, CACHED_JOB_TTL_SECONDS)
        0 * buildCache.setLastBuild(MASTER, 'test-org/test-repo', 4, false, CACHED_JOB_TTL_SECONDS)

        buildPollingDelta.items.size() == 1
        buildPollingDelta.items[0].branchedRepoSlug == 'test-org/test-repo/master'
        buildPollingDelta.items[0].currentBuildNum == 4
        buildPollingDelta.items[0].previousBuildNum == 3
    }

    void 'send events for build both on branch and on repository'() {
        V3Build build = Mock(V3Build)
        V3Repository repository = Mock(V3Repository)

        when:
        TravisBuildMonitor.BuildPollingDelta buildPollingDelta = travisBuildMonitor.generateDelta(new PollContext(MASTER))
        travisBuildMonitor.commitDelta(buildPollingDelta, true)

        then:
        1 * travisService.getLatestBuilds() >> [ build ]
        build.branchedRepoSlug() >> "test-org/test-repo/my_branch"
        build.getNumber() >> 4
        build.getState() >> TravisBuildState.passed
        build.jobs >> []
        build.repository >> repository
        repository.slug >> 'test-org/test-repo'

        1 * travisService.getGenericBuild(build, true) >> TravisBuildConverter.genericBuild(build, MASTER)
        1 * buildCache.getLastBuild(MASTER, 'test-org/test-repo/my_branch', false) >> 3
        1 * buildCache.setLastBuild(MASTER, 'test-org/test-repo/my_branch', 4, false, CACHED_JOB_TTL_SECONDS)
        1 * buildCache.setLastBuild(MASTER, 'test-org/test-repo', 4, false, CACHED_JOB_TTL_SECONDS)

        1 * echoService.postEvent({
            it.content.project.name == "test-org/test-repo"
            it.content.project.lastBuild.number == 4
        }) >> Calls.response(null)
        1 * echoService.postEvent({
            it.content.project.name == "test-org/test-repo/my_branch"
            it.content.project.lastBuild.number == 4
        }) >> Calls.response(null)
    }

    void 'suppress echo notifications'() {
        V3Build build = Mock(V3Build)
        V3Repository repository = Mock(V3Repository)

        when:
        TravisBuildMonitor.BuildPollingDelta buildPollingDelta = travisBuildMonitor.generateDelta(new PollContext(MASTER))
        travisBuildMonitor.commitDelta(buildPollingDelta, false)

        then:
        1 * travisService.getLatestBuilds() >> [ build ]
        build.branchedRepoSlug() >> "test-org/test-repo/my_branch"
        build.getNumber() >> 4

        1 * buildCache.getLastBuild(MASTER, 'test-org/test-repo/my_branch', false) >> 3
        1 * buildCache.setLastBuild(MASTER, 'test-org/test-repo/my_branch', 4, false, CACHED_JOB_TTL_SECONDS)
        1 * buildCache.setLastBuild(MASTER, 'test-org/test-repo', 4, false, CACHED_JOB_TTL_SECONDS)

        build.jobs >> []
        build.repository >> repository
        repository.slug >> 'test-org/test-repo'
        build.getState() >> "passed"

        0 * echoService.postEvent(_)
    }

    void 'send events when two different branches build at the same time.'() {
        V3Build build = Mock(V3Build)
        V3Build buildDifferentBranch = Mock(V3Build)
        V3Repository repository = Mock(V3Repository)

        when:
        TravisBuildMonitor.BuildPollingDelta buildPollingDelta = travisBuildMonitor.generateDelta(new PollContext(MASTER))
        travisBuildMonitor.commitDelta(buildPollingDelta, true)

        then:
        1 * travisService.getLatestBuilds() >> [ build, buildDifferentBranch ]
        build.branchedRepoSlug() >> "test-org/test-repo/my_branch"
        build.getNumber() >> 4
        build.getState() >> TravisBuildState.passed
        build.jobs >> []
        build.repository >> repository
        buildDifferentBranch.branchedRepoSlug() >> "test-org/test-repo/different_branch"
        buildDifferentBranch.getNumber() >> 3
        buildDifferentBranch.getState() >> TravisBuildState.passed
        buildDifferentBranch.jobs >> []
        buildDifferentBranch.repository >> repository
        repository.slug >> 'test-org/test-repo'
        1 * travisService.getGenericBuild(build, true) >> TravisBuildConverter.genericBuild(build, MASTER)
        1 * travisService.getGenericBuild(buildDifferentBranch, true) >> TravisBuildConverter.genericBuild(buildDifferentBranch, MASTER)
        1 * buildCache.getLastBuild(MASTER, 'test-org/test-repo/my_branch', false) >> 2
        1 * buildCache.setLastBuild(MASTER, 'test-org/test-repo/my_branch', 4, false, CACHED_JOB_TTL_SECONDS)
        1 * buildCache.setLastBuild(MASTER, 'test-org/test-repo', 4, false, CACHED_JOB_TTL_SECONDS)
        1 * buildCache.setLastBuild(MASTER, 'test-org/test-repo', 3, false, CACHED_JOB_TTL_SECONDS)

        1 * buildCache.getLastBuild(MASTER, 'test-org/test-repo/different_branch', false) >> 1
        1 * buildCache.setLastBuild(MASTER, 'test-org/test-repo/different_branch', 3, false, CACHED_JOB_TTL_SECONDS)

        1 * echoService.postEvent({
            it.content.project.name == "test-org/test-repo/my_branch" &&
                it.content.project.lastBuild.number == 4
        }) >> Calls.response(null)
        1 * echoService.postEvent({
            it.content.project.name == "test-org/test-repo" &&
                it.content.project.lastBuild.number == 4
        }) >> Calls.response(null)

        1 * echoService.postEvent({
            it.content.project.name == "test-org/test-repo" &&
                it.content.project.lastBuild.number == 3
        }) >> Calls.response(null)
        1 * echoService.postEvent({
            it.content.project.name == "test-org/test-repo/different_branch" &&
                it.content.project.lastBuild.number == 3
        }) >> Calls.response(null)
    }

    def "should keep track of started builds and monitor them even if they disappear from the Travis API"() {
        V3Build build = new V3Build()
        V3Repository repository = Mock(V3Repository)

        build.commit = Mock(V3Commit)
        build.commit.isTag() >> false
        build.commit.isPullRequest() >> false
        build.branch = new V3Branch()
        build.branch.name = "my_branch"
        build.id = 1337
        build.number = 4
        build.state = TravisBuildState.started
        build.jobs = []
        build.repository = repository
        repository.slug >> 'test-org/test-repo'

        when:
        TravisBuildMonitor.BuildPollingDelta buildPollingDelta = travisBuildMonitor.generateDelta(new PollContext(MASTER))
        travisBuildMonitor.commitDelta(buildPollingDelta, true)

        build.state = TravisBuildState.passed
        buildPollingDelta = travisBuildMonitor.generateDelta(new PollContext(MASTER))
        travisBuildMonitor.commitDelta(buildPollingDelta, true)

        then:
        2 * travisService.getLatestBuilds() >>> [ [ build ], [] ]
        1 * buildCache.setTracking(MASTER, build.getRepository().getSlug(), 1337, TravisBuildMonitor.TRACKING_TTL_SECS)
        2 * buildCache.getTrackedBuilds(MASTER) >> [ [ buildId: "1337" ] ]
        1 * travisService.getV3Build(1337) >> build
        2 * travisService.getGenericBuild(_, _) >> { V3Build b, boolean fetchLogs ->
            TravisBuildConverter.genericBuild(b, MASTER)
        }
        1 * buildCache.getLastBuild(MASTER, 'test-org/test-repo/my_branch', true) >> 3
        1 * buildCache.getLastBuild(MASTER, 'test-org/test-repo/my_branch', false) >> 3
        1 * buildCache.setLastBuild(MASTER, 'test-org/test-repo/my_branch', 4, false, CACHED_JOB_TTL_SECONDS)
        1 * buildCache.setLastBuild(MASTER, 'test-org/test-repo', 4, false, CACHED_JOB_TTL_SECONDS)

        1 * echoService.postEvent({
            it.content.project.name == "test-org/test-repo"
            it.content.project.lastBuild.number == 4
        }) >> Calls.response(null)
        1 * echoService.postEvent({
            it.content.project.name == "test-org/test-repo/my_branch"
            it.content.project.lastBuild.number == 4
        }) >> Calls.response(null)
    }
}
