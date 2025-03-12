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
package com.netflix.spinnaker.orca.pipelinetemplate.v1schema.graph.transform

import com.jayway.jsonpath.PathNotFoundException
import com.netflix.spinnaker.orca.pipelinetemplate.exceptions.IllegalTemplateConfigurationException
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.model.PipelineTemplate
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.model.StageDefinition
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.model.StageDefinition.InheritanceControl.Rule
import spock.lang.Specification
import spock.lang.Subject

class StageInheritanceControlTransformSpec extends Specification {

  @Subject StageInheritanceControlTransform subject = new StageInheritanceControlTransform()

  def 'should merge like data structures'() {
    given:
    PipelineTemplate template = new PipelineTemplate(
      stages: [
        new StageDefinition(
          id: 's1',
          config: [
            myList: [1, 2],
            myMap: [
              foo: 'bar'
            ]
          ],
          inheritanceControl: [
            merge: [
              new Rule(path: '$.myList', value: [3]),
              new Rule(path: '$.myMap', value: [baz: 'bang'])
            ]
          ]
        )
      ]
    )

    when:
    subject.visitPipelineTemplate(template)

    then:
    noExceptionThrown()
    template.stages[0].config.myList == [1, 2, 3]
    template.stages[0].config.myMap == [foo: 'bar', baz: 'bang']
  }

  def 'should fail merging incompatible data structures'() {
    given:
    PipelineTemplate template = new PipelineTemplate(
      stages: [
        new StageDefinition(
          id: 's1',
          config: [
            myList: [1, 2],
            myMap: [
              foo: 'bar'
            ]
          ],
          inheritanceControl: [
            merge: [
              new Rule(path: path, value: value)
            ]
          ]
        )
      ]
    )

    when:
    subject.visitPipelineTemplate(template)

    then:
    thrown(IllegalTemplateConfigurationException)

    where:
    path        | value
    '$.myList'  | 1
    '$.myList'  | [foo: 'bar']
    '$.myMap'   | 1
    '$.myMap'   | [1]
  }

  def 'should fail when path does not exist'() {
    given:
    PipelineTemplate template = new PipelineTemplate(
      stages: [
        new StageDefinition(
          id: 's1',
          config: [:],
          inheritanceControl: [
            merge: [
              new Rule(path: '$.noExist', value: [4])
            ]
          ]
        )
      ]
    )

    when:
    subject.visitPipelineTemplate(template)

    then:
    thrown(PathNotFoundException)
  }

  def 'should replace like data structures'() {
    given:
    PipelineTemplate template = new PipelineTemplate(
      stages: [
        new StageDefinition(
          id: 's1',
          config: [
            myList: [1, 2],
            myMap: [
              foo: 'bar'
            ]
          ],
          inheritanceControl: [
            replace: [
              new Rule(path: '$.myList', value: [3, 4]),
              new Rule(path: '$.myMap', value: [baz: 'bang'])
            ]
          ]
        )
      ]
    )

    when:
    subject.visitPipelineTemplate(template)

    then:
    noExceptionThrown()
    template.stages[0].config.myList == [3, 4]
    template.stages[0].config.myMap == [baz: 'bang']
  }

  def 'should fail replacing incompatible data structures'() {
    given:
    PipelineTemplate template = new PipelineTemplate(
      stages: [
        new StageDefinition(
          id: 's1',
          config: [
            myList: [1, 2],
            myMap: [
              foo: 'bar'
            ]
          ],
          inheritanceControl: [
            replace: [
              new Rule(path: path, value: value)
            ]
          ]
        )
      ]
    )

    when:
    subject.visitPipelineTemplate(template)

    then:
    thrown(IllegalTemplateConfigurationException)

    where:
    path        | value
    '$.myList'  | 1
    '$.myList'  | [foo: 'bar']
    '$.myMap'   | 1
    '$.myMap'   | [1]
  }

  def 'should remove paths'() {
    given:
    PipelineTemplate template = new PipelineTemplate(
      stages: [
        new StageDefinition(
          id: 's1',
          config: [
            myList: [1, 2],
            myMap: [
              foo: 'bar'
            ],
            subList: [3, 4],
            subMap: [foo: 'bar', baz: 'bang']
          ],
          inheritanceControl: [
            remove: [
              new Rule(path: '$.myList'),
              new Rule(path: '$.subList[0]'),
              new Rule(path: '$.myMap'),
              new Rule(path: '$.subMap.foo')
            ]
          ]
        )
      ]
    )

    when:
    subject.visitPipelineTemplate(template)

    then:
    noExceptionThrown()
    template.stages[0].config.myList == null
    template.stages[0].config.myMap == null
    template.stages[0].config.subList == [4]
    template.stages[0].config.subMap == [baz: 'bang']
  }
}
