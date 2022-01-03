/*
 * Copyright 2014 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.orca.controllers

import com.netflix.spinnaker.fiat.model.UserPermission
import com.netflix.spinnaker.fiat.model.resources.Account
import com.netflix.spinnaker.fiat.model.resources.Role
import com.netflix.spinnaker.fiat.shared.FiatService
import com.netflix.spinnaker.fiat.shared.FiatStatus
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionType
import com.netflix.spinnaker.orca.api.pipeline.models.PipelineExecution
import com.netflix.spinnaker.orca.api.preconfigured.jobs.TitusPreconfiguredJobProperties
import com.netflix.spinnaker.orca.clouddriver.service.JobService
import com.netflix.spinnaker.orca.exceptions.PipelineTemplateValidationException
import com.netflix.spinnaker.orca.front50.Front50Service

import javax.servlet.http.HttpServletResponse
import com.netflix.spinnaker.kork.common.Header
import com.netflix.spinnaker.orca.igor.BuildService
import com.netflix.spinnaker.orca.jackson.OrcaObjectMapper
import com.netflix.spinnaker.orca.pipeline.ExecutionLauncher
import com.netflix.spinnaker.orca.pipeline.model.PipelineExecutionImpl
import com.netflix.spinnaker.orca.pipeline.model.JenkinsTrigger
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionNotFoundException
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import com.netflix.spinnaker.orca.pipeline.util.ArtifactUtils
import com.netflix.spinnaker.orca.pipeline.util.ContextParameterProcessor
import com.netflix.spinnaker.orca.pipelinetemplate.PipelineTemplateService
import com.netflix.spinnaker.orca.webhook.config.WebhookProperties
import com.netflix.spinnaker.orca.webhook.service.WebhookService
import groovy.json.JsonSlurper
import org.slf4j.MDC
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import rx.Observable
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll
import static com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus.CANCELED
import static com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus.SUCCEEDED
import static com.netflix.spinnaker.orca.api.pipeline.models.ExecutionType.PIPELINE
import static com.netflix.spinnaker.orca.test.model.ExecutionBuilder.pipeline
import static java.lang.String.format
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post

class OperationsControllerSpec extends Specification {

  void setup() {
    MDC.clear()
  }

  def executionLauncher = Mock(ExecutionLauncher)
  def buildService = Stub(BuildService)
  def mapper = OrcaObjectMapper.newInstance()
  def executionRepository = Mock(ExecutionRepository)
  def pipelineTemplateService = Mock(PipelineTemplateService)
  def webhookService = Mock(WebhookService)
  def artifactUtils = Mock(ArtifactUtils)
  def fiatService = Mock(FiatService)
  def fiatStatus = Mock(FiatStatus) {
    _ * isEnabled() >> { return false }
  }
  def front50Service = Mock(Front50Service)
  def jobService = Mock(JobService)

  @Subject
    controller = new OperationsController(
      objectMapper: mapper,
      buildService: buildService,
      executionRepository: executionRepository,
      pipelineTemplateService: pipelineTemplateService,
      executionLauncher: executionLauncher,
      contextParameterProcessor: new ContextParameterProcessor(),
      webhookService: webhookService,
      artifactUtils: artifactUtils,
      fiatService: fiatService,
      fiatStatus: fiatStatus,
      front50Service: front50Service,
      jobService: jobService
    )

  @Unroll
  void '#endpoint accepts #contentType'() {
    given:
    def mockMvc = MockMvcBuilders.standaloneSetup(controller).build()

    when:
    def resp = mockMvc.perform(
      post(endpoint).contentType(contentType).content('{}')
    ).andReturn().response

    then:
    1 * executionLauncher.start(PIPELINE, _) >> pipeline

    and:
    resp.status == 200
    slurp(resp.contentAsString).ref == "/pipelines/$pipeline.id"

    where:
    contentType << [MediaType.APPLICATION_JSON, MediaType.valueOf('application/context+json')]
    endpoint = "/orchestrate"
    pipeline = PipelineExecutionImpl.newPipeline("1")
  }

  private Map slurp(String json) {
    new JsonSlurper().parseText(json)
  }

  def "uses trigger details from pipeline if present"() {
    given:
    PipelineExecution startedPipeline = null
    executionLauncher.start(*_) >> { ExecutionType type, Map config ->
      startedPipeline = mapper.convertValue(config, PipelineExecutionImpl)
    }
    buildService.getBuild(buildNumber, master, job) >> buildInfo

    when:
    controller.orchestrate(requestedPipeline, Mock(HttpServletResponse))

    then:
    with(startedPipeline) {
      trigger.type == requestedPipeline.trigger.type
      trigger instanceof JenkinsTrigger
      def asJenkins = trigger as JenkinsTrigger
      asJenkins.master == master
      asJenkins.job == job
      asJenkins.buildNumber == buildNumber
      asJenkins.buildInfo.result == buildInfo.result
    }

    where:
    master = "master"
    job = "job"
    buildNumber = 1337
    requestedPipeline = [
      application: "someapp",
      trigger    : [
        type       : "jenkins",
        master     : master,
        job        : job,
        buildNumber: buildNumber
      ]
    ]
    buildInfo = [name: job, number: buildNumber, result: "SUCCESS", url: "http://jenkins"]
  }

  def "should not get pipeline execution details from trigger if provided"() {
    given:
    PipelineExecutionImpl startedPipeline = null
    executionLauncher.start(*_) >> { ExecutionType type, Map config ->
      startedPipeline = mapper.convertValue(config, PipelineExecutionImpl)
    }

    when:
    controller.orchestrate(requestedPipeline, Mock(HttpServletResponse))

    then:
    0 * executionRepository._

    where:
    requestedPipeline = [
      application: "someapp",
      trigger    : [
        type            : "manual",
        parentPipelineId: "12345",
        parentExecution : [name: "abc", type: PIPELINE, id: "1", application: "application"]
      ]
    ]
  }

  def "should get pipeline execution details from trigger if not provided"() {
    given:
    PipelineExecutionImpl startedPipeline = null
    executionLauncher.start(*_) >> { ExecutionType type, Map config ->
      startedPipeline = mapper.convertValue(config, PipelineExecutionImpl)
    }
    PipelineExecutionImpl parentPipeline = pipeline {
      name = "pipeline from orca"
      status = CANCELED
      id = "12345"
      application = "someapp"
    }

    when:
    controller.orchestrate(requestedPipeline, Mock(HttpServletResponse))

    then:
    1 * executionRepository.retrieve(PIPELINE, "12345") >> parentPipeline

    and:
    with(startedPipeline.trigger) {
      parentExecution != null
      parentExecution.id == "12345"
    }

    where:
    requestedPipeline = [
      application: "someapp",
      trigger    : [
        type            : "pipeline",
        parentPipelineId: "12345"
      ]
    ]
  }

  def "should get pipeline execution context from a previous execution if not provided and attribute plan is truthy"() {
    given:
    PipelineExecutionImpl startedPipeline = null
    executionLauncher.start(*_) >> { ExecutionType type, Map config ->
      startedPipeline = mapper.convertValue(config, PipelineExecutionImpl)
    }

    def previousExecution = pipeline {
      name = "Last executed pipeline"
      status = SUCCEEDED
      id = "12345"
      application = "covfefe"
    }

    when:
    def orchestration = controller.orchestrate(requestedPipeline, Mock(HttpServletResponse))

    then:
    1 * pipelineTemplateService.retrievePipelineOrNewestExecution("12345", _) >> previousExecution
    orchestration.trigger.type == "manual"

    where:
    requestedPipeline = [
      id         : "54321",
      plan       : true,
      type       : "templatedPipeline",
      executionId: "12345"
    ]
  }

  def "trigger user takes precedence over query parameter"() {
    given:
    PipelineExecutionImpl startedPipeline = null
    executionLauncher.start(*_) >> { ExecutionType type, Map config ->
      startedPipeline = mapper.convertValue(config, PipelineExecutionImpl)
    }
    buildService.getBuild(buildNumber, master, job) >> buildInfo

    if (queryUser) {
      MDC.put(Header.USER.header, queryUser)
    }
    when:
    controller.orchestrate(requestedPipeline, Mock(HttpServletResponse))

    then:
    with(startedPipeline) {
      trigger.type == requestedPipeline.trigger.type
      trigger instanceof JenkinsTrigger
      trigger.master == master
      trigger.job == job
      trigger.buildNumber == buildNumber
      trigger.buildInfo.result == buildInfo.result
      trigger.user == expectedUser
    }

    where:
    triggerUser   | queryUser   | expectedUser
    null          | "fromQuery" | "fromQuery"
    null          | null        | "[anonymous]"
    "fromTrigger" | "fromQuery" | "fromTrigger"

    master = "master"
    job = "job"
    buildNumber = 1337
    requestedPipeline = [
      trigger: [
        type       : "jenkins",
        master     : master,
        job        : job,
        buildNumber: buildNumber,
        user       : triggerUser
      ]
    ]
    buildInfo = [name: job, number: buildNumber, result: "SUCCESS", url: "http://jenkins"]

  }

  def "gets properties file from igor if specified in pipeline"() {
    given:
    PipelineExecutionImpl startedPipeline = null
    executionLauncher.start(*_) >> { ExecutionType type, Map config ->
      startedPipeline = mapper.convertValue(config, PipelineExecutionImpl)
    }
    buildService.getBuild(buildNumber, master, job) >> [name: job, number: buildNumber, result: "SUCCESS", url: "http://jenkins"]
    buildService.getPropertyFile(buildNumber, propertyFile, master, job) >> propertyFileContent

    when:
    controller.orchestrate(requestedPipeline, Mock(HttpServletResponse))

    then:
    with(startedPipeline) {
      trigger.propertyFile == propertyFile
      trigger.properties == propertyFileContent
    }

    where:
    master = "qs-master"
    job = "qs-job"
    buildNumber = 1337
    propertyFile = "foo.properties"
    requestedPipeline = [
      trigger: [
        type        : "jenkins",
        master      : master,
        job         : job,
        buildNumber : buildNumber,
        propertyFile: propertyFile
      ]
    ]
    propertyFileContent = [foo: "bar"]
  }

  def "context parameters are processed before pipeline is started"() {
    given:
    PipelineExecutionImpl startedPipeline = null
    executionLauncher.start(*_) >> { ExecutionType type, Map config ->
      startedPipeline = mapper.convertValue(config, PipelineExecutionImpl)
    }

    Map requestedPipeline = [
      trigger: [
        type      : "jenkins",
        master    : "master",
        job       : "jon",
        number    : 1,
        properties: [
          key1        : 'val1',
          key2        : 'val2',
          replaceValue: ['val3']
        ]
      ],
      id     : '${trigger.properties.key1}',
      name   : '${trigger.properties.key2}'
    ]

    when:
    controller.orchestrate(requestedPipeline, Mock(HttpServletResponse))

    then:
    startedPipeline.id == 'val1'
    startedPipeline.name == 'val2'
  }

  def "processes pipeline parameters"() {
    given:
    PipelineExecutionImpl startedPipeline = null
    executionLauncher.start(*_) >> { ExecutionType type, Map config ->
      startedPipeline = mapper.convertValue(config, PipelineExecutionImpl)
    }

    Map requestedPipeline = [
      trigger: [
        type      : "manual",
        parameters: [
          key1: 'value1',
          key2: 'value2'
        ]
      ],
      id     : '${parameters.key1}',
      name   : '${parameters.key2}'
    ]

    when:
    controller.orchestrate(requestedPipeline, Mock(HttpServletResponse))

    then:
    startedPipeline.id == 'value1'
    startedPipeline.name == 'value2'
  }

  def "fills out pipeline parameters with defaults"() {
    given:
    PipelineExecutionImpl startedPipeline = null
    executionLauncher.start(*_) >> { ExecutionType type, Map config ->
      startedPipeline = mapper.convertValue(config, PipelineExecutionImpl)
    }

    Map requestedPipeline = [
      trigger         : [
        parameters: [
          otherParam: 'from pipeline'
        ]
      ],
      parameterConfig : [
        [
          name       : "region",
          default    : "us-west-1",
          description: "region for the deployment"
        ],
        [
          name       : "key1",
          default    : "value1",
          description: "region for the deployment"
        ],
        [
          name       : "otherParam",
          default    : "defaultOther",
          description: "region for the deployment"
        ]
      ],
      pipelineConfigId: '${parameters.otherParam}',
      id              : '${parameters.key1}',
      name            : '${parameters.region}'
    ]

    when:
    controller.orchestrate(requestedPipeline, Mock(HttpServletResponse))

    then:
    startedPipeline.id == 'value1'
    startedPipeline.name == 'us-west-1'
    startedPipeline.pipelineConfigId == 'from pipeline'
  }

  def "an empty string does not get overridden with default values"() {
    given:
    PipelineExecutionImpl startedPipeline = null
    executionLauncher.start(*_) >> { ExecutionType type, Map config ->
      startedPipeline = mapper.convertValue(config, PipelineExecutionImpl)
    }

    Map requestedPipeline = [
      trigger         : [
        parameters: [
          otherParam: ''
        ]
      ],
      parameterConfig : [
        [
          name       : "otherParam",
          default    : "defaultOther",
          description: "region for the deployment"
        ]
      ],
      pipelineConfigId: '${parameters.otherParam}'
    ]

    when:
    controller.orchestrate(requestedPipeline, Mock(HttpServletResponse))

    then:
    startedPipeline.pipelineConfigId == ''
  }

  def "provided trigger can evaluate spel"() {
    given:
    PipelineExecutionImpl startedPipeline = null
    executionLauncher.start(*_) >> { ExecutionType type, Map config ->
      startedPipeline = mapper.convertValue(config, PipelineExecutionImpl)
    }
    executionRepository.retrievePipelinesForPipelineConfigId(*_) >> Observable.empty()
    ArtifactUtils realArtifactUtils = new ArtifactUtils(mapper, executionRepository, new ContextParameterProcessor())

    // can't use @subject, since we need to test the behavior of otherwise mocked-out 'artifactUtils'
    def tempController = new OperationsController(
        objectMapper: mapper,
        buildService: buildService,
        executionRepository: executionRepository,
        pipelineTemplateService: pipelineTemplateService,
        executionLauncher: executionLauncher,
        contextParameterProcessor: new ContextParameterProcessor(),
        webhookService: webhookService,
        artifactUtils: realArtifactUtils,
    )

    def reference = 'gs://bucket'
    def name = 'name'
    def id = 'id'

    Map requestedPipeline = [
        pipelineConfigId: "some-id",
        id: '12345',

        expectedArtifacts: [[
            id: id,

            matchArtifact: [
                name: "not $name".toString(),
                reference: "not $reference".toString()
            ],

            defaultArtifact: [
                reference: '${parameters.reference}'
            ],

            useDefaultArtifact: true
        ]],

        trigger         : [
            parameters: [
                reference: reference
            ],

            expectedArtifactIds: [ id ]
        ],
    ]

    when:
    tempController.orchestrate(requestedPipeline, Mock(HttpServletResponse))

    then:
    startedPipeline.getTrigger().resolvedExpectedArtifacts[0].boundArtifact.reference == reference
  }

  def "should not start pipeline when truthy plan pipeline attribute is present"() {
    given:
    def pipelineConfig = [
      plan: true
    ]

    when:
    controller.orchestrate(pipelineConfig, Mock(HttpServletResponse))

    then:
    0 * executionLauncher.start(*_)
  }

  def "should throw validation exception when templated pipeline contains errors"() {
    given:
    def pipelineConfig = [
      plan       : true,
      type       : "templatedPipeline",
      executionId: "12345",
      errors     : [
        'things broke': 'because of the way it is'
      ]
    ]
    def response = Mock(HttpServletResponse)

    when:
    controller.orchestrate(pipelineConfig, response)

    then:
    thrown(PipelineTemplateValidationException)
    1 * pipelineTemplateService.retrievePipelineOrNewestExecution("12345", null) >> {
      throw new ExecutionNotFoundException("Not found")
    }
    0 * executionLauncher.start(*_)
  }

  def "should log and re-throw validation error for non-templated pipeline"() {
    given:
    def pipelineConfig = [
      type: PIPELINE,
      errors: [
        'things broke': 'because of the way it is'
      ]
    ]
    def response = Mock(HttpServletResponse)
    1 * executionLauncher.fail(*_) >> { ExecutionType type, Map config, Throwable t ->
      mapper.convertValue(config, PipelineExecutionImpl)
    }

    when:
    controller.orchestrate(pipelineConfig, response)

    then:
    thrown(PipelineTemplateValidationException)
    0 * executionLauncher.start(*_)
  }

  def "should log and re-throw missing artifact error"() {
    given:
    def pipelineConfig = [
      type: PIPELINE
    ]
    def response = Mock(HttpServletResponse)
    artifactUtils.resolveArtifacts(*_) >> { Map pipeline ->
      throw new IllegalStateException(format("Unmatched expected artifact could not be resolved."))
    }
    1 * executionLauncher.fail(*_) >> { ExecutionType type, Map config, Throwable t ->
      mapper.convertValue(config, PipelineExecutionImpl)
    }

    when:
    controller.orchestrate(pipelineConfig, response)

    then:
    thrown(IllegalStateException)
    0 * executionLauncher.start(*_)
  }

  def "should return empty list if webhook stage is not enabled"() {
    given:
    controller.webhookService = null

    when:
    def preconfiguredWebhooks = controller.preconfiguredWebhooks()

    then:
    0 * webhookService.preconfiguredWebhooks
    preconfiguredWebhooks == []
  }

  def "should call webhookService and return correct information"() {
    given:
    def preconfiguredProperties = ["url", "customHeaders", "method", "payload", "failFastStatusCodes", "waitForCompletion", "statusUrlResolution",
                                   "statusUrlJsonPath", "statusJsonPath", "progressJsonPath", "successStatuses", "canceledStatuses", "terminalStatuses",
                                   "signalCancellation", "cancelEndpoint", "cancelMethod", "cancelPayload"]

    when:
    def preconfiguredWebhooks = controller.preconfiguredWebhooks()

    then:
    1 * webhookService.preconfiguredWebhooks >> [
      createPreconfiguredWebhook("Webhook #1", "Description #1", "webhook_1", null),
      createPreconfiguredWebhook("Webhook #2", "Description #2", "webhook_2", null)
    ]
    preconfiguredWebhooks == [
      [label: "Webhook #1", description: "Description #1", type: "webhook_1", waitForCompletion: true, preconfiguredProperties: preconfiguredProperties, noUserConfigurableFields: true, parameters: null, parameterData: null],
      [label: "Webhook #2", description: "Description #2", type: "webhook_2", waitForCompletion: true, preconfiguredProperties: preconfiguredProperties, noUserConfigurableFields: true, parameters: null, parameterData: null]
    ]
  }

  def "should not return protected preconfigured webhooks if user don't have the role"() {
    given:
    def preconfiguredProperties = ["url", "customHeaders", "method", "payload", "failFastStatusCodes", "waitForCompletion", "statusUrlResolution",
                                   "statusUrlJsonPath", "statusJsonPath", "progressJsonPath", "successStatuses", "canceledStatuses", "terminalStatuses",
                                   "signalCancellation", "cancelEndpoint", "cancelMethod", "cancelPayload"]
    executionLauncher.start(*_) >> { ExecutionType type, Map config ->
      mapper.convertValue(config, PipelineExecutionImpl)
    }

    UserPermission userPermission = new UserPermission()
    userPermission.addResource(new Role("test"))

    def account = new Account().setName("account")
    def role = new Role().setName("role")
    def permission = new UserPermission().setId("foo").setAccounts([account] as Set).setRoles([role] as Set)

    fiatService.getUserPermission(*_) >> permission.getView()

    when:
    def preconfiguredWebhooks = controller.preconfiguredWebhooks()

    then:
    1 * controller.fiatStatus.isEnabled() >> { return true }
    1 * webhookService.preconfiguredWebhooks >> [
      createPreconfiguredWebhook("Webhook #1", "Description #1", "webhook_1", ["READ": [], "WRITE": []]),
      createPreconfiguredWebhook("Webhook #2", "Description #2", "webhook_2", ["READ": ["some-role"], "WRITE": ["some-role"]])
    ]
    preconfiguredWebhooks == [
      [label: "Webhook #1", description: "Description #1", type: "webhook_1", waitForCompletion: true, preconfiguredProperties: preconfiguredProperties, noUserConfigurableFields: true, parameters: null, parameterData: null]
    ]
  }

  def "should return protected preconfigured webhooks if user have the role"() {
    given:
    def preconfiguredProperties = ["url", "customHeaders", "method", "payload", "failFastStatusCodes", "waitForCompletion", "statusUrlResolution",
                                   "statusUrlJsonPath", "statusJsonPath", "progressJsonPath", "successStatuses", "canceledStatuses", "terminalStatuses",
                                   "signalCancellation", "cancelEndpoint", "cancelMethod", "cancelPayload"]
    executionLauncher.start(*_) >> { ExecutionType type, Map config ->
      mapper.convertValue(config, PipelineExecutionImpl)
    }


    UserPermission userPermission = new UserPermission()
    userPermission.addResource(new Role("some-role"))

    def account = new Account().setName("account")
    def role = new Role().setName("some-role")
    def permission = new UserPermission().setId("foo").setAccounts([account] as Set).setRoles([role] as Set)

    fiatService.getUserPermission(*_) >> permission.getView()

    when:
    def preconfiguredWebhooks = controller.preconfiguredWebhooks()

    then:
    1 * controller.fiatStatus.isEnabled() >> { return true }
    1 * webhookService.preconfiguredWebhooks >> [
      createPreconfiguredWebhook("Webhook #1", "Description #1", "webhook_1", ["READ": [], "WRITE": []]),
      createPreconfiguredWebhook("Webhook #2", "Description #2", "webhook_2", ["READ": ["some-role"], "WRITE": ["some-role"]])
    ]
    preconfiguredWebhooks == [
      [label: "Webhook #1", description: "Description #1", type: "webhook_1", waitForCompletion: true, preconfiguredProperties: preconfiguredProperties, noUserConfigurableFields: true, parameters: null, parameterData: null],
      [label: "Webhook #2", description: "Description #2", type: "webhook_2", waitForCompletion: true, preconfiguredProperties: preconfiguredProperties, noUserConfigurableFields: true, parameters: null, parameterData: null]
    ]
  }

  def "should return unrestricted preconfigured webhooks if fiat is unavailable"() {
    given:
    UserPermission userPermission = new UserPermission()
    userPermission.addResource(new Role("test"))

    when:
    def preconfiguredWebhooks = controller.preconfiguredWebhooks()

    then:
    1 * fiatService.getUserPermission(*_) >> {
      throw new IllegalStateException("Sorry, Fiat is unavailable")
    }
    1 * controller.fiatStatus.isEnabled() >> { return true }
    1 * webhookService.preconfiguredWebhooks >> [
        createPreconfiguredWebhook("Webhook #1", "Description #1", "webhook_1", [:]),
        createPreconfiguredWebhook("Webhook #2", "Description #2", "webhook_2", ["READ": ["some-role"], "WRITE": ["some-role"]]),
        createPreconfiguredWebhook("Webhook #3", "Description #3", "webhook_3", ["READ": ["anonymous"], "WRITE": ["anonymous"]])
    ]
    preconfiguredWebhooks*.label == ["Webhook #1", "Webhook #3"]
  }

  def "should start pipeline by config id with provided trigger"() {
    given:
    front50Service.getPipeline("1234") >> [id: '1234', stages: []]

    Map trigger = [
      type      : "manual",
      parameters: [
        key1: 'value1',
        key2: 'value2'
      ]
    ]

    PipelineExecutionImpl startedPipeline = null
    executionLauncher.start(*_) >> { ExecutionType type, Map config ->
      startedPipeline = mapper.convertValue(config, PipelineExecutionImpl)
    }

    when:
    controller.orchestratePipelineConfig('1234', trigger)

    then:
    startedPipeline.id == '1234'
    startedPipeline.trigger.type == 'manual'
    startedPipeline.trigger.parameters.key1 == 'value1'
  }

  def "should return only jobs that are enabled"() {
    given:
    TitusPreconfiguredJobProperties jobProps1 = new TitusPreconfiguredJobProperties(enabled: true, label: 'job1')
    TitusPreconfiguredJobProperties jobProps2 = new TitusPreconfiguredJobProperties(enabled: false, label: 'job2')
    TitusPreconfiguredJobProperties jobProps3 = new TitusPreconfiguredJobProperties(label: 'job3')

    when:
    def preconfiguredWebhooks = controller.preconfiguredJob()

    then:
    1* jobService.getPreconfiguredStages() >> [jobProps1, jobProps2, jobProps3]
    preconfiguredWebhooks.size() == 2
    preconfiguredWebhooks.find { it.label == 'job1' }
    preconfiguredWebhooks.find { it.label == 'job3' }
  }

  static WebhookProperties.PreconfiguredWebhook createPreconfiguredWebhook(
    def label, def description, def type, def permissions) {
    def customHeaders = new HttpHeaders()
    customHeaders.put("header", ["value1"])
    return new WebhookProperties.PreconfiguredWebhook(
      label: label, description: description, type: type,
      url: "a", customHeaders: customHeaders, method: HttpMethod.POST, payload: "b",
      failFastStatusCodes: [500, 501], waitForCompletion: true, statusUrlResolution: WebhookProperties.StatusUrlResolution.webhookResponse,
      statusUrlJsonPath: "c", statusJsonPath: "d", progressJsonPath: "e", successStatuses: "f", canceledStatuses: "g", terminalStatuses: "h", parameters: null, parameterData: null,
      permissions: permissions, signalCancellation: true, cancelEndpoint: "i", cancelMethod: HttpMethod.POST, cancelPayload: "j"
    )
  }
}
