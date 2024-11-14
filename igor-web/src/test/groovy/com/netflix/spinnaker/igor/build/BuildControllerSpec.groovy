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

package com.netflix.spinnaker.igor.build

import com.netflix.spinnaker.igor.PendingOperationsCache
import com.netflix.spinnaker.igor.build.model.GenericBuild
import com.netflix.spinnaker.igor.build.model.UpdatedBuild
import com.netflix.spinnaker.igor.config.JenkinsConfig
import com.netflix.spinnaker.igor.jenkins.client.model.Build
import com.netflix.spinnaker.igor.jenkins.client.model.BuildArtifact
import com.netflix.spinnaker.igor.jenkins.client.model.JobConfig
import com.netflix.spinnaker.igor.jenkins.client.model.ParameterDefinition
import com.netflix.spinnaker.igor.jenkins.client.model.QueuedJob
import com.netflix.spinnaker.igor.jenkins.service.JenkinsService
import com.netflix.spinnaker.igor.model.BuildServiceProvider
import com.netflix.spinnaker.igor.service.BuildOperations
import com.netflix.spinnaker.igor.service.BuildServices
import com.netflix.spinnaker.igor.travis.service.TravisService
import com.netflix.spinnaker.kork.web.exceptions.ExceptionMessageDecorator
import com.netflix.spinnaker.kork.web.exceptions.GenericExceptionHandlers
import com.netflix.spinnaker.kork.web.exceptions.NotFoundException
import okhttp3.mockwebserver.MockWebServer
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import retrofit.client.Header
import retrofit.client.Response
import spock.lang.Shared
import spock.lang.Specification

import static com.netflix.spinnaker.igor.build.BuildController.InvalidJobParameterException
import static com.netflix.spinnaker.igor.build.BuildController.validateJobParameters
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * Tests for BuildController
 */
@SuppressWarnings(['DuplicateNumberLiteral', 'PropertyName'])
class BuildControllerSpec extends Specification {

  MockMvc mockMvc
  BuildServices buildServices
  BuildCache cache
  JenkinsService jenkinsService
  BuildOperations service
  TravisService travisService
  PendingOperationsCache pendingOperationService
  ExceptionMessageDecorator exceptionMessageDecorator

  @Shared
  MockWebServer server

  def SERVICE = 'SERVICE'
  def JENKINS_SERVICE = 'JENKINS_SERVICE'
  def TRAVIS_SERVICE = 'TRAVIS_SERVICE'
  def HTTP_201 = 201
  def BUILD_NUMBER = 123
  def BUILD_ID = 654321
  def QUEUED_JOB_NUMBER = 123456
  def JOB_NAME = "job/name/can/have/slashes"
  def SIMPLE_JOB_NAME = "simpleJobName"
  def PENDING_JOB_NAME = "pendingjob"
  def FILE_NAME = "test.yml"

  GenericBuild genericBuild

  void cleanup() {
    server.shutdown()
  }

  void setup() {
    exceptionMessageDecorator = Mock(ExceptionMessageDecorator)
    service = Mock(BuildOperations)
    jenkinsService = Mock(JenkinsService)
    jenkinsService.getBuildServiceProvider() >> BuildServiceProvider.JENKINS
    travisService = Mock(TravisService)
    travisService.getBuildServiceProvider() >> BuildServiceProvider.TRAVIS
    buildServices = new BuildServices()
    buildServices.addServices([
      (SERVICE)        : service,
      (JENKINS_SERVICE): jenkinsService,
      (TRAVIS_SERVICE) : travisService,
    ])
    genericBuild = new GenericBuild()
    genericBuild.number = BUILD_NUMBER
    genericBuild.id = BUILD_ID

    cache = Mock(BuildCache)
    server = new MockWebServer()
    pendingOperationService = Mock(PendingOperationsCache)
    pendingOperationService.getAndSetOperationStatus(_, _, _) >> {
      return new PendingOperationsCache.OperationState()
    }

    mockMvc = MockMvcBuilders
      .standaloneSetup(new BuildController(buildServices, pendingOperationService, Optional.empty(), Optional.empty(), Optional.empty()))
      .setControllerAdvice(new GenericExceptionHandlers(exceptionMessageDecorator))
      .build()
  }

