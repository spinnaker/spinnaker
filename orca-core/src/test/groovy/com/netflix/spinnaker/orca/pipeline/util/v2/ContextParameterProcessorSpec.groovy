/*
 * Copyright 2017 Netflix, Inc.
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

package com.netflix.spinnaker.orca.pipeline.util.v2

import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.pipeline.expressions.ExpressionTransform
import com.netflix.spinnaker.orca.pipeline.expressions.ExpressionsSupport
import com.netflix.spinnaker.orca.pipeline.expressions.SpelHelperFunctionException
import com.netflix.spinnaker.orca.pipeline.model.Execution
import com.netflix.spinnaker.orca.pipeline.util.ContextParameterProcessor
import org.springframework.expression.spel.SpelEvaluationException
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

import static com.netflix.spinnaker.orca.pipeline.expressions.ExpressionEvaluationSummary.Result.*
import static com.netflix.spinnaker.orca.test.model.ExecutionBuilder.pipeline
import static com.netflix.spinnaker.orca.test.model.ExecutionBuilder.stage

/**
 * TODO: Used to ensure feature parity between expression evaluation in v1 and v2
 * Eventually remove/consolidate with v1
 */
class ContextParameterProcessorSpec extends Specification {

  @Subject ContextParameterProcessor contextParameterProcessor = new ContextParameterProcessor()

  @Unroll
  def "should #processAttributes"() {
    given:
    def source = ['test': sourceValue]
    def context = ['testArray': ['good', ['arrayVal': 'bad'], [['one': 'two']]], replaceMe: 'newValue', 'h1': [h1: 'h1Val'], hierarchy: [h2: 'hierarchyValue', h3: [h4: 'h4Val']],
                   replaceTest: 'stack-with-hyphens', withUpperCase: 'baconBacon']

    when:
    def result = contextParameterProcessor.processV2(source, context, true)

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
  }

  @Unroll
  def "should restrict fromUrl requests #desc"() {
    given:
    def source = ['test': '${ #fromUrl(\'' + theTest + '\')}']
    def escapedExpression = escapeExpression(source.test)

    when:
    def result = contextParameterProcessor.processV2(source, [:], true)
    def summary = result.expressionEvaluationSummary as Map<String, List>

    then:
    result.test == source.test
    summary[escapedExpression].size() == 1
    summary[escapedExpression][0].level as String == Level.ERROR.name()
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
    contextParameterProcessor.processV2([test: '${#toBase64("Yo Dawg")}'], [:], true).test == 'WW8gRGF3Zw=='
    contextParameterProcessor.processV2([test: '${#fromBase64("WW8gRGF3Zw==")}'], [:], true).test == 'Yo Dawg'
  }

  @Unroll
  def "should not System.exit"() {
    when:
    Map<String, Object> result = contextParameterProcessor.processV2([test: testCase], [:], true)
    def escapedExpression = escapeExpression(testCase)
    def summary = result.expressionEvaluationSummary as Map<String, List>

    then:
    //the failure scenario for this test case is the VM halting...
    summary[escapedExpression].size() == 1
    summary[escapedExpression][0].level as String == Level.ERROR.name()

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
    def result = contextParameterProcessor.processV2(source, [:], true)
    def summary = result.expressionEvaluationSummary as Map<String, List>

    then:
    //ensure we failed to interpret the expression and left it as is
    result.test == source.test
    summary[escapedExpression].size() == 1
    summary[escapedExpression][0].level as String == Level.ERROR.name()
    summary[escapedExpression][0].exceptionType == SpelEvaluationException

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
    def result = contextParameterProcessor.processV2(source, [:], true)
    def summary = result.expressionEvaluationSummary as Map<String, List>

    then:
    //ensure we failed to interpret the expression and left it as is
    result.test == source.test
    summary[escapedExpression].size() == 1
    summary[escapedExpression][0].level as String == Level.ERROR.name()
    summary[escapedExpression][0].exceptionType == SpelEvaluationException

    where:
    testCase                                                            | desc
    '${ new java.lang.Integer(1).wait(100000) }'                        | 'wait'
    '${ new java.lang.Integer(1).getClass().getSimpleName() }'          | 'getClass'
  }

