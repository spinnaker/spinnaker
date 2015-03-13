package com.netflix.spinnaker.orca.pipeline.util

import spock.lang.Specification
import spock.lang.Unroll

class ContextParameterProcessorSpec extends Specification {

  @Unroll
  def "should #processAttributes"() {
    given:
    def source = ['test': sourceValue]
    def context = [replaceMe: 'newValue', 'h1.h1': 'h1Val', hierarchy: [h2: 'hierarchyValue', h3: [h4: 'h4Val']]]

    when:
    def result = ContextParameterProcessor.process(source, context)

    then:
    result.test == expectedValue

    where:
    processAttributes                      | sourceValue                     | expectedValue
    'leave strings alone'                  | 'just a string'                 | 'just a string'
    'transform simple properties'          | '${replaceMe}'                  | 'newValue'
    'transform properties with dots'       | '${h1.h1}'                      | 'h1Val'
    'transform embedded properties'        | '${replaceMe} and ${replaceMe}' | 'newValue and newValue'
    'transform hierarchical values'        | '${hierarchy.h2}'               | 'hierarchyValue'
    'transform nested hierarchical values' | '${hierarchy.h3.h4}'            | 'h4Val'
    'leave unresolvable values'            | '${notResolvable}'              | '${notResolvable}'
  }

  def 'correctly flattens an object'() {
    expect:
    ContextParameterProcessor.flattenMap('', source) == result

    where:
    source                         | result
    [a: 'b']                       | [a: 'b']
    [a: 'b', c: 'd']               | [a: 'b', c: 'd']
    [a: [b: 'c']]                  | ['a.b': 'c']
    [a: [b: 'c', d: 'e']]          | ['a.b': 'c', 'a.d': 'e']
    [a: [b: [c: [d: 'e']]]]        | ['a.b.c.d': 'e']
    [a: [b: ['1', '2'], c: 'huh']] | ['a.b': ['1', '2'], 'a.c': 'huh']
    ['a.b': ['c': 'huh']]          | ['a.b.c': 'huh']
  }

}
