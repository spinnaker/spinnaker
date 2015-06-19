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

abstract class PipelineQueueTck<T extends PipelineQueue> extends Specification {

  @Subject
  T pipelineQueue

  void setup() {
    pipelineQueue = createPipelineQueue()
  }

  abstract T createPipelineQueue()

  String id = '1234'

  void "offer returns correct value if item is in a list"() {
    expect:
    !pipelineQueue.contains(id)

    when:
    pipelineQueue.add(id, 'abc')

    then:
    pipelineQueue.contains(id)

    when:
    pipelineQueue.remove(id, 'abc')

    then:
    !pipelineQueue.contains(id)
  }

  void "offer returns correct value if items are removed"() {
    given:
    pipelineQueue.add(id, 'abc')
    pipelineQueue.add(id, 'abc2')

    when:
    pipelineQueue.remove(id, 'abc')

    then:
    pipelineQueue.contains(id)

    when:
    pipelineQueue.remove(id, 'abc2')

    then:
    !pipelineQueue.contains(id)
  }

  void "can retrieve all items in a list"() {
    given:
    pipelineQueue.add(id, 'abc')
    pipelineQueue.add(id, 'abc2')
    pipelineQueue.add(id, 'abc3')

    expect:
    pipelineQueue.elements(id) == ['abc3', 'abc2', 'abc']
  }

}
