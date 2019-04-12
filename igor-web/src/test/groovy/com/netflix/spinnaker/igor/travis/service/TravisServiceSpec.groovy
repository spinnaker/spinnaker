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

package com.netflix.spinnaker.igor.travis.service

import com.netflix.spinnaker.fiat.model.resources.Permissions
import com.netflix.spinnaker.igor.build.artifact.decorator.DebDetailsDecorator
import com.netflix.spinnaker.igor.build.artifact.decorator.RpmDetailsDecorator
import com.netflix.spinnaker.igor.build.model.GenericBuild
import com.netflix.spinnaker.igor.build.model.Result
import com.netflix.spinnaker.igor.service.ArtifactDecorator
import com.netflix.spinnaker.igor.travis.TravisCache
import com.netflix.spinnaker.igor.travis.client.TravisClient
import com.netflix.spinnaker.igor.travis.client.model.AccessToken
import com.netflix.spinnaker.igor.travis.client.model.Build
import com.netflix.spinnaker.igor.travis.client.model.Builds
import com.netflix.spinnaker.igor.travis.client.model.Commit
import com.netflix.spinnaker.igor.travis.client.model.RepoRequest
import com.netflix.spinnaker.igor.travis.client.model.TriggerResponse
import com.netflix.spinnaker.igor.travis.client.model.v3.Request
import com.netflix.spinnaker.igor.travis.client.model.v3.TravisBuildType
import com.netflix.spinnaker.igor.travis.client.model.v3.V3Log
import com.netflix.spinnaker.igor.travis.client.model.v3.V3Repository
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

import java.time.Instant

class TravisServiceSpec extends Specification{
    @Shared
    TravisClient client

    @Shared
    TravisService service

    @Shared
    TravisCache travisCache

    @Shared
    Optional<ArtifactDecorator> artifactDecorator

    void setup() {
        client = Mock()
        travisCache = Mock()
        artifactDecorator = Optional.of(new ArtifactDecorator([new DebDetailsDecorator(), new RpmDetailsDecorator()], null))
        service = new TravisService('travis-ci', 'http://my.travis.ci', 'someToken', 25, client, travisCache, artifactDecorator, [], "travis.buildMessage", Permissions.EMPTY)

        AccessToken accessToken = new AccessToken()
        accessToken.accessToken = "someToken"
        service.accessToken = accessToken
    }

    def "getGenericBuild(build, repoSlug)" () {
        given:
        Build build = Mock(Build)
        def v3log = new V3Log()
        def logPart = new V3Log.V3LogPart()
        logPart.content = ""
        logPart.final = true
        logPart.number = 0
        v3log.logParts = [logPart]

        when:
        GenericBuild genericBuild = service.getGenericBuild(build, "some/repo-slug")

        then:
        genericBuild.number == 1337
        genericBuild.result == Result.SUCCESS
        genericBuild.duration == 32
        genericBuild.timestamp == "1458051084000"

        2 * travisCache.getJobLog('travis-ci', 42) >>> [null, ""]
        1 * travisCache.setJobLog('travis-ci', 42, "") >>> [null, ""]
        1 * client.jobLog(_, 42) >> v3log
        2 * build.job_ids >> [42]
        2 * build.number >> 1337
        2 * build.state >> 'passed'
        1 * build.duration >> 32
        1 * build.finishedAt >> Instant.now()
        1 * build.getTimestamp() >> 1458051084000
    }

    @Unroll
    def "cleanRepoSlug(repoSlug)"() {
        expect:
        service.cleanRepoSlug(inputRepoSlug) == expectedRepoSlug

        where:
        inputRepoSlug                     || expectedRepoSlug
        "my-org/repo"                     || "my-org/repo"
        "my-org/repo/branch"              || "my-org/repo"
        "my-org/repo/branch/with/slashes" || "my-org/repo"

    }

    @Unroll
    def "branchFromRepoSlug(repoSlug)"() {
        expect:
        service.branchFromRepoSlug(inputRepoSlug) == expectedBranch

        where:
        inputRepoSlug                     || expectedBranch
        "my-org/repo"                     || ""
        "my-org/repo/branch"              || "branch"
        "my-org/repo/branch/with/slashes" || "branch/with/slashes"
        "m/r/some_pull_request_in_name"   || "some_pull_request_in_name"
        "my-org/repo/pull_request_master" || "master"
        "my-org/repo/tags"                || ""
    }

