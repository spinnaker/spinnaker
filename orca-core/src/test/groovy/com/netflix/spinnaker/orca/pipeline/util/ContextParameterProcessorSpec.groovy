/*
 * Copyright 2016 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.orca.pipeline.util

import com.netflix.spinnaker.kork.expressions.ExpressionEvaluationSummary
import com.netflix.spinnaker.kork.expressions.ExpressionTransform
import com.netflix.spinnaker.kork.expressions.SpelHelperFunctionException
import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.jackson.OrcaObjectMapper
import com.netflix.spinnaker.orca.pipeline.expressions.functions.UrlExpressionFunctionProvider
import com.netflix.spinnaker.orca.pipeline.model.*
import org.springframework.expression.spel.SpelEvaluationException
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

import static com.netflix.spinnaker.orca.ExecutionStatus.SUCCEEDED
import static com.netflix.spinnaker.orca.ExecutionStatus.TERMINAL
import static com.netflix.spinnaker.orca.test.model.ExecutionBuilder.pipeline
import static com.netflix.spinnaker.orca.test.model.ExecutionBuilder.stage

class ContextParameterProcessorSpec extends Specification {

  @Subject
  ContextParameterProcessor contextParameterProcessor = new ContextParameterProcessor()

  @Unroll
  def "should #processAttributes"() {
    given:
    def source = ['test': sourceValue]
    def context = ['testArray': ['good', ['arrayVal': 'bad'], [['one': 'two']]], replaceMe: 'newValue', 'h1': [h1: 'h1Val'], hierarchy: [h2: 'hierarchyValue', h3: [h4: 'h4Val']],
                   replaceTest: 'stack-with-hyphens', withUpperCase: 'baconBacon']

    when:
    def result = contextParameterProcessor.process(source, context, true)

    then:
    result.test == expectedValue

    where:
    processAttributes                           | sourceValue                                                 | expectedValue
    'leave strings alone'                       | 'just a string'                                             | 'just a string'
    'transform simple properties'               | '${replaceMe}'                                              | 'newValue'
    'transform properties with dots'            | '${h1.h1}'                                                  | 'h1Val'
    'transform embedded properties'             | '${replaceMe} and ${replaceMe}'                             | 'newValue and newValue'
    'transform hierarchical values'             | '${hierarchy.h2}'                                           | 'hierarchyValue'
    'transform nested hierarchical values'      | '${hierarchy.h3.h4}'                                        | 'h4Val'
    'leave unresolvable values'                 | '${notResolvable}'                                          | '${notResolvable}'
    'get a value in an array'                   | '${testArray[0]}'                                           | 'good'
    'get a value in an map within an array'     | '${testArray[1].arrayVal}'                                  | 'bad'
    'get a value in an array within an array'   | '${testArray[2][0].one}'                                    | 'two'
    'support SPEL expression'                   | '${ h1.h1 == "h1Val" }'                                     | true
    'support SPEL defaults'                     | '${ h1.h2  ?: 60 }'                                         | 60
    'support SPEL string methods no args'       | '${ withUpperCase.toLowerCase() }'                          | 'baconbacon'
    'support SPEL string methods with args'     | '${ replaceTest.replaceAll("-","") }'                       | 'stackwithhyphens'
    'make any string alphanumerical for deploy' | '${ #alphanumerical(replaceTest) }'                         | 'stackwithhyphens'
    'make any string alphanumerical for deploy' | '''${#readJson('{ "newValue":"two" }')[#root.replaceMe]}''' | 'two' // [#root.parameters.cluster]
    'support composite expression'              | 'a.${replaceMe}.b.${replaceMe}'                             | 'a.newValue.b.newValue'
    'leave unresolved expressions alone'        | 'a.${ENV1}.b.${ENV2}'                                       | 'a.${ENV1}.b.${ENV2}'
    'support partial resolution for composites' | 'a.${ENV1}.b.${replaceMe}'                                  | 'a.${ENV1}.b.newValue'
    'can call replaceAll'                       | '${ "jeyrs!".replaceAll("\\W", "") }'                       | 'jeyrs'
  }

  def "should include evaluation summary errors on partially evaluated composite expression"() {
    given:
    def source = ['test': value]

    when:
    def result = contextParameterProcessor.process(source, ['replaceMe': 'newValue'], true)
    def summary = result.getOrDefault('expressionEvaluationSummary', [:]) as Map<String, List>

    then:
    result.test == evaluatedValue
    summary.values().size() == errorCount


    where:
    value                                        | evaluatedValue                    | errorCount
    'a.${ENV1}.b.${ENV2}'                        | 'a.${ENV1}.b.${ENV2}'             | 1
    'a.${ENV1}.b.${replaceMe}'                   | 'a.${ENV1}.b.newValue'            | 1
    'a.${replaceMe}.b.${replaceMe}'              | 'a.newValue.b.newValue'           | 0
    'a.${\'${ESCAPED_LITERAL}\'}.b.${replaceMe}' | 'a.${ESCAPED_LITERAL}.b.newValue' | 0
  }

  @Unroll
  def "should restrict fromUrl requests #desc"() {
    given:
    def source = ['test': '${ #fromUrl(\'' + theTest + '\')}']
    def escapedExpression = escapeExpression(source.test)

    when:
    def result = contextParameterProcessor.process(source, [:], true)
    def summary = result.expressionEvaluationSummary as Map<String, List>

    then:
    result.test == source.test
    summary[escapedExpression].size() == 1
    summary[escapedExpression][0].level as String == ExpressionEvaluationSummary.Result.Level.ERROR.name()
    summary[escapedExpression][0].timestamp != null

    where:
    theTest                   | desc
    'file:///etc/passwd'      | 'file scheme'
    'http://169.254.169.254/' | 'link local'
    'http://127.0.0.1/'       | 'localhost'
    'http://localhost/'       | 'localhost by name'
    //successful case: 'http://captive.apple.com/' | 'this should work'
  }

  def "base64 encode and decode"() {
    expect:
    contextParameterProcessor.process([test: '${#toBase64("Yo Dawg")}'], [:], true).test == 'WW8gRGF3Zw=='
    contextParameterProcessor.process([test: '${#fromBase64("WW8gRGF3Zw==")}'], [:], true).test == 'Yo Dawg'
  }

  @Unroll
  def "should not System.exit"() {
    when:
    def result = contextParameterProcessor.process([test: testCase], [:], true)
    def escapedExpression = escapeExpression(testCase)
    def summary = result.expressionEvaluationSummary as Map<String, List>

    then:
    //the failure scenario for this test case is the VM halting...
    summary[escapedExpression].size() == 1
    summary[escapedExpression][0].level as String == ExpressionEvaluationSummary.Result.Level.ERROR.name()

    where:
    testCase                                  | desc
    '${T(java.lang.System).exit(1)}'          | 'System.exit'
    '${T(java.lang.Runtime).runtime.exit(1)}' | 'Runtime.getRuntime.exit'

  }

  @Unroll
  def "should not allow bad type #desc"() {
    given:
    def source = [test: testCase]
    def escapedExpression = escapeExpression(source.test)

    when:
    def result = contextParameterProcessor.process(source, [:], true)
    def summary = result.expressionEvaluationSummary as Map<String, List>

    then:
    //ensure we failed to interpret the expression and left it as is
    result.test == source.test
    summary[escapedExpression].size() == 1
    summary[escapedExpression][0].level as String == ExpressionEvaluationSummary.Result.Level.ERROR.name()
    summary[escapedExpression][0].exceptionType == SpelEvaluationException.typeName

    where:
    testCase                                                            | desc
    '${ new java.net.URL(\'http://google.com\').openConnection() }'     | 'URL'
    '${T(java.lang.Boolean).forName(\'java.net.URI\').getSimpleName()}' | 'forName'
  }

  @Unroll
  def "should not allow bad method #desc"() {
    given:
    def source = [test: testCase]
    def escapedExpression = escapeExpression(source.test)

    when:
    def result = contextParameterProcessor.process(source, [:], true)
    def summary = result.expressionEvaluationSummary as Map<String, List>

    then:
    //ensure we failed to interpret the expression and left it as is
    result.test == source.test
    summary[escapedExpression].size() == 1
    summary[escapedExpression][0].level as String == ExpressionEvaluationSummary.Result.Level.ERROR.name()
    summary[escapedExpression][0].exceptionType == SpelEvaluationException.typeName

    where:
    testCase                                                   | desc
    '${ new java.lang.Integer(1).wait(100000) }'               | 'wait'
    '${ new java.lang.Integer(1).getClass().getSimpleName() }' | 'getClass'
  }

  def "should deny access to groovy metaclass methods via #desc"() {
    given:
    def source = [test: testCase]
    def escapedExpression = escapeExpression(source.test)

    when:
    def result = contextParameterProcessor.process(source, [status: ExecutionStatus.PAUSED, nested: [status: ExecutionStatus.RUNNING]], true)
    def summary = result.expressionEvaluationSummary as Map<String, List>

    then:
    result.test == source.test
    summary[escapedExpression].size() == 1
    summary[escapedExpression][0].level as String == ExpressionEvaluationSummary.Result.Level.ERROR.name()

    where:
    testCase                          | desc
    '${status.getMetaClass()}'        | 'method'
    '${status.metaClass}'             | 'propertyAccessor'
    '${nested.status.metaClass}'      | 'nested accessor'
    '${nested.status.getMetaClass()}' | 'nested method'
  }

  @Unroll
  def "when allowUnknownKeys is #allowUnknownKeys it #desc"() {
    given:
    def source = [test: sourceValue]
    def context = [exists: 'yay', isempty: '', isnull: null]

    when:
    def result = contextParameterProcessor.process(source, context, allowUnknownKeys)

    then:
    result.test == expectedValue

    where:
    desc                                      | sourceValue              | expectedValue            | allowUnknownKeys
    'should leave alone non existing'         | '${noexists}-foo'        | '${noexists}-foo'        | false
    'should handle elvis'                     | '${noexists ?: "bacon"}' | 'bacon'                  | true
    'should leave elvis expression untouched' | '${noexists ?: "bacon"}' | '${noexists ?: "bacon"}' | false
    'should work with empty existing key'     | '${isempty ?: "bacon"}'  | 'bacon'                  | true
    'should work with empty existing key'     | '${isempty ?: "bacon"}'  | 'bacon'                  | false
    'should work with null existing key'      | '${isnull ?: "bacon"}'   | 'bacon'                  | true
    'should work with null existing key'      | '${isnull ?: "bacon"}'   | 'bacon'                  | false

  }

  def "should not allow subtypes in expression allowed types"() {
    given:
    def source = ["test": sourceValue]
    def context = [:]
    def escapedExpression = escapeExpression(source.test)

    when:
    def result = contextParameterProcessor.process(source, context, true)
    def summary = result.expressionEvaluationSummary as Map<String, List>

    then:
    result.test == sourceValue
    summary[escapedExpression].size() == 1
    summary[escapedExpression][0].level as String == ExpressionEvaluationSummary.Result.Level.ERROR.name()

    where:
    sourceValue = '${new rx.internal.util.RxThreadFactory("").newThread(null).getContextClassLoader().toString()}'
  }

  def "should replace the keys in a map"() {
    given:
    def source = ['${replaceMe}': 'somevalue', '${replaceMe}again': ['cats': 'dogs']]

    when:
    def result = contextParameterProcessor.process(source, [replaceMe: 'newVal'], true)

    then:
    result.newVal == 'somevalue'
    result.newValagain?.cats == 'dogs'
  }

  def "should be able to swap out a SPEL expression of a string with other types"() {
    def source = ['test': ['k1': '${var1}', 'k2': '${map1}']]
    def context = [var1: 17, map1: [map1key: 'map1val']]

    when:
    def result = contextParameterProcessor.process(source, context, true)

    then:
    result.test.k1 instanceof Integer
    result.test.k1 == 17
    result.test.k2 instanceof Map
    result.test.k2.map1key == 'map1val'

  }

  def "should process elements of source map correctly"() {

    given:
    def source = ['test': '${h1}', 'nest': ['nest2': ['${h1}', '${h1}']], 'clusters': ['${h1}', '${h2}'], 'intval': 4]
    def context = ['h1': 'h1val', 'h2': 'h2val']

    when:
    def result = contextParameterProcessor.process(source, context, true)

    then:
    result.test == 'h1val'
    result.clusters == ['h1val', 'h2val']
    result.intval == 4
    result.nest.nest2 == ['h1val', 'h1val']
  }

  @Unroll
  def "correctly compute scmInfo attribute"() {
    given:
    context.trigger.buildInfo = new JenkinsBuildInfo("name", 1, "http://jenkins", "SUCCESS", [], scm)

    def source = ['branch': '${scmInfo.branch}']

    when:
    def result = contextParameterProcessor.process(source, context, true)

    then:
    result.branch == expectedBranch

    where:
    scm                                                                                    | expectedBranch
    []                                                                                     | '${scmInfo.branch}'
    [new SourceControl("", "branch1", "")]                                                 | 'branch1'
    [new SourceControl("", "branch1", ""), new SourceControl("", "master", "")]            | 'branch1'
    [new SourceControl("", "develop", ""), new SourceControl("", "master", "")]            | 'develop'
    [new SourceControl("", "buildBranch", ""), new SourceControl("", "jenkinsBranch", "")] | 'buildBranch'

    context = [
      trigger: new JenkinsTrigger("master", "job", 1, null)
    ]
  }

  @Unroll
  def "does not fail when buildInfo contains a webhook stage response"() {
    // TODO(jacobkiefer): Outgoing webhook stage responses land in buildInfo. Why?
    given:
    def source = ['triggerId': '${trigger.correlationId}']
    def context = [
      trigger  : new DefaultTrigger("manual", "id"),
      buildInfo: buildInfo
    ]

    when:
    def result = contextParameterProcessor.process(source, context, true)

    then:
    result.triggerId == 'id'

    where:
    buildInfo = "<html></html>" // Webhook stage response.
  }

  def "ignores deployment details that have not yet ran"() {
    given:
    def source = [deployed: '${deployedServerGroups}']
    def ctx = [execution: OrcaObjectMapper.newInstance().convertValue(execution, Map)]

    when:
    def result = contextParameterProcessor.process(source, ctx, true)
    def summary = result.expressionEvaluationSummary as Map<String, List>

    then:
    result.deployed == '${deployedServerGroups}'
    summary.values().size() == 1
    summary.values().first()[0].description.contains("Failed to evaluate [deployed]")

    where:
    execution = [
      stages: [
        [
          type         : "deploy",
          name         : "Deploy in us-east-1",
          context      : [
            capacity             : [desired: 1, max: 1, min: 1],
            "deploy.account.name": "test",
            stack                : "test",
            strategy             : "highlander",
            subnetType           : "internal",
            suspendedProcesses   : [],
            terminationPolicies  : ["Default"],
            type                 : "linearDeploy"
          ],
          parentStageId: "dca27ddd-ce7d-42a0-a1db-5b43c6b2f0c7",
        ]
      ]
    ]

  }

  @Unroll
  def "constructs execution context correctly"() {
    when:
    def result = contextParameterProcessor.buildExecutionContext(execution)

    then:
    result.size() == 2
    result.execution == execution
    result.trigger == OrcaObjectMapper.newInstance().convertValue(execution.trigger, Map)

    where:
    execution << [
      pipeline {
        stage {
          type = "wait"
          status = SUCCEEDED
        }
        trigger = new JenkinsTrigger("master", "job", 1, null)
      },
      pipeline {
        stage {
          type = "wait"
          status = SUCCEEDED
        }
      }]
  }

  @Unroll
  def "constructs stage context correctly"() {
    when:
    def testStage = execution.stageByRef("stage1")
    def result = contextParameterProcessor.buildExecutionContext(testStage)

    then:
    result.size() == 3
    result.execution == execution
    result.trigger == OrcaObjectMapper.newInstance().convertValue(execution.trigger, Map)
    result.waitTime == testStage.context.waitTime

    where:
    execution << [
      pipeline {
        stage {
          refId = "stage1"
          type = "wait"
          status = SUCCEEDED
          context = [waitTime: 10]
        }
        trigger = new JenkinsTrigger("master", "job", 1, null)
      },
      pipeline {
        stage {
          refId = "stage1"
          type = "wait"
          status = SUCCEEDED
          context = [waitTime: 10]
        }
      }]
  }

  def "is able to parse deployment details correctly from execution"() {
    given:
    def source = ['deployed': '${deployedServerGroups}']

    when:
    def result = contextParameterProcessor.process(source, contextParameterProcessor.buildExecutionContext(execution), true)

    then:
    result.deployed.size == 2
    result.deployed.serverGroup == ["flex-test-v043", "flex-prestaging-v011"]
    result.deployed.region == ["us-east-1", "us-west-1"]
    result.deployed.ami == ["ami-06362b6e", "ami-f759b7b3"]

    where:
    execution = pipeline {
      stage {
        type = "bake"
        name = "Bake"
        refId = "1"
        status = SUCCEEDED
        outputs.putAll(
          deploymentDetails: [
            [
              ami      : "ami-06362b6e",
              amiSuffix: "201505150627",
              baseLabel: "candidate",
              baseOs   : "ubuntu",
              package  : "flex",
              region   : "us-east-1",
              storeType: "ebs",
              vmType   : "pv"
            ],
            [
              ami      : "ami-f759b7b3",
              amiSuffix: "201505150627",
              baseLabel: "candidate",
              baseOs   : "ubuntu",
              package  : "flex",
              region   : "us-west-1",
              storeType: "ebs",
              vmType   : "pv"
            ]
          ]
        )
      }
      stage {
        type = "deploy"
        name = "Deploy"
        refId = "2"
        requisiteStageRefIds = ["1"]
        status = SUCCEEDED
        stage {
          status = SUCCEEDED
          type = "createServerGroup"
          name = "Deploy in us-east-1"
          context.putAll(
            "account": "test",
            "application": "flex",
            "availabilityZones": [
              "us-east-1": [
                "us-east-1c",
                "us-east-1d",
                "us-east-1e"
              ]
            ],
            "capacity": [
              "desired": 1,
              "max"    : 1,
              "min"    : 1
            ],
            "deploy.account.name": "test",
            "deploy.server.groups": [
              "us-east-1": [
                "flex-test-v043"
              ]
            ],
            "stack": "test",
            "strategy": "highlander",
            "subnetType": "internal",
            "suspendedProcesses": [],
            "terminationPolicies": [
              "Default"
            ]
          )
        }
        stage {
          type = "destroyAsg"
          name = "destroyAsg"
        }
        stage {
          type = "createServerGroup"
          name = "Deploy in us-west-1"
          status = SUCCEEDED
          context.putAll(
            account: "prod",
            application: "flex",
            availabilityZones: [
              "us-west-1": [
                "us-west-1a",
                "us-west-1c"
              ]
            ],
            capacity: [
              desired: 1,
              max    : 1,
              min    : 1
            ],
            cooldown: 10,
            "deploy.account.name": "prod",
            "deploy.server.groups": [
              "us-west-1": [
                "flex-prestaging-v011"
              ]
            ],
            keyPair: "nf-prod-keypair-a",
            loadBalancers: [
              "flex-prestaging-frontend"
            ],
            provider: "aws",
            securityGroups: [
              "sg-d2c3dfbe",
              "sg-d3c3dfbf"
            ],
            stack: "prestaging",
            strategy: "highlander",
            subnetType: "internal",
            suspendedProcesses: [],
            terminationPolicies: [
              "Default"
            ]
          )
        }
      }
    }
  }

  def 'helper method to convert objects into json'() {

    given:
    def source = ['json': '${#toJson( map )}']
    def context = [map: [["v1": "k1"], ["v2": "k2"]]]

    when:
    def result = contextParameterProcessor.process(source, context, true)

    then:
    result.json == '[{"v1":"k1"},{"v2":"k2"}]'
  }

  def "should resolve deployment details using helper function"() {
    given:
    def source = ['deployed': '${#deployedServerGroups()}']

    when:
    def result = contextParameterProcessor.process(source, contextParameterProcessor.buildExecutionContext(execution), true)

    then:
    result.deployed.size == 2
    result.deployed.serverGroup == ["flex-test-v043", "flex-prestaging-v011"]
    result.deployed.region == ["us-east-1", "us-west-1"]
    result.deployed.ami == ["ami-06362b6e", "ami-f759b7b3"]
    result.deployed.deployments == [ [[ "serverGroupName": "flex-test-v043" ]], [] ]

    when: 'specifying a stage name'
    source = ['deployed': '${#deployedServerGroups("Deploy in us-east-1")}']
    result = contextParameterProcessor.process(source, contextParameterProcessor.buildExecutionContext(execution), true)

    then: 'should only consider the specified stage name/id'
    result.deployed.size == 1
    result.deployed.serverGroup == ["flex-test-v043"]
    result.deployed.region == ["us-east-1"]
    result.deployed.ami == ["ami-06362b6e"]
    result.deployed.deployments == [ [[ "serverGroupName": "flex-test-v043" ]] ]

    where:
    execution = pipeline {
      stage {
        type = "bake"
        name = "Bake"
        refId = "1"
        status = SUCCEEDED
        outputs.putAll(
          deploymentDetails: [
            [
              ami      : "ami-06362b6e",
              amiSuffix: "201505150627",
              baseLabel: "candidate",
              baseOs   : "ubuntu",
              package  : "flex",
              region   : "us-east-1",
              storeType: "ebs",
              vmType   : "pv"
            ],
            [
              ami      : "ami-f759b7b3",
              amiSuffix: "201505150627",
              baseLabel: "candidate",
              baseOs   : "ubuntu",
              package  : "flex",
              region   : "us-west-1",
              storeType: "ebs",
              vmType   : "pv"
            ]
          ]
        )
      }
      stage {
        type = "deploy"
        name = "Deploy"
        refId = "2"
        requisiteStageRefIds = ["1"]
        status = SUCCEEDED
        stage {
          status = SUCCEEDED
          type = "createServerGroup"
          name = "Deploy in us-east-1"
          context.putAll(
            "account": "test",
            "application": "flex",
            "availabilityZones": [
              "us-east-1": [
                "us-east-1c",
                "us-east-1d",
                "us-east-1e"
              ]
            ],
            "capacity": [
              "desired": 1,
              "max"    : 1,
              "min"    : 1
            ],
            "deploy.account.name": "test",
            "deploy.server.groups": [
              "us-east-1": [
                "flex-test-v043"
              ]
            ],
            "stack": "test",
            "strategy": "highlander",
            "subnetType": "internal",
            "suspendedProcesses": [],
            "terminationPolicies": [
              "Default"
            ],
            "kato.tasks": [
              [
                "resultObjects": [
                  [
                    "deployments": [
                      [
                        "serverGroupName": "flex-test-v043"
                      ]
                    ]
                  ]
                ]
              ]
            ]
          )
        }
        stage {
          type = "destroyAsg"
          name = "destroyAsg"
        }
        stage {
          type = "createServerGroup"
          name = "Deploy in us-west-1"
          status = SUCCEEDED
          context.putAll(
            account: "prod",
            application: "flex",
            availabilityZones: [
              "us-west-1": [
                "us-west-1a",
                "us-west-1c"
              ]
            ],
            capacity: [
              desired: 1,
              max    : 1,
              min    : 1
            ],
            cooldown: 10,
            "deploy.account.name": "prod",
            "deploy.server.groups": [
              "us-west-1": [
                "flex-prestaging-v011"
              ]
            ],
            keyPair: "nf-prod-keypair-a",
            loadBalancers: [
              "flex-prestaging-frontend"
            ],
            provider: "aws",
            securityGroups: [
              "sg-d2c3dfbe",
              "sg-d3c3dfbf"
            ],
            stack: "prestaging",
            strategy: "highlander",
            subnetType: "internal",
            suspendedProcesses: [],
            terminationPolicies: [
              "Default"
            ]
          )
        }
      }
    }
  }

  def 'can operate on List from json'() {
    given:
    def source = [
      'expression': '${#toJson(list["regions"].split(",")).contains("us-west-2")}',
    ]
    def context = [list: [regions: regions]]

    when:
    def result = contextParameterProcessor.process(source, context, true)

    then:
    result.expression == expected

    where:
    regions               | expected
    'us-west-2'           | true
    'us-east-1'           | false
    'us-east-1,us-west-2' | true
    'us-east-1,eu-west-1' | false
  }

  @Unroll
  def 'helper method to convert Strings into Integers'() {
    given:
    def source = [intParam: '${#toInt( str )}']
    def context = [str: str]

    when:
    def result = contextParameterProcessor.process(source, context, true)

    then:
    result.intParam instanceof Integer
    result.intParam == intParam

    where:
    str | intParam
    '0' | 0
    '1' | 1
  }

  @Unroll
  def 'helper method to convert Strings into Floats'() {
    given:
    def source = [floatParam: '${#toFloat( str )}']
    def context = [str: str]

    when:
    def result = contextParameterProcessor.process(source, context, true)

    then:
    result.floatParam instanceof Float
    result.floatParam == floatParam

    where:
    str   | floatParam
    '7'   | 7f
    '7.5' | 7.5f
  }

  @Unroll
  def 'helper method to convert Strings into Booleans'() {
    given:
    def source = [booleanParam: '${#toBoolean( str )}']
    def context = [str: str]

    when:
    def result = contextParameterProcessor.process(source, context, true)

    then:
    result.booleanParam instanceof Boolean
    result.booleanParam == booleanParam

    where:
    str     | booleanParam
    'true'  | true
    'false' | false
    null    | false
  }

  @Unroll
  def 'json reader returns a list if the item passed starts with a ['() {
    expect:
    expectedClass.isInstance(UrlExpressionFunctionProvider.readJson(json))

    where:
    json               | expectedClass
    '[ "one", "two" ]' | List
    '{ "one":"two" }'  | Map

  }

  def "can find a stage in an execution and get its status"() {
    given:
    def pipe = pipeline {
      stage {
        type = "wait"
        name = "Wait1"
        refId = "1"
        context.waitTime = 1
      }
      stage {
        type = "wait"
        name = "Wait2"
        refId = "2"
        requisiteStageRefIds = ["1"]
        context.waitTime = 1
        context.comments = 'succeeded: ${#stage("Wait1")["status"] == "SUCCEEDED"}'
        context.succeeded = '${#stage("Wait1").hasSucceeded}'
        context.failed = '${#stage("Wait1").hasFailed}'
      }
    }

    def stage1 = pipe.stageByRef("1")
    stage1.setStatus(SUCCEEDED)

    def stage2 = pipe.stageByRef("2")
    def ctx = contextParameterProcessor.buildExecutionContext(stage2)

    when:
    def result = contextParameterProcessor.process(stage2.context, ctx, true)

    then:
    result.comments == "succeeded: true"
    result.succeeded == true
    result.failed == false

    when:
    stage1.setStatus(TERMINAL)
    result = contextParameterProcessor.process(stage2.context, ctx, true)

    then:
    result.comments == "succeeded: false"
    result.succeeded == false
    result.failed == true
  }

  def "can not toJson an execution with expressions in the context"() {
    given:
    def pipe = pipeline {
      stage {
        type = "wait"
        name = "Wait1"
        refId = "1"
        context.comments = '${#toJson(execution)}'
        context.waitTime = 1
      }
    }

    def stage = pipe.stageByRef("1")
    def ctx = contextParameterProcessor.buildExecutionContext(stage)

    when:
    def result = contextParameterProcessor.process(stage.context, ctx, true)
    def summary = result.expressionEvaluationSummary as Map<String, List>
    def escapedExpression = escapeExpression('${#toJson(execution)}')

    then:
    result.comments == '${#toJson(execution)}'
    summary.size() == 1
    summary[escapedExpression][0].level as String == ExpressionEvaluationSummary.Result.Level.ERROR.name()
    summary[escapedExpression][0].description.contains("Failed to evaluate [comments] result for toJson cannot contain an expression")
  }

  def "can read authenticated user in an execution"() {
    given:
    def pipe = pipeline {
      stage {
        type = "wait"
        name = "Wait1"
        refId = "1"
        context.comments = '${execution["authentication"]["user"].split("@")[0]}'
        context.waitTime = 1
      }
    }

    pipe.setAuthentication(new Execution.AuthenticationDetails('joeyjoejoejuniorshabadoo@host.net'))

    def stage = pipe.stages.find { it.name == "Wait1" }
    def ctx = contextParameterProcessor.buildExecutionContext(stage)

    when:
    def result = contextParameterProcessor.process(stage.context, ctx, true)

    then:
    result.comments == "joeyjoejoejuniorshabadoo"
  }

  def "can find a judgment result from execution"() {
    given:
    def expectedJudmentInput = "Real Judgment input"
    def pipe = pipeline {
      stage {
        type = "bake"
        name = "my stage"
        context = [judgmentInput: "input2", judgment: '${#judgment("my stage")}']
      }
      stage {
        type = "manualJudgment"
        name = "my stage"
        context = [judgmentInput: expectedJudmentInput]
      }
    }

    and:
    def stage = pipe.stages.find { it.type == "bake" }
    def ctx = contextParameterProcessor.buildExecutionContext(stage)

    when:
    def result = contextParameterProcessor.process(stage.context, ctx, true)

    then:
    result.judgment == expectedJudmentInput
    notThrown(SpelHelperFunctionException)
  }

  def "should get stage status as String"() {
    given:
    def pipe = pipeline {
      stage {
        refId = "1"
        type = "wait"
        name = "wait stage"
        context = [status: '${#stage("manualJudgment stage")["status"]}']
      }
      stage {
        refId = "2"
        type = "manualJudgment"
        name = "manualJudgment stage"
        status = SUCCEEDED
        context = [judgmentInput: "Real Judgment input"]
      }
    }

    and:
    def stage = pipe.stageByRef("1")
    def ctx = contextParameterProcessor.buildExecutionContext(stage)

    when:
    def result = contextParameterProcessor.process(stage.context, ctx, true)

    then:
    result.status == "SUCCEEDED"
    result.status instanceof String
  }

  @Unroll
  def "Correctly evaluates parameters field in the trigger"() {
    given:
    Map context = [trigger: new DefaultTrigger("","","", parameters)]

    def source = ['branch': '${parameters["branch"] ?: "noValue"}']

    when:
    def result = contextParameterProcessor.process(source, context, true)

    then:
    result.branch == expectedBranch

    where:
    parameters       || expectedBranch
    [:]              || "noValue"
    [notFound:"xyz"] || "noValue"
    [branch:"hello"] || "hello"
  }

  @Unroll
  def "Correctly evaluates expressions that refer to outputs of prior stages"() {
    given:
    def execution = pipeline {
      stage {
        type = "evaluateVariables"
        name = "Evaluate namespace"
        refId = "1"
        status = SUCCEEDED
        outputs.putAll(
          keyA: "valueA",
          keyB: "valueB"
        )
      }
      stage {
        type = "deployManifest"
        name = "Deploy manifest"
        refId = "2"
        requisiteStageRefIds = ["1"]
        context.putAll(
          manifests: [
            [
              kind: 'ReplicaSet',
              name: '${keyA}',
              namespace: '${keyB}'
            ]

          ]
        )
      }
    }

    def stage = execution.stageByRef("2")
    def ctx = contextParameterProcessor.buildExecutionContext(stage)

    when:
    def result = contextParameterProcessor.process(stage.context, ctx, true)

    then: "doesn't mutate the context"
    ctx == contextParameterProcessor.buildExecutionContext(stage)

    and: "looks up results from prior stages"
    result.manifests == [
      [
        kind: 'ReplicaSet',
        name: 'valueA',
        namespace: 'valueB'
      ]
    ]
  }

  static escapeExpression(String expression) {
    return ExpressionTransform.escapeSimpleExpression(expression)
  }
}
