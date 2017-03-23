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

import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.model.NamedHashMap
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.model.PipelineTemplate
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.model.PipelineTemplate.Configuration
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.model.PipelineTemplate.Variable
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.model.StageDefinition
import spock.lang.Specification

class TemplateMergeSpec extends Specification {

  def 'should merge list of templates together'() {
    given:
    PipelineTemplate t1 = new PipelineTemplate().with {
      id = 't1'
      schema = '1'
      protect = true
      variables = [
        new Variable(name: 'foo', description: 'foo description', type: 'string', defaultValue: 'foo value'),
        new Variable(name: 'bar', description: 'bar description', type: 'list', defaultValue: ['bar value'])
      ]
      configuration = new Configuration(
        triggers: [
          new NamedHashMap().with {
            put('name', 'trigger1')
            return it
          }
        ],
        parameters: [
          new NamedHashMap().with {
            put('name', 'parameter1')
            return it
          }
        ],
        notifications: [
          new NamedHashMap().with {
            put('name', 'notification1')
            return it
          }
        ]
      )
      stages = [
        new StageDefinition(id: 's1', type: 's1type'),
        new StageDefinition(id: 's2', type: 's2type')
      ]
      return it
    }

    PipelineTemplate t2 = new PipelineTemplate().with {
      id = 't2'
      schema = '1'
      source = 't1'
      variables = [
        new Variable(name: 'foo', description: 'foo description', type: 'string', defaultValue: 'overridden value')
      ]
      configuration = new Configuration(
        triggers: [
          new NamedHashMap().with {
            put('name', 'trigger2')
            return it
          }
        ]
      )
      stages = [
        new StageDefinition(id: 's3', type: 's3type')
      ]
      return it
    }

    when:
    PipelineTemplate result = TemplateMerge.merge([t1, t2])

    then:
    noExceptionThrown()
    result.id == 'mergedTemplate'
    result.schema == '1'
    result.source == 't1'
    result.protect
    result.variables*.name == ['foo', 'bar']
    result.variables.find { it.name == 'foo' }.defaultValue == 'overridden value'
    result.configuration.triggers*.name == ['trigger1', 'trigger2']
    result.configuration.parameters*.name == ['parameter1']
    result.configuration.notifications*.name == ['notification1']
    result.stages*.id == ['s1', 's2', 's3']
    result.modules == null
  }
}
