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
import com.netflix.spinnaker.igor.config.TravisProperties
import com.netflix.spinnaker.igor.history.EchoService
import com.netflix.spinnaker.igor.service.BuildServices
import com.netflix.spinnaker.igor.travis.client.model.Repo
import com.netflix.spinnaker.igor.travis.client.model.v3.TravisBuildState
import com.netflix.spinnaker.igor.travis.client.model.v3.V3Build
import com.netflix.spinnaker.igor.travis.client.model.v3.V3Repository
import com.netflix.spinnaker.igor.travis.service.TravisBuildConverter
import com.netflix.spinnaker.igor.travis.service.TravisService
import spock.lang.Specification

import java.time.Instant
import java.time.temporal.ChronoUnit

class TravisBuildMonitorSpec extends Specification {
    BuildCache buildCache = Mock(BuildCache)
    TravisService travisService = Mock(TravisService)
    EchoService echoService = Mock()
    TravisBuildMonitor travisBuildMonitor

    final String MASTER = "MASTER"
    final int CACHED_JOB_TTL_SECONDS = 172800
    final int CACHED_JOB_TTL_DAYS = 2

    void setup() {
        def travisProperties = new TravisProperties(cachedJobTTLDays: CACHED_JOB_TTL_DAYS)
        def buildServices = new BuildServices()
        buildServices.addServices([MASTER: travisService])
        travisBuildMonitor = new TravisBuildMonitor(
            new IgorConfigurationProperties(),
            new NoopRegistry(),
            Optional.empty(),
            buildCache,
            buildServices,
            travisProperties,
            Optional.of(echoService),
            Optional.empty()
        )
        travisService.isLogReady(_) >> true
    }

    void 'flag a new build on master, but do not send event on repo if a newer build is present at repo level'() {
        Repo repo = new Repo()
        repo.slug = "test-org/test-repo"
        repo.lastBuildNumber = 4
        repo.lastBuildState = "passed"
        repo.lastBuildStartedAt = Instant.now()
        List<Repo> repos = [repo]
        V3Build build = Mock(V3Build)
        V3Repository repository = Mock(V3Repository)

        when:
        List<TravisBuildMonitor.BuildDelta> receivedBuilds = travisBuildMonitor.changedBuilds(MASTER, travisService)
        travisBuildMonitor.commitDelta(new TravisBuildMonitor.BuildPollingDelta(master: MASTER, items: receivedBuilds), true)

        then:
        1 * travisService.getReposForAccounts() >> repos
        1 * travisService.getBuilds(repo, 5) >> [ build ]
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

        receivedBuilds.size() == 1
        receivedBuilds[0].branchedRepoSlug == 'test-org/test-repo/master'
        receivedBuilds[0].currentBuildNum == 4
        receivedBuilds[0].previousBuildNum == 3
    }

    void 'ignore old build not found in the cache'() {
        Repo oldRepo = new Repo()
        Instant now = Instant.now()
        oldRepo.lastBuildStartedAt = now.minus(travisBuildMonitor.travisProperties.cachedJobTTLDays, ChronoUnit.DAYS)
        Repo noLastBuildStartedAtRepo = new Repo()
        noLastBuildStartedAtRepo.lastBuildStartedAt = null
        Repo repo = new Repo()
        repo.slug = "test-org/test-repo"
        repo.lastBuildNumber = 4
        repo.lastBuildState = "passed"
        repo.lastBuildStartedAt = now.minus(travisBuildMonitor.travisProperties.cachedJobTTLDays-1, ChronoUnit.DAYS)
        List<Repo> repos = [oldRepo, repo, noLastBuildStartedAtRepo]
        V3Build build = Mock(V3Build)
        V3Repository repository = Mock(V3Repository)

        when:
        List<TravisBuildMonitor.BuildDelta> builds = travisBuildMonitor.changedBuilds(MASTER, travisService)
        travisBuildMonitor.commitDelta(new TravisBuildMonitor.BuildPollingDelta(master: MASTER, items: builds), true)

        then:
        1 * travisService.getReposForAccounts() >> repos
        1 * travisService.getBuilds(repo, 5) >> [ build ]
        build.branchedRepoSlug() >> "test-org/test-repo/master"
        build.getNumber() >> 4
        build.getState() >> TravisBuildState.passed
        build.jobs >> []
        build.repository >> repository
        repository.slug >> 'test-org/test-repo'

        1 * travisService.getGenericBuild(build, true) >> TravisBuildConverter.genericBuild(build, MASTER)
        1 * buildCache.getLastBuild(MASTER, 'test-org/test-repo/master', false) >> 3
        1 * buildCache.setLastBuild(MASTER, 'test-org/test-repo/master', 4, false, CACHED_JOB_TTL_SECONDS)
        1 * buildCache.setLastBuild(MASTER, 'test-org/test-repo', 4, false, CACHED_JOB_TTL_SECONDS)

        expect:
        builds.size() == 1
        builds[0].branchedRepoSlug == 'test-org/test-repo/master'
        builds[0].currentBuildNum == 4
        builds[0].previousBuildNum == 3
    }

