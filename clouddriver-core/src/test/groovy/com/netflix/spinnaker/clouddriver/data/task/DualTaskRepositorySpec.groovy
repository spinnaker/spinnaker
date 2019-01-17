/*
 * Copyright 2019 Netflix, Inc.
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
package com.netflix.spinnaker.clouddriver.data.task

import spock.lang.Specification
import spock.lang.Subject

class DualTaskRepositorySpec extends Specification {

  TaskRepository primary = Mock()
  TaskRepository previous = Mock()

  @Subject
  TaskRepository subject = new DualTaskRepository(primary, previous, 4, 1)

  void "always creates tasks from primary"() {
    when:
    subject.create("afternoon", "coffee")

    then:
    1 * primary.create(_, _)
    0 * _

    when:
    subject.create("afternoon", "coffee", "needed")

    then:
    1 * primary.create(_, _, _)
    0 * _
  }

  void "reads from previous if primary is missing a task"() {
    given:
    def expectedTask = new DefaultTask("1")

    when:
    def task = subject.get("1")

    then:
    task == expectedTask
    1 * primary.get(_) >> null
    1 * previous.get(_) >> expectedTask
    0 * _
  }

  void "list collates results from both primary and previous"() {
    when:
    def result = subject.list()

    then:
    result*.id.sort() == ["1", "2", "3", "4"]
    1 * primary.list() >> [
      new DefaultTask("1"),
      new DefaultTask("2")
    ]
    1 * previous.list() >> [
      new DefaultTask("3"),
      new DefaultTask("4")
    ]
    0 * _
  }
}
