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
import com.netflix.spinnaker.igor.travis.client.model.v3.RepoRequest
import com.netflix.spinnaker.igor.travis.client.model.v3.Request
import com.netflix.spinnaker.igor.travis.client.model.v3.TravisBuildState
import com.netflix.spinnaker.igor.travis.client.model.v3.TravisBuildType
import com.netflix.spinnaker.igor.travis.client.model.v3.TriggerResponse
import com.netflix.spinnaker.igor.travis.client.model.v3.V3Build
import com.netflix.spinnaker.igor.travis.client.model.v3.V3Builds
import com.netflix.spinnaker.igor.travis.client.model.v3.V3Job
import com.netflix.spinnaker.igor.travis.client.model.v3.V3Jobs
import com.netflix.spinnaker.igor.travis.client.model.v3.V3Log
import com.netflix.spinnaker.igor.travis.client.model.v3.V3Repository
import com.netflix.spinnaker.igor.util.RetrofitUtils
import com.netflix.spinnaker.kork.retrofit.exceptions.SpinnakerHttpException
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry
import okhttp3.MediaType
import okhttp3.ResponseBody
import org.assertj.core.util.Lists
import retrofit2.Response
import retrofit2.converter.jackson.JacksonConverterFactory
import retrofit2.Retrofit
import retrofit2.mock.Calls
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

import java.time.Instant

import static java.util.Collections.emptyList

class TravisServiceSpec extends Specification {
    @Shared
    TravisClient client

    TravisService service

    @Shared
    TravisCache travisCache

    @Shared
    Optional<ArtifactDecorator> artifactDecorator

    private static int TRAVIS_BUILD_RESULT_LIMIT = 10

    void setup() {
        client = Mock()
        travisCache = Mock()
        artifactDecorator = Optional.of(new ArtifactDecorator([new DebDetailsDecorator(), new RpmDetailsDecorator()], null))
        service = new TravisService('travis-ci', 'http://my.travis.ci', 'someToken', TravisService.TRAVIS_JOB_RESULT_LIMIT, TRAVIS_BUILD_RESULT_LIMIT, emptyList(), client, travisCache, artifactDecorator, [], "travis.buildMessage", Permissions.EMPTY, false, CircuitBreakerRegistry.ofDefaults())

        AccessToken accessToken = new AccessToken()
        accessToken.accessToken = "someToken"
        service.accessToken = accessToken
    }

    def "getGenericBuild(build, repoSlug)"() {
        given:
        Build build = Mock(Build)
        def v3log = new V3Log()
        def logPart = new V3Log.V3LogPart()
        logPart.content = "Done. Your build exited with 0."
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
        1 * client.jobLog(_, 42) >> Calls.response(v3log)
        2 * build.job_ids >> [42]
        2 * build.number >> 1337
        2 * build.state >> 'passed'
        1 * build.duration >> 32
        1 * build.finishedAt >> Instant.now()
        1 * build.getTimestamp() >> 1458051084000
    }

    @Unroll
    def "getLatestBuilds() with #numberOfJobs jobs should query Travis #expectedNumberOfPages time(s)"() {
        given:
        service = new TravisService('travis-ci', 'http://my.travis.ci', 'someToken', numberOfJobs, TRAVIS_BUILD_RESULT_LIMIT, emptyList(), client, travisCache, artifactDecorator, [], "travis.buildMessage", Permissions.EMPTY, true, CircuitBreakerRegistry.ofDefaults())
        AccessToken accessToken = new AccessToken()
        accessToken.accessToken = "someToken"
        service.accessToken = accessToken


        def listOfJobs = (1..numberOfJobs).collect { createJob(it) }
        def partitionedJobs = listOfJobs.collate(TravisService.TRAVIS_JOB_RESULT_LIMIT).collect { partition ->
            V3Jobs jobs = new V3Jobs()
            jobs.jobs = partition
            return jobs
        }

        when:
        def builds = service.getLatestBuilds()

        then:
        (1..expectedNumberOfPages).each { page ->
            1 * client.jobs(
                "token someToken",
                [TravisBuildState.passed, TravisBuildState.started, TravisBuildState.errored, TravisBuildState.failed, TravisBuildState.canceled].join(","),
                "job.build",
                service.getLimit(page, numberOfJobs),
                (page - 1) * TravisService.TRAVIS_JOB_RESULT_LIMIT) >> Calls.response(partitionedJobs[page - 1])
        }
        builds.size() == numberOfJobs

        where:
        numberOfJobs | expectedNumberOfPages
        100          | 1
        305          | 4
        99           | 1
        101          | 2
    }

    private static V3Job createJob(int id) {
        def build = new V3Build([
            id: id,
            state: TravisBuildState.passed,
            repository: new V3Repository([slug: "my/slug"])
        ])
        return new V3Job().with { v3job ->
            v3job.id = id
            v3job.build = build
            return v3job
        }
    }

