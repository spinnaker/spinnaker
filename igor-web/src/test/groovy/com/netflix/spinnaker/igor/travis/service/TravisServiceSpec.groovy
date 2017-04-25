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

import com.netflix.spinnaker.igor.build.model.GenericBuild
import com.netflix.spinnaker.igor.build.model.Result
import com.netflix.spinnaker.igor.travis.client.TravisClient
import com.netflix.spinnaker.igor.travis.client.model.AccessToken
import com.netflix.spinnaker.igor.travis.client.model.Build
import com.netflix.spinnaker.igor.travis.client.model.Builds
import com.netflix.spinnaker.igor.travis.client.model.Commit
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll


class TravisServiceSpec extends Specification{
    @Shared
    TravisClient client

    @Shared
    TravisService service

    void setup() {
        client = Mock(TravisClient)
        service = new TravisService('travis-ci', 'http://my.travis.ci', 'someToken', client, null, Collections.emptyList())

        AccessToken accessToken = new AccessToken()
        accessToken.accessToken = "someToken"
        service.accessToken = accessToken
    }

    def "getGenericBuild(build, repoSlug)" () {
        given:
        Build build = Mock(Build)

        when:
        GenericBuild genericBuild = service.getGenericBuild(build, "some/repo-slug")

        then:
        genericBuild.number == 1337
        genericBuild.result == Result.SUCCESS
        genericBuild.duration == 32
        genericBuild.timestamp == "1458051084000"

        1 * build.number >> 1337
        2 * build.state >> 'passed'
        1 * build.duration >> 32
        1 * build.finishedAt >> new Date()
        1 * build.timestamp() >> 1458051084000
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
        2 * builds.commits >> [commit]
    }

    def "getCommit(repoSlug, buildNumber) when no commit is found"() {
        given:
        Builds builds = Mock(Builds)

        when:
        service.getCommit("org/repo", 38)

        then:
        thrown NoSuchFieldException
        1 * client.builds("token someToken", "org/repo", 38) >> builds
        1 * builds.commits >> []
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
        1 * commit.branchNameWithTagHandling() >> "master"

    }

    def "branchedRepoSlug should fallback to input repoSlug if hystrix kicks in"() {
        given:
        Commit commit = Mock(Commit)

        when:
        String branchedRepoSlug = service.branchedRepoSlug("my/slug", 21, commit)

        then:
        branchedRepoSlug == "my/slug"
    }
}
