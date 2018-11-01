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
package com.netflix.spinnaker.orca.pipelinetemplate.v1schema

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spectator.api.*
import com.netflix.spinnaker.orca.front50.Front50Service
import com.netflix.spinnaker.orca.pipeline.util.ContextParameterProcessor
import com.netflix.spinnaker.orca.pipelinetemplate.PipelineTemplatePreprocessor
import com.netflix.spinnaker.orca.pipelinetemplate.handler.PipelineTemplateErrorHandler
import com.netflix.spinnaker.orca.pipelinetemplate.handler.SchemaVersionHandler
import com.netflix.spinnaker.orca.pipelinetemplate.loader.ResourceSchemeLoader
import com.netflix.spinnaker.orca.pipelinetemplate.loader.TemplateLoader
import com.netflix.spinnaker.orca.pipelinetemplate.loader.v2.V2FileTemplateSchemeLoader
import com.netflix.spinnaker.orca.pipelinetemplate.loader.v2.V2TemplateLoader
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.handler.V1SchemaHandlerGroup
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.handler.v2.V2SchemaHandlerGroup
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.model.PipelineTemplate
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.model.TemplateConfiguration
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.render.JinjaRenderer
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.render.Renderer
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.render.YamlRenderedValueConverter
import org.springframework.core.io.Resource
import org.springframework.core.io.support.PathMatchingResourcePatternResolver
import org.unitils.reflectionassert.ReflectionComparatorMode
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.SafeConstructor
import spock.lang.Specification
import spock.lang.Unroll

import static org.unitils.reflectionassert.ReflectionAssert.assertReflectionEquals

class V1SchemaIntegrationSpec extends Specification {

  ObjectMapper objectMapper = new ObjectMapper()

  TemplateLoader templateLoader = new TemplateLoader([new ResourceSchemeLoader("/integration/v1schema", objectMapper)])
  V2TemplateLoader v2TemplateLoader = new V2TemplateLoader([new V2FileTemplateSchemeLoader(objectMapper)])
  ContextParameterProcessor contextParameterProcessor = new ContextParameterProcessor()

  Renderer renderer = new JinjaRenderer(
    new YamlRenderedValueConverter(), objectMapper, Mock(Front50Service), []
  )

  Registry registry = Mock() {
    clock() >> Mock(Clock) {
      monotonicTime() >> 0L
    }
    timer(_) >> Mock(Timer)
    createId(_) >> Mock(Id)
    counter(_) >> Mock(Counter)
  }

  @Unroll
  def 'test handler strategy "#integration.name"'() {
    given:
    PipelineTemplatePreprocessor subject = new PipelineTemplatePreprocessor(
      objectMapper,
      new SchemaVersionHandler(
        new V1SchemaHandlerGroup(templateLoader, renderer, objectMapper, registry),
        new V2SchemaHandlerGroup(v2TemplateLoader, objectMapper, contextParameterProcessor, registry)),
      new PipelineTemplateErrorHandler(),
      registry
    )

    def expected = integration.expected

    when:
    def result = subject.process(integration.toRequest())

    then:
    assertReflectionEquals(expected, result, ReflectionComparatorMode.IGNORE_DEFAULTS)

    where:
    integration << new IntegrationTestDataProvider().provide()
  }

  private static class IntegrationTestDataProvider {

    Yaml yaml = new Yaml(new SafeConstructor())
    ObjectMapper objectMapper = new ObjectMapper()

    List<IntegrationTest> provide() {
      Resource[] resources = new PathMatchingResourcePatternResolver().getResources("/integration/v1schema/**")

      List<IntegrationTest> tests = []

      resources.findAll { new File(it.URI).isFile() }.each {
        String name = it.filename.split(/-/)[0]

        IntegrationTest test = tests.find { it.name == name }
        if (test == null) {
          test = new IntegrationTest(name: name)
          tests.add(test)
        }

        if (it.filename.endsWith('-config.yml')) {
          test.configuration = objectMapper.convertValue(yaml.load(it.file.text), TemplateConfiguration)
        } else if (it.filename.endsWith('-expected.json')) {
          test.expected = objectMapper.readValue(it.file, Map)
        } else if (it.filename.endsWith('-request.json')) {
          test.request = objectMapper.readValue(it.file, Map)
        } else {
          test.template.add(objectMapper.convertValue(yaml.load(it.file.text), PipelineTemplate))
        }
      }

      return tests
    }
  }

  static class IntegrationTest {
    String name
    TemplateConfiguration configuration
    List<PipelineTemplate> template = []
    Map<String, Object> request
    Map<String, Object> expected

    Map<String, Object> toRequest() {
      if (request != null) {
        if (configuration != null) {
          request.config = configuration
        }
        if (!template.isEmpty()) {
          request.template = template.find { it.source == null }
        }
        request.plan = true
        return request
      }

      def req = [
        schema: '1',
        type: 'templatedPipeline',
        trigger: [:],
        config: configuration,
        plan: true
      ]

      if (configuration?.pipeline?.template?.source == null) {
        req.template = template.find { it.source == null }
      }

      return req
    }
  }
}
