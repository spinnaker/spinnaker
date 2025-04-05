package com.netflix.spinnaker.echo.services

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import com.github.tomakehurst.wiremock.client.WireMock
import com.google.common.collect.ImmutableList
import com.netflix.spinnaker.config.DefaultServiceEndpoint
import com.netflix.spinnaker.config.okhttp3.OkHttpClientProvider
import com.netflix.spinnaker.config.okhttp3.InsecureOkHttpClientBuilderProvider
import com.netflix.spinnaker.echo.model.Trigger
import com.netflix.spinnaker.kork.retrofit.ErrorHandlingExecutorCallAdapterFactory
import com.netflix.spinnaker.kork.retrofit.Retrofit2SyncCall
import com.netflix.spinnaker.okhttp.Retrofit2EncodeCorrectionInterceptor
import okhttp3.OkHttpClient
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import retrofit2.Retrofit
import retrofit2.converter.jackson.JacksonConverterFactory
import spock.lang.Ignore;
import spock.lang.Specification
import spock.util.concurrent.BlockingVariable

@SpringBootTest(classes = [OkHttpClientProvider, InsecureOkHttpClientBuilderProvider, OkHttpClient, Retrofit2EncodeCorrectionInterceptor],
  webEnvironment = SpringBootTest.WebEnvironment.NONE)
class Front50ServiceSpec extends Specification {
  WireMockServer wireMockServer
  Front50Service front50Service

  @Autowired
  OkHttpClientProvider clientProvider

  BlockingVariable<List<Map<String,Object>>> pipelineResponse

  ObjectMapper objectMapper = new ObjectMapper()

  def setup() {
    pipelineResponse = new BlockingVariable<List<Map<String,Object>>>(5)
    wireMockServer = new WireMockServer(WireMockConfiguration.options().dynamicPort());
    wireMockServer.start();
  }

  def cleanup(){
    wireMockServer.stop()
  }

  def "parses pipelines"() {
    given:
    front50Service = front50Service(wireMockServer.baseUrl())
    wireMockServer.stubFor(WireMock.get(WireMock.urlEqualTo("/pipelines?restricted=false"))
      .willReturn(WireMock.aResponse()
        .withBody(objectMapper.writeValueAsString(pipelineWithNoTriggers))
        .withStatus(200)))

    when:
    def pipelines = Retrofit2SyncCall.execute(front50Service.getPipelines())

    then:
    !pipelines.empty
  }

  def "handles pipelines with empty triggers array"() {
    given:
    front50Service = front50Service(wireMockServer.baseUrl())
    wireMockServer.stubFor(WireMock.get(WireMock.urlEqualTo("/pipelines?restricted=false"))
      .willReturn(WireMock.aResponse()
        .withBody(objectMapper.writeValueAsString(pipelineWithNoTriggers))
        .withStatus(200)))

    when:
    def pipelines = Retrofit2SyncCall.execute(front50Service.getPipelines())

    then:
    def pipeline = pipelines.first()
    pipeline.triggers.empty
  }

  def "handles pipelines with actual triggers"() {
    given:
    front50Service = front50Service(wireMockServer.baseUrl())
    wireMockServer.stubFor(WireMock.get(WireMock.urlEqualTo("/pipelines?restricted=false"))
      .willReturn(WireMock.aResponse()
        .withBody(objectMapper.writeValueAsString(pipelineWithJenkinsTrigger))
        .withStatus(200)))

    when:
    def pipelines = Retrofit2SyncCall.execute(front50Service.getPipelines())

    then:
    def pipeline = pipelines.find { it.application == "rush" && it.name == "bob the sinner" }
    pipeline.triggers.size() == 1
    with(pipeline.triggers[0]) {
      enabled
      type == "jenkins"
      master == "spinnaker"
      job == "Dummy_test_job"
      propertyFile == "deb.properties"
    }
  }

  def "handles parallel pipelines"() {
    given:
    front50Service = front50Service(wireMockServer.baseUrl())
    wireMockServer.stubFor(WireMock.get(WireMock.urlEqualTo("/pipelines?restricted=false"))
      .willReturn(WireMock.aResponse()
        .withBody(objectMapper.writeValueAsString(parallelPipeline))
        .withStatus(200)))

    when:
    def pipelines = Retrofit2SyncCall.execute(front50Service.getPipelines())

    then:
    pipelines.first().parallel
  }

  @Ignore
  def "list properties are immutable"() {
    given:
    def pipelines = front50Service.getPipelines()
    def pipeline = pipelines.find { it.application == "kato" }

    expect:
    pipeline.triggers instanceof ImmutableList

    when:
    pipeline.triggers << Trigger.builder().enabled(false).type('jenkins').master('foo').job('bar').propertyFile('baz').build()

    then:
    thrown UnsupportedOperationException
  }