    void 'send events for build both on branch and on repository'() {
        Repo repo = new Repo()
        repo.slug = "test-org/test-repo"
        repo.lastBuildNumber = 4
        repo.lastBuildState = "passed"
        repo.lastBuildStartedAt = Instant.now()
        List<Repo> repos = [repo]
        V3Build build = Mock(V3Build)
        V3Repository repository = Mock(V3Repository)

        when:
        List<TravisBuildMonitor.BuildDelta> builds = travisBuildMonitor.changedBuilds(MASTER, travisService)
        travisBuildMonitor.commitDelta(new TravisBuildMonitor.BuildPollingDelta(master: MASTER, items: builds), true)

        then:
        1 * travisService.getReposForAccounts() >> repos
        1 * travisService.getBuilds(repo, 5) >> [ build ]
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
        })
        1 * echoService.postEvent({
            it.content.project.name == "test-org/test-repo/my_branch"
            it.content.project.lastBuild.number == 4
        })
    }

    void 'suppress echo notifications'() {
        Repo repo = new Repo()
        repo.slug = "test-org/test-repo"
        repo.lastBuildNumber = 4
        repo.lastBuildState = "passed"
        repo.lastBuildStartedAt = Instant.now()
        List<Repo> repos = [repo]
        V3Build build = Mock(V3Build)
        V3Repository repository = Mock(V3Repository)

        when:
        List<TravisBuildMonitor.BuildDelta> builds = travisBuildMonitor.changedBuilds(MASTER, travisService)
        travisBuildMonitor.commitDelta(new TravisBuildMonitor.BuildPollingDelta(master: MASTER, items: builds), false)

        then:
        1 * travisService.getReposForAccounts() >> repos
        1 * travisService.getBuilds(repo, 5) >> [ build ]
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
        Repo repo = new Repo()
        repo.slug = "test-org/test-repo"
        repo.lastBuildNumber = 4
        repo.lastBuildState = "passed"
        repo.lastBuildStartedAt = Instant.now()
        List<Repo> repos = [repo]
        V3Build build = Mock(V3Build)
        V3Build buildDifferentBranch = Mock(V3Build)
        V3Repository repository = Mock(V3Repository)

        when:
        List<TravisBuildMonitor.BuildDelta> result = travisBuildMonitor.changedBuilds(MASTER, travisService)
        travisBuildMonitor.commitDelta(new TravisBuildMonitor.BuildPollingDelta(master: MASTER, items: result), true)

        then:
        1 * travisService.getReposForAccounts() >> repos
        1 * travisService.getBuilds(repo, 5) >> [ build, buildDifferentBranch ]
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
        })
        1 * echoService.postEvent({
            it.content.project.name == "test-org/test-repo" &&
                it.content.project.lastBuild.number == 4
        })

        1 * echoService.postEvent({
            it.content.project.name == "test-org/test-repo" &&
                it.content.project.lastBuild.number == 3
        })
        1 * echoService.postEvent({
            it.content.project.name == "test-org/test-repo/different_branch" &&
                it.content.project.lastBuild.number == 3
        })
    }
}
