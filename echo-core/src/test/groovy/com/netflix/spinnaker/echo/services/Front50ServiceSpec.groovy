package com.netflix.spinnaker.echo.services

import groovy.json.JsonOutput
import com.netflix.spinnaker.echo.model.Trigger
import retrofit.Endpoints
import retrofit.RestAdapter
import spock.lang.Ignore
import spock.lang.Specification
import spock.lang.Subject
import com.google.common.collect.ImmutableList
import retrofit.client.Client
import retrofit.client.Header
import retrofit.client.Response
import retrofit.mime.TypedString

class Front50ServiceSpec extends Specification {
  def endpoint = "http://front50-prestaging.prod.netflix.net"
  def client = Stub(Client)
  @Subject front50 = new RestAdapter.Builder()
    .setEndpoint(Endpoints.newFixedEndpoint(endpoint))
    .setClient(client)
    .build()
    .create(Front50Service)

  def "parses pipelines"() {
    given:
    client.execute(_) >> response(pipelineWithNoTriggers)

    when:
    def pipelines = front50.getPipelines()

    then:
    !pipelines.empty
  }

  def "handles pipelines with empty triggers array"() {
    given:
    client.execute(_) >> response(pipelineWithNoTriggers)

    when:
    def pipelines = front50.getPipelines()

    then:
    def pipeline = pipelines.first()
    pipeline.triggers.empty
  }

  def "handles pipelines with actual triggers"() {
    given:
    client.execute(_) >> response(pipelineWithJenkinsTrigger)

    when:
    def pipelines = front50.getPipelines()

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
    client.execute(_) >> response(parallelPipeline)

    when:
    def pipelines = front50.getPipelines()

    then:
    pipelines.first().parallel
  }

  @Ignore
  def "list properties are immutable"() {
    given:
    def pipelines = front50.getPipelines()
    def pipeline = pipelines.find { it.application == "kato" }

    expect:
    pipeline.triggers instanceof ImmutableList

    when:
    pipeline.triggers << Trigger.builder().enabled(false).type('jenkins').master('foo').job('bar').propertyFile('baz').build()

    then:
    thrown UnsupportedOperationException
  }

  private Response response(Map... pipelines) {
    new Response("", 200, "OK", [new Header("Content-Type", "application/json")], new TypedString(JsonOutput.toJson(pipelines)))
  }

  def pipelineWithNoTriggers = [
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

  def pipelineWithJenkinsTrigger = [
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

  def parallelPipeline = [
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
}