    def "getLatestBuilds() with filteredRepositories should fetch builds only for those repos"() {
      given:
      List<String> filteredRepos = Lists.newArrayList("myorg/myrepo")
      service = new TravisService('travis-ci', 'http://my.travis.ci', 'someToken', 100, TRAVIS_BUILD_RESULT_LIMIT, filteredRepos, client, travisCache, artifactDecorator, [], "travis.buildMessage", Permissions.EMPTY, true, CircuitBreakerRegistry.ofDefaults())
      AccessToken accessToken = new AccessToken()
      accessToken.accessToken = "someToken"
      service.accessToken = accessToken

      def job = new V3Job().with { v3job ->
        v3job.id = 2
        return v3job
      }

      def build = new V3Build([
        id: 1,
        jobs: [job],
        state: TravisBuildState.passed,
        repository: new V3Repository([slug: "myorg/myrepo"])
      ])

      when:
      def builds = service.getLatestBuilds()

      then:
      1 * client.v3builds("token someToken", "myorg/myrepo", TRAVIS_BUILD_RESULT_LIMIT,
        null) >> Calls.response(new V3Builds([builds: [build]]))
      0 * client.jobs(*_)
      builds == [build]
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

    @Unroll
    def "calculate pagination correctly"() {
        expect:
        service.calculatePagination(numberOfJobs) == pages

        where:
        numberOfJobs || pages
        175          || 2
        279          || 3
        2            || 1
        15           || 1
        100          || 1
        101          || 2
        1001         || 11
    }

    @Unroll
    def "calculate limit correctly"() {
        expect:
        service.getLimit(page, numberOfJobs) == limit

        where:
        page | numberOfJobs || limit
        1    | 100          || 100
        1    | 200          || 100
        2    | 200          || 100
        1    | 150          || 100
        2    | 150          || 50
        1    | 99           || 99
        2    | 201          || 100
        3    | 201          || 1
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
        }) >> Calls.response(response)
        1 * travisCache.setQueuedJob("travis-ci", 42, 1337) >> 1

        buildNumber == 1
    }

    @Unroll
    def "use correct way of checking if logs are completed when legacyLogFetching is #legacyLogFetching and log_complete flag is #isLogCompleteFlag"() {
        given:
        def job = new V3Job().with { v3job ->
            v3job.id = 2
            return v3job
        }
        def build = new V3Build([
            id: 1,
            jobs: [job],
            state: TravisBuildState.passed,
            repository: new V3Repository([slug: "my/slug"])
        ])
        if (!legacyLogFetching) {
            build.logComplete = isLogCompleteFlag
        }

        def logLine = "log" + (isLogReallyComplete ? " Done. Your build exited with 0." : "")
        def v3log = new V3Log([
            logParts: [
                new V3Log.V3LogPart([
                    number: 0,
                    content: logLine,
                    isFinal: isLogReallyComplete
                ])],
            content: logLine
        ])
        service = new TravisService('travis-ci', 'http://my.travis.ci', 'someToken', 25, TRAVIS_BUILD_RESULT_LIMIT, emptyList(), client, travisCache, artifactDecorator, [], "travis.buildMessage", Permissions.EMPTY, legacyLogFetching, CircuitBreakerRegistry.ofDefaults())
        AccessToken accessToken = new AccessToken()
        accessToken.accessToken = "someToken"
        service.accessToken = accessToken

        when:
        def genericBuilds = service.getBuilds("my/slug/master")

        then:
        1 * client.v3builds("token someToken", "my/slug", "master", "push,api", TRAVIS_BUILD_RESULT_LIMIT,
            legacyLogFetching ? null : "build.log_complete") >> Calls.response(new V3Builds([builds: [build]]))
        (isLogCompleteFlag ? 0 : 1) * travisCache.getJobLog("travis-ci", 2) >> (isLogCached ? "log" : null)
        (!isLogCompleteFlag && !isLogCached ? 1 : 0) * client.jobLog("token someToken", 2) >> Calls.response(v3log)
        (!isLogCompleteFlag && !isLogCached && isLogReallyComplete ? 1 : 0) * travisCache.setJobLog("travis-ci", 2, logLine)
        genericBuilds.size() == expectedNumberOfBuilds

        where:
        legacyLogFetching | isLogCompleteFlag | isLogCached | isLogReallyComplete | expectedNumberOfBuilds
        false             | true              | true        | true                | 1
        false             | false             | true        | true                | 1
        false             | false             | false       | true                | 1
        false             | false             | false       | false               | 0
        true              | null              | true        | true                | 1
        true              | null              | true        | true                | 1
        true              | null              | false       | true                | 1
        true              | null              | false       | false               | 0
    }

    def "should ignore expired logs from the Travis API when fetching logs"() {
        given:
        def v3log = new V3Log([
            logParts: [
                new V3Log.V3LogPart([
                    number : 0,
                    content: "Done. Your build exited with 0.",
                    isFinal: true
                ])],
            content : "Done. Your build exited with 0."
        ])

        when:
        def ready = service.isLogReady([1, 2])

        then:
        2 * travisCache.getJobLog("travis-ci", _) >> null
        1 * client.jobLog(_, 1) >> Calls.response(v3log)
        1 * client.jobLog(_, 2) >> {
            throw makeSpinnakerHttpException();
        }
        !ready
    }
  SpinnakerHttpException makeSpinnakerHttpException(){
    String url = "https://travis-ci.com/api/job/2/log/";
    Response retrofit2Response =
      Response.error(
        403,
        ResponseBody.create("""{
                            "@type": "error",
                            "error_type": "log_expired",
                            "error_message": "We're sorry, but this data is not available anymore. Please check the repository settings in Travis CI."
                        }""", MediaType.parse("application/json")));

    Retrofit retrofit =
      new Retrofit.Builder()
        .baseUrl(RetrofitUtils.getBaseUrl(url))
        .addConverterFactory(JacksonConverterFactory.create())
        .build();

    new SpinnakerHttpException(retrofit2Response, retrofit);
  }
}
