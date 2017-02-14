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
package com.netflix.spinnaker.orca.pipelinetemplate

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spectator.api.Clock
import com.netflix.spectator.api.Registry
import com.netflix.spectator.api.Timer
import com.netflix.spinnaker.orca.extensionpoint.pipeline.PipelinePreprocessor
import com.netflix.spinnaker.orca.pipelinetemplate.loader.FileTemplateSchemeLoader
import com.netflix.spinnaker.orca.pipelinetemplate.loader.TemplateLoader
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.render.HandlebarsRenderer
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.render.Renderer
import org.unitils.reflectionassert.ReflectionComparatorMode
import spock.lang.Specification
import spock.lang.Subject

import static org.unitils.reflectionassert.ReflectionAssert.assertReflectionEquals

class PipelineTemplatePipelinePreprocessorSpec extends Specification {

  ObjectMapper objectMapper = new ObjectMapper()

  TemplateLoader templateLoader = new TemplateLoader([new FileTemplateSchemeLoader(objectMapper)])

  Renderer renderer = new HandlebarsRenderer(objectMapper)

  Registry registry = Mock() {
    clock() >> Mock(Clock) {
      monotonicTime() >> 0L
    }
    timer(_) >> Mock(Timer)
  }

  @Subject
  PipelinePreprocessor subject = new PipelineTemplatePipelinePreprocessor(
    objectMapper,
    templateLoader,
    renderer,
    registry
  )

  def 'should ignore non-templated pipeline requests'() {
    given:
    def request = [
      type: 'not-interested'
    ]

    when:
    def result = subject.process(request)

    then:
    result == [
      type: 'not-interested'
    ]
    0 * _
  }

  def 'should process simple template'() {
    given:
    def request = [
      type: 'templatedPipeline',
      trigger: [
        type: "jenkins",
        master: "master",
        job: "job",
        buildNumber: 1111
      ],
      config: [
        id: 'myTemplate',
        pipeline: [
          application: 'myapp',
          template: [
            source: getClass().getResource("/templates/simple-001.yml").toURI()
          ],
          variables: [
            regions: ['us-east-1', 'us-west-2']
          ]
        ]
      ]
    ]

    when:
    def result = subject.process(request)

    then:
    def expected = [
      id: null,
      application: 'myapp',
      name: 'Unnamed Execution',
      keepWaitingPipelines: false,
      limitConcurrent: true,
      parallel: true,
      notifications: [],
      stages: [
        [
          id: null,
          refId: 'bake',
          type: 'bake',
          name: 'Bake',
          requisiteStageRefIds: [],
          context: [
            regions: ['us-east-1', 'us-west-2'],
            package: 'myapp-package',
            baseOs: 'trusty',
            vmType: 'hvm',
            storeType: 'ebs',
            baseLabel: 'release'
          ]
        ],
        [
          id: null,
          refId: 'tagImage',
          type: 'tagImage',
          name: 'Tag Image',
          requisiteStageRefIds: ['bake'],
          context: [
            tags: [
              stack: 'test'
            ]
          ]
        ]
      ]
    ]
    assertReflectionEquals(expected, result, ReflectionComparatorMode.IGNORE_DEFAULTS)
  }
}
