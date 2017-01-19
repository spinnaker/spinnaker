/*
 * Copyright 2017 Netflix, Inc.
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
package com.netflix.spinnaker.orca.pipelinetemplate.v1schema.render.helper

import com.github.jknack.handlebars.EscapingStrategy
import com.github.jknack.handlebars.Handlebars
import com.github.jknack.handlebars.HandlebarsException
import com.github.jknack.handlebars.Template
import spock.lang.Specification
import spock.lang.Unroll

class ConditionHelperSpec extends Specification {

  Handlebars handlebars = new Handlebars()
    .with(EscapingStrategy.NOOP)

  def setup() {
    ConditionHelper.register(handlebars)
  }

  @Unroll
  def 'should process conditionals'() {
    given:
    Map context = [
      'simpleValue': 'foo',
      'listVal': ['foo', 'bar'],
      'mapVal': [foo: 'fooval', bar: 'barval']
    ]
    when:
    String result = compile(template, context)

    then:
    noExceptionThrown()
    result == expected

    where:
    template                            || expected
    '{{isEqual simpleValue "foo"}}'     || 'true'
    '{{isEqual simpleValue "bar"}}'     || 'false'
    '{{isNotEqual simpleValue "foo"}}'  || 'false'
    '{{isNotEqual simpleValue "bar"}}'  || 'true'
    '{{contains listVal "foo"}}'        || 'true'
    '{{contains listVal "baz"}}'        || 'false'
    '{{contains mapVal "fooval"}}'      || 'true'
    '{{contains mapVal "foo"}}'         || 'false'
    '{{containsKey mapVal "foo"}}'      || 'true'
    '{{containsKey mapVal "fooval"}}'   || 'false'
  }

  @Unroll
  def 'should throw on invalid argument'() {
    when:
    compile(template, context)

    then:
    Throwable t = thrown(HandlebarsException)
    t.cause.getClass() == IllegalArgumentException

    where:
    template                            | context
    '{{contains value "something"}}'    | [value: 1]
    '{{containsKey value "something"}}'  | [value: 1]
  }

  public String compile(String template, Object context) {
    Template t = handlebars.compileInline(template)
    return t.apply(context)
  }
}
