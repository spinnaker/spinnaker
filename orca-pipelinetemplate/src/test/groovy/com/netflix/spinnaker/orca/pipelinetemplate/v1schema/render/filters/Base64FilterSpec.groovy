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
package com.netflix.spinnaker.orca.pipelinetemplate.v1schema.render.filters

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.orca.front50.Front50Service
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.model.PipelineTemplate
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.render.DefaultRenderContext
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.render.JinjaRenderer
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.render.RenderContext
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.render.Renderer
import spock.lang.Specification
import spock.lang.Unroll

class Base64FilterSpec extends Specification {

  Renderer renderer = new JinjaRenderer(new ObjectMapper(), Mock(Front50Service), [])

  @Unroll
  def "should render variable before encode"() {
    given:
    RenderContext context = new DefaultRenderContext("myapp", Mock(PipelineTemplate), [:])
    context.variables.put("name", "zed")
    context.variables.put("message", message)

    when:
    def result = renderer.render("{{ message|base64 }}", context)

    then:
    result == Base64.encoder.encodeToString(expectedResult.bytes)

    where:
    message             || expectedResult
    'hello'             || 'hello'
    'hello, {{ name }}' || 'hello, zed'
  }
}