  def "should deny access to groovy metaclass methods via #desc"() {
    given:
    def source = [test: testCase]
    def escapedExpression = escapeExpression(source.test)

    when:
    def result = contextParameterProcessor.processV2(source, [status: ExecutionStatus.PAUSED, nested: [status: ExecutionStatus.RUNNING]], true)
    def summary = result.expressionEvaluationSummary as Map<String, List>

    then:
    result.test == source.test
    summary[escapedExpression].size() == 1
    summary[escapedExpression][0].level as String == Level.ERROR.name()

    where:
    testCase  | desc
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
    def result = contextParameterProcessor.processV2(source, context, allowUnknownKeys)

    then:
    result.test == expectedValue

    where:
    desc                                      | sourceValue              | expectedValue            | allowUnknownKeys
    'should blank out null'                   | '${noexists}-foo'        | '-foo'                   | true
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
    def result = contextParameterProcessor.processV2(source, context, true)
    def summary = result.expressionEvaluationSummary as Map<String, List>

    then:
    result.test == sourceValue
    summary[escapedExpression].size() == 1
    summary[escapedExpression][0].level as String == Level.ERROR.name()

    where:
    sourceValue = '${new rx.internal.util.RxThreadFactory("").newThread(null).getContextClassLoader().toString()}'
  }

  def "should replace the keys in a map"() {
    given:
    def source = ['${replaceMe}': 'somevalue', '${replaceMe}again': ['cats': 'dogs']]

    when:
    def result = contextParameterProcessor.processV2(source, [replaceMe: 'newVal'], true)

    then:
    result.newVal == 'somevalue'
    result.newValagain?.cats == 'dogs'
    !result.containsKey("expressionsEvaluation")
  }

  def "should be able to swap out a SPEL expression of a string with other types"() {
    def source = ['test': ['k1': '${var1}', 'k2': '${map1}']]
    def context = [var1: 17, map1: [map1key: 'map1val']]

    when:
    def result = contextParameterProcessor.processV2(source, context, true)

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
    def result = contextParameterProcessor.processV2(source, context, true)

    then:
    result.test == 'h1val'
    result.clusters == ['h1val', 'h2val']
    result.intval == 4
    result.nest.nest2 == ['h1val', 'h1val']
  }

  @Unroll
  def "correctly compute scmInfo attribute"() {

    given:
    def source = ['branch': '${scmInfo.branch}']

    when:
    def result = contextParameterProcessor.processV2(source, context, true)

    then:
    result.branch == expectedBranch

    where:
    context                                                                                                 | expectedBranch
    [:]                                                                                                     | '${scmInfo.branch}'
    [trigger: [buildInfo: [scm: [[branch: 'branch1']]]]]                                                    | 'branch1'
    [trigger: [buildInfo: [scm: [[branch: 'branch1'], [branch: 'master']]]]]                                | 'branch1'
    [trigger: [buildInfo: [scm: [[branch: 'develop'], [branch: 'master']]]]]                                | 'develop'
    [trigger: [buildInfo: [scm: [[branch: 'jenkinsBranch']]]], buildInfo: [scm: [[branch: 'buildBranch']]]] | 'buildBranch'
  }

