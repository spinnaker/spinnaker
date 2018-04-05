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
package com.netflix.spinnaker.orca.pipelinetemplate.v1schema.converter

import groovy.json.JsonSlurper
import spock.lang.Ignore
import spock.lang.Specification

class PipelineTemplateConverterSpec extends Specification {

  def "should convert a pipeline to an ordered pipeline template yaml document"() {
    given:
    def pipeline = new JsonSlurper().parse(new File("src/test/resources/convertedPipelineTemplateSource.json"))

    and:
    String expected = new File("src/test/resources/convertedPipelineTemplate.yml").text

    when:
    String result = new PipelineTemplateConverter().convertToPipelineTemplate(pipeline)

    then:
    expected == result
  }
}