  void 'get the status of a build'() {
    given:
    1 * service.getGenericBuild(JOB_NAME, BUILD_NUMBER) >> new GenericBuild(building: false, number: BUILD_NUMBER)

    when:
    MockHttpServletResponse response = mockMvc.perform(get("/builds/status/${BUILD_NUMBER}/${SERVICE}/${JOB_NAME}")
      .accept(MediaType.APPLICATION_JSON)).andReturn().response

    then:
    response.contentAsString == "{\"building\":false,\"number\":${BUILD_NUMBER}}"
  }

  void 'get the status of a build with job name as query parameter'() {
    given:
    1 * service.getGenericBuild(JOB_NAME, BUILD_NUMBER) >> new GenericBuild(building: false, number: BUILD_NUMBER)

    when:
    MockHttpServletResponse response = mockMvc.perform(get("/builds/status/${BUILD_NUMBER}/${SERVICE}")
      .param("job", JOB_NAME)
      .accept(MediaType.APPLICATION_JSON)).andReturn().response

    then:
    response.contentAsString == "{\"building\":false,\"number\":${BUILD_NUMBER}}"
  }

  void 'get an item from the queue'() {
    given:
    1 * jenkinsService.queuedBuild(_, QUEUED_JOB_NUMBER) >> new QueuedJob(executable: [number: QUEUED_JOB_NUMBER])

    when:
    MockHttpServletResponse response = mockMvc.perform(get("/builds/queue/${JENKINS_SERVICE}/${QUEUED_JOB_NUMBER}")
      .accept(MediaType.APPLICATION_JSON)).andReturn().response

    then:
    response.contentAsString == "{\"executable\":{\"number\":${QUEUED_JOB_NUMBER}},\"number\":${QUEUED_JOB_NUMBER}}"
  }

  void 'deserialize a queue response'() {
    given:
    def objectMapper = JenkinsConfig.getObjectMapper()

    when:
    def queuedJob = objectMapper.readValue("<hudson><executable><number>${QUEUED_JOB_NUMBER}</number></executable></hudson>", QueuedJob.class)

    then:
    queuedJob.number == QUEUED_JOB_NUMBER
  }

  void 'deserialize a more realistic queue response'() {
    given:
    def objectMapper = JenkinsConfig.getObjectMapper()

    when:
    def queuedJob = objectMapper.readValue(
      "<buildableItem _class=\"hudson.model.Queue\$BuildableItem\">\n" +
        "    <action _class=\"hudson.model.ParametersAction\">\n" +
        "        <parameter _class=\"hudson.model.StringParameterValue\">\n" +
        "            <name>CLUSTER_NAME</name>\n" +
        "            <value>aspera-ingestqc</value>\n" +
        "        </parameter>\n" +
        "    </action>\n" +
        "    <action _class=\"hudson.model.CauseAction\">\n" +
        "        <cause _class=\"hudson.model.Cause\$UserIdCause\">\n" +
        "            <shortDescription>Started by user buildtest</shortDescription>\n" +
        "            <userId>buildtest</userId>\n" +
        "            <userName>buildtest</userName>\n" +
        "        </cause>\n" +
        "    </action>\n" +
        "    <blocked>false</blocked>\n" +
        "    <buildable>true</buildable>\n" +
        "    <id>${QUEUED_JOB_NUMBER}</id>" +
        "    <stuck>true</stuck>" +
        "    <pending>false</pending>" +
        "</buildableItem>", QueuedJob.class)

    then:
    queuedJob.number == null
  }

  void 'get a list of builds for a job'() {
    given:
    1 * jenkinsService.getBuilds(JOB_NAME) >> [new Build(number: 111), new Build(number: 222)]

    when:
    MockHttpServletResponse response = mockMvc.perform(get("/builds/all/${JENKINS_SERVICE}/${JOB_NAME}")
      .accept(MediaType.APPLICATION_JSON)).andReturn().response

    then:
    response.contentAsString == "[{\"building\":false,\"number\":111},{\"building\":false,\"number\":222}]"
  }

