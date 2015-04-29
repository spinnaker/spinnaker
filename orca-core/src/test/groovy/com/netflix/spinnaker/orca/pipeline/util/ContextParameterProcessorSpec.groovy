package com.netflix.spinnaker.orca.pipeline.util

import spock.lang.Specification
import spock.lang.Unroll

class ContextParameterProcessorSpec extends Specification {

  @Unroll
  def "should #processAttributes"() {
    given:
    def source = ['test': sourceValue]
    def context = ['testArray': ['good', ['arrayVal': 'bad'], [['one': 'two']]], replaceMe: 'newValue', 'h1': [h1: 'h1Val'], hierarchy: [h2: 'hierarchyValue', h3: [h4: 'h4Val']],
                   replaceTest: 'stack-with-hyphens']

    when:
    def result = ContextParameterProcessor.process(source, context)

    then:
    result.test == expectedValue

    where:
    processAttributes                               | sourceValue                           | expectedValue
    'leave strings alone'                           | 'just a string'                       | 'just a string'
    'transform simple properties'                   | '${replaceMe}'                        | 'newValue'
    'transform properties with dots'                | '${h1.h1}'                            | 'h1Val'
    'transform embedded properties'                 | '${replaceMe} and ${replaceMe}'       | 'newValue and newValue'
    'transform hierarchical values'                 | '${hierarchy.h2}'                     | 'hierarchyValue'
    'transform nested hierarchical values'          | '${hierarchy.h3.h4}'                  | 'h4Val'
    'leave unresolvable values'                     | '${notResolvable}'                    | '${notResolvable}'
    'can get a value in an array'                   | '${testArray[0]}'                     | 'good'
    'can get a value in an map within an array'     | '${testArray[1].arrayVal}'            | 'bad'
    'can get a value in an array within an array'   | '${testArray[2][0].one}'              | 'two'
    'can support SPEL expression'                   | '${ h1.h1 == "h1Val" }'               | 'true'
    'can support SPEL defaults'                     | '${ h1.h2  ?: 60 }'                   | '60'
    'can support SPEL string methods'               | '${ replaceTest.replaceAll("-","") }' | 'stackwithhyphens'
    'can make any string alphanumerical for deploy' | '${ #alphanumerical(replaceTest) }'   | 'stackwithhyphens'
  }

  def "should process elements of source map correctly"() {

    given:
    def source = ['test': '${h1}', 'nest': ['nest2': ['${h1}', '${h1}']], 'clusters': ['${h1}', '${h2}'], 'intval': 4]
    def context = ['h1': 'h1val', 'h2': 'h2val']

    when:
    def result = ContextParameterProcessor.process(source, context)

    then:
    result.test == 'h1val'
    result.clusters == ['h1val', 'h2val']
    result.intval == 4
    result.nest.nest2 == ['h1val', 'h1val']
  }

  @Unroll
  def "correctly computer scmInfo attribute"() {

    given:
    def source = ['branch': '${scmInfo.branch}']

    when:
    def result = ContextParameterProcessor.process(source, context)

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

}