  def "ignores deployment details that have not yet ran"() {
    given:
    def source = ['deployed': '${deployedServerGroups}']
    def context = [execution: execution]
    def escapedExpression = escapeExpression(source.deployed)

    when:
    def result = contextParameterProcessor.processV2(source, context, true)
    def summary = result.expressionEvaluationSummary as Map<String, List>

    then:
    result.deployed == '${deployedServerGroups}'
    summary[escapedExpression].size() == 1
    summary[escapedExpression][0].level as String == Level.INFO.name()
    summary[escapedExpression][0].description == "Failed to evaluate [deployed] : deployedServerGroups not found"

    where:
    execution = [
      "stages": [
        [
          "type"         : "deploy",
          "name"         : "Deploy in us-east-1",
          "context"      : [
            "capacity"           : [
              "desired": 1,
              "max"    : 1,
              "min"    : 1
            ],
            "deploy.account.name": "test",
            "stack"              : "test",
            "strategy"           : "highlander",
            "subnetType"         : "internal",
            "suspendedProcesses" : [],
            "terminationPolicies": [
              "Default"
            ],
            "type"               : "linearDeploy"
          ],
          "parentStageId": "dca27ddd-ce7d-42a0-a1db-5b43c6b2f0c7",
        ]
      ]
    ]

  }

  def "is able to parse deployment details correctly from execution"() {

    given:
    def source = ['deployed': '${deployedServerGroups}']
    def context = [execution: execution]

    when:
    def result = contextParameterProcessor.processV2(source, context, true)

    then:
    result.deployed.size == 2
    result.deployed.serverGroup == ['flex-test-v043', 'flex-prestaging-v011']
    result.deployed.region == ['us-east-1', 'us-west-1']
    result.deployed[0].ami == 'ami-06362b6e'

    where:
    execution = [
      "context": [
        "deploymentDetails": [
          [
            "ami"      : "ami-06362b6e",
            "amiSuffix": "201505150627",
            "baseLabel": "candidate",
            "baseOs"   : "ubuntu",
            "package"  : "flex",
            "region"   : "us-east-1",
            "storeType": "ebs",
            "vmType"   : "pv"
          ],
          [
            "ami"      : "ami-f759b7b3",
            "amiSuffix": "201505150627",
            "baseLabel": "candidate",
            "baseOs"   : "ubuntu",
            "package"  : "flex",
            "region"   : "us-west-1",
            "storeType": "ebs",
            "vmType"   : "pv"
          ]
        ]
      ],
      "stages" : [
        [
          "status"       : ExecutionStatus.SUCCEEDED,
          "type"         : "deploy",
          "name"         : "Deploy in us-east-1",
          "context"      : [
            "account"             : "test",
            "application"         : "flex",
            "availabilityZones"   : [
              "us-east-1": [
                "us-east-1c",
                "us-east-1d",
                "us-east-1e"
              ]
            ],
            "capacity"            : [
              "desired": 1,
              "max"    : 1,
              "min"    : 1
            ],
            "deploy.account.name" : "test",
            "deploy.server.groups": [
              "us-east-1": [
                "flex-test-v043"
              ]
            ],
            "stack"               : "test",
            "strategy"            : "highlander",
            "subnetType"          : "internal",
            "suspendedProcesses"  : [],
            "terminationPolicies" : [
              "Default"
            ],
            "type"                : "linearDeploy"
          ],
          "parentStageId": "dca27ddd-ce7d-42a0-a1db-5b43c6b2f0c7",
        ],
        [
          "id"     : "dca27ddd-ce7d-42a0-a1db-5b43c6b2f0c7-2-destroyAsg",
          "type"   : "destroyAsg",
          "name"   : "destroyAsg",
          "context": [
          ]
        ],
        [
          "id"           : "68ad3566-4857-4c76-839e-f4afc14410c5-1-Deployinuswest1",
          "type"         : "deploy",
          "name"         : "Deploy in us-west-1",
          "startTime"    : 1431672074613,
          "endTime"      : 1431672487124,
          "status"       : ExecutionStatus.SUCCEEDED,
          "context"      : [
            "account"             : "prod",
            "application"         : "flex",
            "availabilityZones"   : [
              "us-west-1": [
                "us-west-1a",
                "us-west-1c"
              ]
            ],
            "capacity"            : [
              "desired": 1,
              "max"    : 1,
              "min"    : 1
            ],
            "cooldown"            : 10,
            "deploy.account.name" : "prod",
            "deploy.server.groups": [
              "us-west-1": [
                "flex-prestaging-v011"
              ]
            ],
            "keyPair"             : "nf-prod-keypair-a",
            "loadBalancers"       : [
              "flex-prestaging-frontend"
            ],
            "provider"            : "aws",
            "securityGroups"      : [
              "sg-d2c3dfbe",
              "sg-d3c3dfbf"
            ],
            "stack"               : "prestaging",
            "strategy"            : "highlander",
            "subnetType"          : "internal",
            "suspendedProcesses"  : [],
            "terminationPolicies" : [
              "Default"
            ],
            "type"                : "linearDeploy"
          ],
          "parentStageId": "68ad3566-4857-4c76-839e-f4afc14410c5",
          "scheduledTime": 0
        ]
      ]
    ]
  }