  void 'get properties of a build with a bad master'() {
    given:
    jenkinsService.getBuild(JOB_NAME, BUILD_NUMBER) >> new Build(
      number: BUILD_NUMBER, artifacts: [new BuildArtifact(fileName: "badFile.yml", relativePath: FILE_NAME)])
    1 * exceptionMessageDecorator.decorate(_, _)

    expect:
    mockMvc.perform(
      get("/builds/properties/${BUILD_NUMBER}/${FILE_NAME}/badMaster/${JOB_NAME}")
        .accept(MediaType.APPLICATION_JSON))
      .andExpect(status().isNotFound())
      .andReturn().response
  }

  void 'get properties of a build with a bad filename'() {
    given:
    1 * jenkinsService.getGenericBuild(JOB_NAME, BUILD_NUMBER) >> genericBuild
    1 * exceptionMessageDecorator.decorate(_, _)
    1 * jenkinsService.getBuildProperties(JOB_NAME, genericBuild, FILE_NAME) >> {
      throw new NotFoundException()
    }

    expect:
    mockMvc.perform(
      get("/builds/properties/${BUILD_NUMBER}/${FILE_NAME}/${JENKINS_SERVICE}/${JOB_NAME}")
        .accept(MediaType.APPLICATION_JSON))
      .andExpect(status().isNotFound())
      .andReturn().response
  }

  void 'get properties of a build with job Name in query parameters' () {
    given:
    1 * jenkinsService.getGenericBuild(JOB_NAME, BUILD_NUMBER) >> genericBuild
    1 * jenkinsService.getBuildProperties(JOB_NAME, genericBuild, FILE_NAME) >> ['foo': 'bar']

    when:
    MockHttpServletResponse response = mockMvc.perform(
      get("/builds/properties/${BUILD_NUMBER}/${FILE_NAME}/${JENKINS_SERVICE}")
        .param("job", JOB_NAME)
        .accept(MediaType.APPLICATION_JSON)).andReturn().response

    then:
    response.contentAsString == "{\"foo\":\"bar\"}"
  }

  void 'get properties of a travis build'() {
    given:
    1 * travisService.getGenericBuild(JOB_NAME, BUILD_NUMBER) >> genericBuild
    1 * travisService.getBuildProperties(JOB_NAME, genericBuild, _) >> ['foo': 'bar']

    when:
    MockHttpServletResponse response = mockMvc.perform(
      get("/builds/properties/${BUILD_NUMBER}/${FILE_NAME}/${TRAVIS_SERVICE}/${JOB_NAME}")
        .accept(MediaType.APPLICATION_JSON)).andReturn().response

    then:
    response.contentAsString == "{\"foo\":\"bar\"}"
  }

  void 'trigger a build without parameters'() {
    given:
    1 * jenkinsService.getJobConfig(JOB_NAME) >> new JobConfig(buildable: true)
    1 * jenkinsService.build(JOB_NAME) >> new Response("http://test.com", HTTP_201, "", [new Header("Location", "foo/${BUILD_NUMBER}")], null)

    when:
    MockHttpServletResponse response = mockMvc.perform(put("/masters/${JENKINS_SERVICE}/jobs/${JOB_NAME}")
      .accept(MediaType.APPLICATION_JSON)).andReturn().response

    then:
    response.contentAsString == BUILD_NUMBER.toString()

    1 * pendingOperationService.getAndSetOperationStatus(_, _, _) >> { new PendingOperationsCache.OperationState() }
    1 * pendingOperationService.setOperationStatus(_, PendingOperationsCache.OperationStatus.COMPLETED, BUILD_NUMBER.toString())
  }

  void 'trigger a build with parameters to a job with parameters'() {
    given:
    1 * jenkinsService.getJobConfig(JOB_NAME) >> new JobConfig(buildable: true, parameterDefinitionList: [new ParameterDefinition(defaultParameterValue: [name: "name", value: null], description: "description")])
    1 * jenkinsService.buildWithParameters(JOB_NAME, [name: "myName"]) >> new Response("http://test.com", HTTP_201, "", [new Header("Location", "foo/${BUILD_NUMBER}")], null)

    when:
    MockHttpServletResponse response = mockMvc.perform(put("/masters/${JENKINS_SERVICE}/jobs/${JOB_NAME}")
      .contentType(MediaType.APPLICATION_JSON).param("name", "myName")).andReturn().response

    then:
    response.contentAsString == BUILD_NUMBER.toString()
  }

