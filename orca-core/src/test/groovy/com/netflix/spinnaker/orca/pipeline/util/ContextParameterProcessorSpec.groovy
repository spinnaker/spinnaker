package com.netflix.spinnaker.orca.pipeline.util

import spock.lang.Specification
import spock.lang.Unroll

class ContextParameterProcessorSpec extends Specification {

  @Unroll
  def "should #processAttributes"() {
    given:
    def source = ['test': sourceValue]
    def context = ['testArray': ['good', ['arrayVal': 'bad'], [['one': 'two']]], replaceMe: 'newValue', 'h1': [h1: 'h1Val'], hierarchy: [h2: 'hierarchyValue', h3: [h4: 'h4Val']]]

    when:
    def result = ContextParameterProcessor.process(source, context)

    then:
    result.test == expectedValue

    where:
    processAttributes                             | sourceValue                     | expectedValue
    'leave strings alone'                         | 'just a string'                 | 'just a string'
    'transform simple properties'                 | '${replaceMe}'                  | 'newValue'
    'transform properties with dots'              | '${h1.h1}'                      | 'h1Val'
    'transform embedded properties'               | '${replaceMe} and ${replaceMe}' | 'newValue and newValue'
    'transform hierarchical values'               | '${hierarchy.h2}'               | 'hierarchyValue'
    'transform nested hierarchical values'        | '${hierarchy.h3.h4}'            | 'h4Val'
    'leave unresolvable values'                   | '${notResolvable}'              | '${notResolvable}'
    'can get a value in an array'                 | '${testArray[0]}'               | 'good'
    'can get a value in an map within an array'   | '${testArray[1].arrayVal}'      | 'bad'
    'can get a value in an array within an array' | '${testArray[2][0].one}'        | 'two'
    'can support SPEL expression'                 | '${ h1.h1 == "h1Val" }'         | 'true'
    'can support SPEL defaults'                   | '${ h1.h2  ?: 60 }'             | '60'
  }

}
