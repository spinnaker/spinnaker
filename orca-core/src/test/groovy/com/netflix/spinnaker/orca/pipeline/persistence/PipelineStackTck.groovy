/*
 * Copyright 2014 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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

package com.netflix.spinnaker.orca.pipeline.persistence

import spock.lang.Specification
import spock.lang.Subject

abstract class PipelineStackTck<T extends PipelineStack> extends Specification {

  @Subject
  T pipelineStack

  void setup() {
    pipelineStack = createPipelineStack()
  }

  abstract T createPipelineStack()

  String id = '1234'

  void "offer returns correct value if item is in a list"() {
    expect:
    !pipelineStack.contains(id)

    when:
    pipelineStack.add(id, 'abc')

    then:
    pipelineStack.contains(id)

    when:
    pipelineStack.remove(id, 'abc')

    then:
    !pipelineStack.contains(id)
  }

  void "offer returns correct value if items are removed"() {
    given:
    pipelineStack.add(id, 'abc')
    pipelineStack.add(id, 'abc2')

    when:
    pipelineStack.remove(id, 'abc')

    then:
    pipelineStack.contains(id)

    when:
    pipelineStack.remove(id, 'abc2')

    then:
    !pipelineStack.contains(id)
  }

  void "can retrieve all items in a list"() {
    given:
    pipelineStack.add(id, 'abc')
    pipelineStack.add(id, 'abc2')
    pipelineStack.add(id, 'abc3')

    expect:
    pipelineStack.elements(id) == ['abc3', 'abc2', 'abc']
  }

}