  void 'trigger a build without parameters to a job with parameters with default values'() {
    given:
    1 * jenkinsService.getJobConfig(JOB_NAME) >> new JobConfig(buildable: true, parameterDefinitionList: [new ParameterDefinition(defaultParameterValue: [name: "name", value: "value"], description: "description")])
    1 * jenkinsService.buildWithParameters(JOB_NAME, ['startedBy': "igor"]) >> new Response("http://test.com", HTTP_201, "", [new Header("Location", "foo/${BUILD_NUMBER}")], null)

    when:
    MockHttpServletResponse response = mockMvc.perform(put("/masters/${JENKINS_SERVICE}/jobs/${JOB_NAME}", "")
      .accept(MediaType.APPLICATION_JSON)).andReturn().response

    then:
    response.contentAsString == BUILD_NUMBER.toString()
  }

  void 'trigger a build with parameters to a job without parameters'() {
    given:
    1 * jenkinsService.getJobConfig(JOB_NAME) >> new JobConfig(buildable: true)

    when:
    MockHttpServletResponse response = mockMvc.perform(put("/masters/${JENKINS_SERVICE}/jobs/${JOB_NAME}")
      .contentType(MediaType.APPLICATION_JSON).param("foo", "bar")).andReturn().response

    then:
    response.status == HttpStatus.INTERNAL_SERVER_ERROR.value()
  }

  void 'trigger a build with an invalid choice'() {
    given:
    JobConfig config = new JobConfig(buildable: true)
    config.parameterDefinitionList = [
      new ParameterDefinition(type: "ChoiceParameterDefinition", name: "foo", choices: ["bar", "baz"])
    ]
    1 * jenkinsService.getJobConfig(JOB_NAME) >> config
    1 * exceptionMessageDecorator.decorate(_, _) >> "`bat` is not a valid choice for `foo`. Valid choices are: bar, baz"

    when:
    MockHttpServletResponse response = mockMvc.perform(put("/masters/${JENKINS_SERVICE}/jobs/${JOB_NAME}")
      .contentType(MediaType.APPLICATION_JSON).param("foo", "bat")).andReturn().response

    then:

    response.status == HttpStatus.BAD_REQUEST.value()
    response.errorMessage == "`bat` is not a valid choice for `foo`. Valid choices are: bar, baz"
  }

  void 'trigger a disabled build'() {
    given:
    JobConfig config = new JobConfig()
    1 * jenkinsService.getJobConfig(JOB_NAME) >> config
    1 * exceptionMessageDecorator.decorate(_, _) >> "Job '${JOB_NAME}' is not buildable. It may be disabled."

    when:
    MockHttpServletResponse response = mockMvc.perform(put("/masters/${JENKINS_SERVICE}/jobs/${JOB_NAME}")
      .contentType(MediaType.APPLICATION_JSON).param("foo", "bat")).andReturn().response

    then:
    response.status == HttpStatus.BAD_REQUEST.value()
    response.errorMessage == "Job '${JOB_NAME}' is not buildable. It may be disabled."
  }

  void 'validation successful for null list of choices'() {
    given:
    Map<String, String> requestParams = ["hey": "you"]
    ParameterDefinition parameterDefinition = new ParameterDefinition()
    parameterDefinition.choices = null
    parameterDefinition.type = "ChoiceParameterDefinition"
    parameterDefinition.name = "hey"
    JobConfig jobConfig = new JobConfig()
    jobConfig.parameterDefinitionList = [parameterDefinition]

    when:
    validateJobParameters(jobConfig, requestParams)

    then:
    noExceptionThrown()
  }

  void 'validation failed for option not in list of choices'() {
    given:
    Map<String, String> requestParams = ["hey": "you"]
    ParameterDefinition parameterDefinition = new ParameterDefinition()
    parameterDefinition.choices = ["why", "not"]
    parameterDefinition.type = "ChoiceParameterDefinition"
    parameterDefinition.name = "hey"
    JobConfig jobConfig = new JobConfig()
    jobConfig.parameterDefinitionList = [parameterDefinition]

    when:
    validateJobParameters(jobConfig, requestParams)

    then:
    thrown(InvalidJobParameterException)
  }