    @Unroll
    def "branchIsTagsVirtualBranch(repoSlug)"() {
        expect:
        service.branchIsTagsVirtualBranch(inputRepoSlug) == expectedBranch

        where:
        inputRepoSlug                     || expectedBranch
        "my-org/repo"                     || false
        "my-org/repo/branch"              || false
        "my-org/repo/branch/with/slashes" || false
        "my-org/repo/pull_request_master" || false
        "my-org/repo/tags"                || true
    }

    @Unroll
    def "branchIsPullRequestVirtualBranch(repoSlug)"() {
        expect:
        service.branchIsPullRequestVirtualBranch(inputRepoSlug) == expectedBranch

        where:
        inputRepoSlug                     || expectedBranch
        "my-org/repo"                     || false
        "my-org/repo/branch"              || false
        "my-org/repo/branch/with/slashes" || false
        "my-org/repo/pull_request_master" || true
        "m/r/some_pull_request_in_name"   || false
        "my-org/repo/tags"                || false
    }

    def "getCommit(repoSlug, buildNumber)"() {
        given:
        Commit commit = new Commit()
        commit.branch = "1.0"
        commit.compareUrl = "https://github.domain/org/repo/compare/1.0"
        Builds builds = Mock(Builds)

        when:
        Commit fetchedCommit = service.getCommit("org/repo", 38)

        then:
        fetchedCommit.isTag()
        1 * client.builds("token someToken", "org/repo", 38) >> builds
        3 * builds.commits >> [commit]
    }

    def "getCommit(repoSlug, buildNumber) when no commit is found"() {
        given:
        Builds builds = Mock(Builds)

        when:
        service.getCommit("org/repo", 38)

        then:
        thrown NoSuchElementException
        1 * client.builds("token someToken", "org/repo", 38) >> builds
        2 * builds.commits >> []
    }

    def "branchedRepoSlug should return branch prefixed with pull_request if it is a pull request"() {
        given:
        Builds builds = Mock(Builds)
        Build build = Mock(Build)
        Commit commit = Mock(Commit)

        when:
        String branchedRepoSlug = service.branchedRepoSlug("my/slug", 21, commit)

        then:
        branchedRepoSlug == "my/slug/pull_request_master"
        1 * client.builds("token someToken", "my/slug", 21) >> builds
        2 * builds.builds >> [build]
        1 * build.pullRequest >> true
        1 * commit.getBranchNameWithTagHandling() >> "master"

    }

    def "branchedRepoSlug should fallback to input repoSlug if hystrix kicks in"() {
        given:
        Commit commit = Mock(Commit)

        when:
        String branchedRepoSlug = service.branchedRepoSlug("my/slug", 21, commit)

        then:
        branchedRepoSlug == "my/slug"
    }

    @Unroll
    def "calculate pagination correctly"() {
        expect:
        service.calculatePagination(buildsToTrack) == pages

        where:
        buildsToTrack || pages
        75            || 3
        79            || 4
        2             || 1
        15            || 1
        26            || 2
        25            || 1
    }

    @Unroll
    def "resolve travis build type from input repo slug"() {
        expect:
        service.travisBuildTypeFromRepoSlug(inputRepoSlug) == expectedTravisBuildType

        where:
        inputRepoSlug                     || expectedTravisBuildType
        "my-org/repo"                     || TravisBuildType.unknown
        "my-org/repo/branch"              || TravisBuildType.branch
        "my-org/repo/branch/with/slashes" || TravisBuildType.branch
        "my-org/repo/pull_request_master" || TravisBuildType.pull_request
        "m/r/some_pull_request_in_name"   || TravisBuildType.branch
        "my-org/repo/tags"                || TravisBuildType.tag
    }

    def "set buildMessage from buildProperties"() {
        given:
        def response = new TriggerResponse()
        response.setRemainingRequests(1)
        def request = new Request()
        request.setId(1337)
        def repository = new V3Repository()
        repository.setId(42)
        request.setRepository(repository)
        response.setRequest(request)

        when:
        int buildNumber = service.triggerBuildWithParameters("my/slug/branch", ["travis.buildMessage": "My build message"])

        then:
        1 * client.triggerBuild("token someToken", "my/slug", { RepoRequest repoRequest ->
            assert repoRequest.branch == "branch"
            assert repoRequest.config.env == null
            assert repoRequest.message == "Triggered from Spinnaker: My build message"
            return repoRequest
        }) >> response
        1 * travisCache.setQueuedJob("travis-ci", 42, 1337) >> 1

        buildNumber == 1
    }
}