  def 'helper method to convert objects into json'() {

    given:
    def source = ['json': '${#toJson( map )}']
    def context = [map: [["v1": "k1"], ["v2": "k2"]]]

    when:
    def result = contextParameterProcessor.processV2(source, context, true)

    then:
    result.json == '[{"v1":"k1"},{"v2":"k2"}]'
  }

  def 'can operate on List from json'() {
    given:
    def source = [
        'expression': '${#toJson(parameters["regions"].split(",")).contains("us-west-2")}',
    ]
    def context = [parameters: [regions: regions]]

    when:
    def result = contextParameterProcessor.processV2(source, context, true)

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
    def result = contextParameterProcessor.processV2(source, context, true)

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
    def result = contextParameterProcessor.processV2(source, context, true)

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
    def result = contextParameterProcessor.processV2(source, context, true)

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
    expectedClass.isInstance(ExpressionsSupport.readJson(json))

    where:
    json               | expectedClass
    '[ "one", "two" ]' | List
    '{ "one":"two" }'  | Map

  }

  def "can find a stage in an execution"() {
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
        context.comments = '${#stage("Wait1")["status"].toString()}'
      }
    }

    def stage = pipe.stages.find { it.name == "Wait2" }
    def ctx = contextParameterProcessor.buildExecutionContext(stage, true)

    when:
    def result = contextParameterProcessor.processV2(stage.context, ctx, true)

    then:
    result.comments == "NOT_STARTED"
  }

  def "can not toJson an execution with expressions in the context"() {
    given:
    def pipe = pipeline {
      stage {
        type = "wait"
        name = "Wait1"
        refId = "1"
        context = [comments: '${#toJson(execution)}', waitTime: 1]
      }
    }

    def stage = pipe.stages.find { it.name == "Wait1" }
    def ctx = contextParameterProcessor.buildExecutionContext(stage, true)

    when:
    def result = contextParameterProcessor.processV2(stage.context, ctx, true)
    def summary = result.expressionEvaluationSummary as Map<String, List>
    def escapedExpression = escapeExpression('${#toJson(execution)}')

    then:
    result.comments == '${#toJson(execution)}'
    summary.size() == 1
    summary[escapedExpression][0].level as String == Level.ERROR.name()
    summary[escapedExpression][0].description.contains("Failed to evaluate [comments] result for toJson cannot contain an expression")
  }

  def "can read authenticated user in an execution"() {
    given:
    def pipe = pipeline {
      stage {
        type = "wait"
        name = "Wait1"
        refId = "1"
        context = [comments: '${execution["authentication"]["user"].split("@")[0]}', waitTime: 1]
      }
    }

    pipe.setAuthentication(new Execution.AuthenticationDetails('joeyjoejoejuniorshabadoo@host.net'))

    def stage = pipe.stages.find { it.name == "Wait1" }
    def ctx = contextParameterProcessor.buildExecutionContext(stage, true)

    when:
    def result = contextParameterProcessor.processV2(stage.context, ctx, true)

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
    def ctx = contextParameterProcessor.buildExecutionContext(stage, true)

    when:
    def result = contextParameterProcessor.processV2(stage.context, ctx, true)

    then:
    result.judgment == expectedJudmentInput
    notThrown(SpelHelperFunctionException)
  }

  static escapeExpression(String expression) {
    return ExpressionTransform.escapeSimpleExpression(expression)
  }
}
