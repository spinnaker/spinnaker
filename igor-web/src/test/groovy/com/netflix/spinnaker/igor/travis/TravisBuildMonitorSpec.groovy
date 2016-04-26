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
import com.netflix.spinnaker.igor.build.model.GenericGitRevision
import com.netflix.spinnaker.igor.history.EchoService
import com.netflix.spinnaker.igor.service.BuildMasters
import com.netflix.spinnaker.igor.travis.client.model.Commit
import com.netflix.spinnaker.igor.travis.client.model.Repo
import com.netflix.spinnaker.igor.travis.service.TravisService
import spock.lang.Specification

class TravisBuildMonitorSpec extends Specification {
    BuildCache buildCache = Mock(BuildCache)
    TravisService travisService = Mock(TravisService)
    TravisBuildMonitor travisBuildMonitor

    final String MASTER = "MASTER"

    void setup() {
        travisBuildMonitor = new TravisBuildMonitor(buildCache: buildCache, buildMasters: new BuildMasters(map: [MASTER : travisService]))
    }

    void 'flag a new build not found in the cache'() {
        Repo repo = new Repo()
        repo.slug = "test-org/test-repo"
        repo.lastBuildNumber = 4
        repo.lastBuildState = "passed"
        List<Repo> repos = [repo]

        given:
        1 * buildCache.getJobNames(MASTER) >> ['test-org/test-repo']

        when:
        List<Map> builds = travisBuildMonitor.changedBuilds(MASTER)

        then:
        1 * travisService.setAccessToken()
        1 * travisService.getReposForAccounts() >> repos

        1 * buildCache.getLastBuild(MASTER, 'test-org/test-repo') >> [lastBuildLabel: 3]
        1 * buildCache.setLastBuild(MASTER, 'test-org/test-repo', 4, false)
        builds.size() == 1
        builds[0].current.slug == 'test-org/test-repo'
        builds[0].current.lastBuildNumber == 4
        builds[0].previous.lastBuildLabel == 3
    }


    void 'send events for build both on branch and on repository'() {
        travisBuildMonitor.echoService = Mock(EchoService)

        Commit commit = Mock(Commit)
        Repo repo = new Repo()
        repo.slug = "test-org/test-repo"
        repo.lastBuildNumber = 4
        repo.lastBuildState = "passed"
        List<Repo> repos = [repo]

        given:
        1 * buildCache.getJobNames(MASTER) >> ['test-org/test-repo']

        when:
        List<Map> builds = travisBuildMonitor.changedBuilds(MASTER)

        then:
        1 * travisService.getAccounts()
        1 * travisService.setAccessToken()
        1 * travisService.getReposForAccounts() >> repos
        1 * travisService.getCommit('test-org/test-repo', 4) >> commit
        1 * commit.branchNameWithTagHandling() >> "my_branch"

        1 * buildCache.getLastBuild(MASTER, 'test-org/test-repo') >> [lastBuildLabel: 3]
        1 * buildCache.setLastBuild(MASTER, 'test-org/test-repo', 4, false)
        1 * buildCache.setLastBuild(MASTER, 'test-org/test-repo/my_branch', 4, false)

        1 * travisBuildMonitor.echoService.postEvent({
            it.content.project.name == "test-org/test-repo"
            it.content.project.lastBuild.number == 4
        })
        1 * travisBuildMonitor.echoService.postEvent({
            it.content.project.name == "test-org/test-repo/my_branch"
            it.content.project.lastBuild.number == 4
        })

    }
}