  void "doesn't trigger a build when previous request is still in progress"() {
    given:
    pendingOperationService = Stub(PendingOperationsCache)
    pendingOperationService.getAndSetOperationStatus("${JENKINS_SERVICE}:${PENDING_JOB_NAME}:NO_EXECUTION_ID:foo=bat", _, _) >> {
      return new PendingOperationsCache.OperationState(PendingOperationsCache.OperationStatus.PENDING)
    }

    mockMvc = MockMvcBuilders
      .standaloneSetup(new BuildController(buildServices, pendingOperationService, Optional.empty(), Optional.empty(), Optional.empty()))
      .setControllerAdvice(new GenericExceptionHandlers())
      .build()

    when:
    MockHttpServletResponse response = mockMvc.perform(put("/masters/${JENKINS_SERVICE}/jobs/${PENDING_JOB_NAME}")
      .contentType(MediaType.APPLICATION_JSON).param("foo", "bat")).andReturn().response

    then:
    response.status == HttpStatus.ACCEPTED.value()
  }

  void "resets the cache once the build status has been retrieved"() {
    given:
    pendingOperationService = Mock(PendingOperationsCache)
    pendingOperationService.getAndSetOperationStatus("${JENKINS_SERVICE}:${JOB_NAME}:NO_EXECUTION_ID:foo=bat", _, _) >> {
      PendingOperationsCache.OperationState state = new PendingOperationsCache.OperationState()
      state.load(PendingOperationsCache.OperationStatus.COMPLETED.toString() + ":" + BUILD_NUMBER)
      return state
    }

    mockMvc = MockMvcBuilders
      .standaloneSetup(new BuildController(buildServices, pendingOperationService, Optional.empty(), Optional.empty(), Optional.empty()))
      .setControllerAdvice(new GenericExceptionHandlers())
      .build()

    when:
    MockHttpServletResponse response = mockMvc.perform(put("/masters/${JENKINS_SERVICE}/jobs/${JOB_NAME}")
      .contentType(MediaType.APPLICATION_JSON).param("foo", "bat")).andReturn().response

    then:
    response.status == HttpStatus.OK.value()
    response.contentAsString == BUILD_NUMBER.toString()
    1 * pendingOperationService.clear("${JENKINS_SERVICE}:${JOB_NAME}:NO_EXECUTION_ID:foo=bat")
  }

  void "updates a jenkins build description"() {
    when:
    MockHttpServletResponse response = mockMvc.perform(
      patch("/masters/${JENKINS_SERVICE}/jobs/${jobName}/update/${BUILD_NUMBER}")
        .contentType(MediaType.APPLICATION_JSON)
        .content("""
{
  "description": "this is my new description"
}
""")
    ).andReturn().response

    then:
    1 * jenkinsService.updateBuild(jobName, BUILD_NUMBER, new UpdatedBuild("this is my new description"))
    0 * _
    response.status == 200

    where:
    jobName << [
      "complex/job/name/with/slashes",
      "simpleJobName"
    ]
  }

  void "stop a jenkins job with simple name"() {

    when:
    MockHttpServletResponse response = mockMvc.perform(
      put("/masters/{master}/jobs/{jobNamePath}/stop/{queue_build}/{build_number}", JENKINS_SERVICE, SIMPLE_JOB_NAME, QUEUED_JOB_NUMBER, BUILD_NUMBER)
    ).andReturn().response

    then:
    1 * jenkinsService.stopRunningBuild(SIMPLE_JOB_NAME, BUILD_NUMBER)
    response.status == 200
    response.contentAsString == 'true'

  }

  void "stop a jenkins job with name containing slashes"() {

    when:
    MockHttpServletResponse response = mockMvc.perform(
      put("/masters/{master}/jobs/stop/{queue_build}/{build_number}", JENKINS_SERVICE, QUEUED_JOB_NUMBER, BUILD_NUMBER)
      .param('jobName', JOB_NAME)
    ).andReturn().response

    then:
    1 * jenkinsService.stopRunningBuild(JOB_NAME, BUILD_NUMBER)
    response.status == 200
    response.contentAsString == 'true'

  }

}
