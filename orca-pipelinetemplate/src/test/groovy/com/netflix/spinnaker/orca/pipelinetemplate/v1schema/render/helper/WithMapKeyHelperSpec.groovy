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
import com.github.jknack.handlebars.Template
import spock.lang.Specification

class WithMapKeyHelperSpec extends Specification {

  Handlebars handlebars = new Handlebars()
    .with(EscapingStrategy.NOOP)

  def setup() {
    handlebars.registerHelper("withMapKey", new WithMapKeyHelper())
  }

  def 'should render nested map'() {
    given:
    Map context = [
      'stringVar': 'key1',
      'mapVar': [key1: 'value1', key2: 'value2']
    ]

    and:
    def template = '{{#withMapKey mapVar stringVar}}{{this}}{{/withMapKey}}'

    expect:
    compile(template, context) == 'value1'
  }

  String compile(String template, Object context) {
    Template t = handlebars.compileInline(template)
    return t.apply(context)
  }

}
