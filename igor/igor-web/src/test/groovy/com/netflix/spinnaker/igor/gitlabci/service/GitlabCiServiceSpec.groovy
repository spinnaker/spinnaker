/*
 * Copyright 2017 Netflix, Inc.
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
package com.netflix.spinnaker.igor.gitlabci.service

import com.netflix.spinnaker.fiat.model.resources.Permissions
import com.netflix.spinnaker.igor.config.GitlabCiProperties.GitlabCiHost
import com.netflix.spinnaker.igor.gitlabci.client.GitlabCiClient
import com.netflix.spinnaker.igor.gitlabci.client.model.Job
import com.netflix.spinnaker.igor.gitlabci.client.model.Pipeline
import com.netflix.spinnaker.igor.gitlabci.client.model.Project
import com.netflix.spinnaker.igor.helpers.TestUtils
import com.netflix.spinnaker.igor.jenkins.client.model.Build
import com.netflix.spinnaker.kork.retrofit.exceptions.SpinnakerHttpException
import okhttp3.MediaType
import okhttp3.ResponseBody
import retrofit2.mock.Calls
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

class GitlabCiServiceSpec extends Specification {
    @Shared
    GitlabCiClient client

    @Shared
    GitlabCiService service

    @Shared
    GitlabCiHost hostConfig

    void setup() {
        client = Mock(GitlabCiClient)
        hostConfig = new GitlabCiHost()
        hostConfig.setTriggerToken("test-trigger-token")
        service = new GitlabCiService(client, "gitlab", hostConfig, Permissions.EMPTY)
    }

    def "verify invalid config of http page size"() {
      when:
      hostConfig.setDefaultHttpPageLength(999999999)

      then:
      thrown IllegalArgumentException
    }

    def "verify project pagination"() {
        given:
        client.getProjects(_, _, 1, _) >> Calls.response([new Project(pathWithNamespace: "project1", buildsAccessLevel: "enabled")])
        client.getProjects(_, _, 2, _) >> Calls.response([new Project(pathWithNamespace: "project2", buildsAccessLevel: "enabled")])
        client.getProjects(_, _, 3, _) >> Calls.response([])

        when:
        def projects = service.getProjects()

        then:
        projects.size() == 2
    }

    def "verify retrieving of pipelines"() {
        given:
        final int PROJECT_ID = 13
        final int PAGE_SIZE = 2
        final int PIPELINE_1_ID = 3
        final int PIPELINE_2_ID = 7
        Project project = new Project(id: PROJECT_ID)
        Pipeline ps1 = new Pipeline(id: PIPELINE_1_ID)
        Pipeline ps2 = new Pipeline(id: PIPELINE_2_ID)
        client.getPipelineSummaries(String.valueOf(PROJECT_ID), PAGE_SIZE) >> Calls.response([ps1, ps2])
        client.getPipeline(String.valueOf(PROJECT_ID), PIPELINE_1_ID) >> Calls.response(new Pipeline(id: PIPELINE_1_ID))
        client.getPipeline(String.valueOf(PROJECT_ID), PIPELINE_1_ID) >> Calls.response(new Pipeline(id: PIPELINE_1_ID))

        when:
        List<Pipeline> pipelines = service.getPipelines(project, PAGE_SIZE)

        then:
        pipelines.size() == 2
        pipelines[0].id == PIPELINE_1_ID
        pipelines[1].id == PIPELINE_2_ID
    }

    def "verify retrieving of empty pipelines"() {
        when:
        List<Pipeline> pipelines = service.getPipelines(new Project(), 10)

        then:
        pipelines.isEmpty()

        1 * client.getPipelineSummaries(_, _) >> Calls.response([])
    }

  @Unroll
  def "getBuildProperties retries on transient failure"() {
    given:
    final String PROJECT_ID = "13"
    final int PIPELINE_ID = 3
    final int JOB_ID = 1

    def build = new Build(number: PIPELINE_ID, duration: 0L)
    def testPipeline = new Pipeline(id: PIPELINE_ID)
    def testJob = new Job(pipeline: testPipeline, id: JOB_ID)
    def badGatewayError = TestUtils.makeSpinnakerHttpException("http://test.net", 502, ResponseBody.create("{}", MediaType.parse("application/json")))
    def jobLog = ResponseBody.create("SPINNAKER_PROPERTY_TEST=1",MediaType.parse("application/text"))

    when:
    def properties = service.getBuildProperties(PROJECT_ID, build.genericBuild(PROJECT_ID), "test.properties")

    then:
    2 * client.getPipeline(PROJECT_ID, PIPELINE_ID) >> { throw badGatewayError } >> Calls.response(testPipeline)
    1 * client.getJobs(PROJECT_ID, PIPELINE_ID) >> Calls.response([testJob])
    1 * client.getBridges(PROJECT_ID, PIPELINE_ID) >> Calls.response([])
    1 * client.getJobLog(PROJECT_ID, JOB_ID) >> Calls.response(jobLog)

    properties == [test: "1"]
  }

  @Unroll
  def "getBuildProperties retries on 404"() {
    given:
    final String PROJECT_ID = "13"
    final int PIPELINE_ID = 3
    final int JOB_ID = 1

    def build = new Build(number: PIPELINE_ID, duration: 0L)
    def testPipeline = new Pipeline(id: PIPELINE_ID)
    def testJob = new Job(pipeline: testPipeline, id: JOB_ID)
    def notFoundError = TestUtils.makeSpinnakerHttpException("http://test.net", 404, ResponseBody.create("not found", MediaType.parse("application/text")))
    def jobLog = ResponseBody.create("SPINNAKER_PROPERTY_TEST=1",MediaType.parse("application/text"))

    when:
    def properties = service.getBuildProperties(PROJECT_ID, build.genericBuild(PROJECT_ID), "test.properties")

    then:
    2 * client.getPipeline(PROJECT_ID, PIPELINE_ID) >> { throw notFoundError } >> Calls.response(testPipeline)
    1 * client.getJobs(PROJECT_ID, PIPELINE_ID) >> Calls.response([testJob])
    1 * client.getBridges(PROJECT_ID, PIPELINE_ID) >> Calls.response([])
    1 * client.getJobLog(PROJECT_ID, JOB_ID) >> Calls.response(jobLog)

    properties == [test: "1"]
  }

  @Unroll
  def "getBuildProperties does not retry on 400"() {
    given:
    final String PROJECT_ID = "13"
    final int PIPELINE_ID = 3
    final int JOB_ID = 1

    def build = new Build(number: PIPELINE_ID, duration: 0L)
    def badRequestError = TestUtils.makeSpinnakerHttpException("http://test.net", 400, ResponseBody.create("bad request", MediaType.parse("application/text")))

    when:
    service.getBuildProperties(PROJECT_ID, build.genericBuild(PROJECT_ID), "test.properties")

    then:
    1 * client.getPipeline(PROJECT_ID, PIPELINE_ID) >> { throw badRequestError }
    0 * client.getJobs(PROJECT_ID, PIPELINE_ID)
    0 * client.getBridges(PROJECT_ID, PIPELINE_ID)
    0 * client.getJobLog(PROJECT_ID, JOB_ID)

    thrown SpinnakerHttpException
  }

  def "getBuildProperties extracts properties from GitLab-prefixed log lines"() {
    given: "a job log in GitLab's real format: each line prefixed with a timestamp and stream code"
    final String PROJECT_ID = "13"
    final int PIPELINE_ID = 3
    final int JOB_ID = 1

    def build = new Build(number: PIPELINE_ID, duration: 0L)
    def testPipeline = new Pipeline(id: PIPELINE_ID)
    def testJob = new Job(pipeline: testPipeline, id: JOB_ID)
    def gitlabLog =
        "2026-10-27T16:13:21.006671Z 00O \u001B[0KRunning with gitlab-runner 19.1.1\u001B[0;m\n" +
        "2026-10-27T16:13:22.166283Z 01O SPINNAKER_PROPERTY_BUILD_VERSION=1.2.3\n" +
        "2026-10-27T16:13:22.166286Z 01O SPINNAKER_PROPERTY_BUILT_BY=eeigjon\n" +
        "2026-10-27T16:13:23.000000Z 00O Job succeeded\n"
    def jobLog = ResponseBody.create(gitlabLog, MediaType.parse("application/text"))

    when:
    def properties = service.getBuildProperties(PROJECT_ID, build.genericBuild(PROJECT_ID), "gitlab.properties")

    then:
    1 * client.getPipeline(PROJECT_ID, PIPELINE_ID) >> Calls.response(testPipeline)
    1 * client.getJobs(PROJECT_ID, PIPELINE_ID) >> Calls.response([testJob])
    1 * client.getBridges(PROJECT_ID, PIPELINE_ID) >> Calls.response([])
    1 * client.getJobLog(PROJECT_ID, JOB_ID) >> Calls.response(jobLog)

    and: "the timestamp/stream prefix is stripped so both properties are extracted (keys lowercased by the parser), and runner noise is ignored"
    properties == [build_version: "1.2.3", built_by: "eeigjon"]
  }

  def "triggerBuildWithParameters triggers a pipeline and returns its ID"() {
    given:
    final String PROJECT_ID = "9729"
    final String REF = "main"
    final long PIPELINE_ID = 12345

    Pipeline triggeredPipeline = new Pipeline(id: PIPELINE_ID)

    when:
    long result = service.triggerBuildWithParameters(PROJECT_ID, [ref: REF])

    then:
    1 * client.triggerPipeline(PROJECT_ID, "test-trigger-token", REF, [:]) >> Calls.response(triggeredPipeline)
    result == PIPELINE_ID
  }

  def "triggerBuildWithParameters uses 'main' as default ref"() {
    given:
    final String PROJECT_ID = "9729"
    final long PIPELINE_ID = 99

    Pipeline triggeredPipeline = new Pipeline(id: PIPELINE_ID)

    when:
    long result = service.triggerBuildWithParameters(PROJECT_ID, [:])

    then:
    1 * client.triggerPipeline(PROJECT_ID, "test-trigger-token", "main", [:]) >> Calls.response(triggeredPipeline)
    result == PIPELINE_ID
  }

  def "triggerBuildWithParameters forwards parameters as GitLab pipeline variables"() {
    given:
    final String PROJECT_ID = "9729"
    final long PIPELINE_ID = 777

    Pipeline triggeredPipeline = new Pipeline(id: PIPELINE_ID)

    when: "a stage is triggered with a ref and extra parameters"
    long result = service.triggerBuildWithParameters(
        PROJECT_ID, [ref: "develop", ENVIRONMENT: "prod", VERSION: "1.2.3"])

    then: "ref is passed as the ref and the remaining params become variables[KEY] entries"
    1 * client.triggerPipeline(
        PROJECT_ID,
        "test-trigger-token",
        "develop",
        ["variables[ENVIRONMENT]": "prod", "variables[VERSION]": "1.2.3"]) >> Calls.response(triggeredPipeline)
    result == PIPELINE_ID
  }

  def "triggerBuildWithParameters skips null-valued parameters instead of failing"() {
    given:
    final String PROJECT_ID = "9729"
    final long PIPELINE_ID = 888

    Pipeline triggeredPipeline = new Pipeline(id: PIPELINE_ID)

    when: "a parameter has a null value"
    def params = new HashMap<String, String>()
    params.put("ref", "main")
    params.put("ENVIRONMENT", "prod")
    params.put("UNSET", null)
    long result = service.triggerBuildWithParameters(PROJECT_ID, params)

    then: "the null-valued parameter is skipped and the call still succeeds"
    1 * client.triggerPipeline(
        PROJECT_ID,
        "test-trigger-token",
        "main",
        ["variables[ENVIRONMENT]": "prod"]) >> Calls.response(triggeredPipeline)
    result == PIPELINE_ID
  }

  def "triggerBuildWithParameters falls back to 'main' when ref is blank"() {
    given:
    final String PROJECT_ID = "9729"
    final long PIPELINE_ID = 555

    Pipeline triggeredPipeline = new Pipeline(id: PIPELINE_ID)

    when: "ref is present but blank"
    long result = service.triggerBuildWithParameters(PROJECT_ID, [ref: "   "])

    then: "it triggers on 'main' rather than the blank value"
    1 * client.triggerPipeline(PROJECT_ID, "test-trigger-token", "main", [:]) >> Calls.response(triggeredPipeline)
    result == PIPELINE_ID
  }

  def "triggerBuildWithParameters throws when triggerToken is not configured"() {
    given:
    def hostConfigNoToken = new GitlabCiHost()
    def serviceNoToken = new GitlabCiService(client, "gitlab-no-token", hostConfigNoToken, Permissions.EMPTY)

    when:
    serviceNoToken.triggerBuildWithParameters("9729", [ref: "main"])

    then:
    def ex = thrown(IllegalStateException)
    ex.message.contains("triggerToken is not configured")
  }

  def "cancelPipeline calls the client and returns the pipeline"() {
    given:
    final String PROJECT_ID = "9729"
    final long PIPELINE_ID = 12345

    Pipeline cancelledPipeline = new Pipeline(id: PIPELINE_ID)

    when:
    Pipeline result = service.cancelPipeline(PROJECT_ID, PIPELINE_ID)

    then:
    1 * client.cancelPipeline(PROJECT_ID, PIPELINE_ID) >> Calls.response(cancelledPipeline)
    result.id == PIPELINE_ID
  }

  def "stopRunningBuild delegates to cancelPipeline"() {
    given:
    final String PROJECT_ID = "9729"
    final long PIPELINE_ID = 12345

    Pipeline cancelledPipeline = new Pipeline(id: PIPELINE_ID)

    when:
    service.stopRunningBuild(PROJECT_ID, PIPELINE_ID)

    then:
    1 * client.cancelPipeline(PROJECT_ID, PIPELINE_ID) >> Calls.response(cancelledPipeline)
  }
}