  private Front50Service front50Service(String baseUrl){
    new Retrofit.Builder()
      .baseUrl(baseUrl)
      .client(clientProvider.getClient(new DefaultServiceEndpoint("front50", baseUrl, false)))
      .addCallAdapterFactory(ErrorHandlingExecutorCallAdapterFactory.getInstance())
      .addConverterFactory(JacksonConverterFactory.create())
      .build()
      .create(Front50Service.class);
  }

  def pipelineWithNoTriggers = [
    [
      name       : "healthCheck",
      stages     : [
        [
          type    : "wait",
          name    : "Wait",
          waitTime: 10
        ]
      ],
      triggers   : [],
      application: "spindemo",
      index      : 0,
      id         : "58b700a0-ed12-11e4-a8b3-f5bd0633341b"
    ]
  ]

  def pipelineWithJenkinsTrigger = [
    [
      "name"       : "bob the sinner",
      "stages"     : [
        [
          "isNew"            : true,
          "type"             : "findAmi",
          "name"             : "Find AMI",
          "master"           : "spinnaker",
          "job"              : "Dummy_test_job_2",
          "propertyFile"     : "deb.properties",
          "parameters"       : [
            "apiDeb"  : "\${trigger.properties.apiDeb}",
            "newValue": "somthing else"
          ],
          "selectionStrategy": "NEWEST",
          "onlyEnabled"      : true,
          "cluster"          : "rush-main",
          "account"          : "prod",
          "regions"          : ["us-west-1"]
        ]
      ],
      "triggers"   : [
        [
          "enabled"     : true,
          "type"        : "jenkins",
          "master"      : "spinnaker",
          "job"         : "Dummy_test_job",
          "propertyFile": "deb.properties"
        ]
      ],
      "application": "rush",
      "appConfig"  : null,
      "index"      : 1
    ]
  ]

  def parallelPipeline = [
    [
      name        : "DZ parallel pipeline",
      stages      : [
        [
          type                : "bake",
          name                : "Bake",
          regions             : [
            "us-east-1",
            "us-west-2",
            "eu-west-1"
          ],
          user                : "dzapata@netflix.com",
          baseOs              : "trusty",
          baseLabel           : "release",
          vmType              : "pv",
          storeType           : "ebs",
          package             : "api",
          refId               : "1",
          requisiteStageRefIds: []
        ],
        [
          type                : "quickPatch",
          name                : "Quick Patch ASG",
          application         : "api",
          healthProviders     : [
            "Discovery"
          ],
          account             : "prod",
          credentials         : "prod",
          region              : "us-east-1",
          clusterName         : "api-ci-dzapata",
          package             : "api",
          baseOs              : "ubuntu",
          refId               : "2",
          requisiteStageRefIds: []
        ],
        [
          requisiteStageRefIds: [
            "2"
          ],
          refId               : "3",
          type                : "jenkins",
          name                : "Smoke Test",
          master              : "edge",
          job                 : "Edge-DZ-Smoke-Test",
          parameters          : []
        ],
        [
          requisiteStageRefIds: [
            "3",
            "1"
          ],
          refId               : "4",
          type                : "deploy",
          name                : "Deploy",
          clusters            : [
            [
              application           : "api",
              strategy              : "redblack",
              stack                 : "sandbox",
              freeFormDetails       : "dzapata",
              cooldown              : 10,
              healthCheckGracePeriod: 600,
              healthCheckType       : "EC2",
              terminationPolicies   : [
                "Default"
              ],
              loadBalancers         : [],
              capacity              : [
                min    : 1,
                max    : 1,
                desired: 1
              ],
              availabilityZones     : [
                "us-east-1": [
                  "us-east-1c",
                  "us-east-1d",
                  "us-east-1e"
                ]
              ],
              suspendedProcesses    : [],
              instanceType          : "m2.4xlarge",
              iamRole               : "BaseIAMRole",
              keyPair               : "nf-prod-keypair-a",
              instanceMonitoring    : false,
              ebsOptimized          : false,
              securityGroups        : [
                "sg-31cd0758",
                "sg-42c0132b",
                "sg-ae9a5ec7",
                "sg-d8e330b1"
              ],
              maxRemainingAsgs      : 2,
              provider              : "aws",
              account               : "prod"
            ]
          ]
        ]
      ],
      triggers    : [
        [
          enabled: true,
          type   : "jenkins",
          master : "edge",
          job    : "EDGE-DZ-Branch-Build"
        ]
      ],
      application : "api",
      index       : 0,
      id          : "ed5ed000-f412-11e4-a8b3-f5bd0633341b",
      stageCounter: 4,
      parallel    : true
    ]
  ]
}
