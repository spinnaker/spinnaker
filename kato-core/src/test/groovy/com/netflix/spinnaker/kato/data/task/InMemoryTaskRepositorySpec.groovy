/*
 * Copyright 2014 Netflix, Inc.
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

package com.netflix.spinnaker.kato.data.task

import spock.lang.Shared
import spock.lang.Specification

class InMemoryTaskRepositorySpec extends Specification {

  @Shared
  InMemoryTaskRepository taskRepository

  def setupSpec() {
    resetTaskRepository()
  }

  void resetTaskRepository() {
    this.taskRepository = new InMemoryTaskRepository()
  }

  void cleanup() {
    resetTaskRepository()
  }

  void "creating a new task returns task with unique id"() {
    given:
    def t1 = taskRepository.create("TEST", "Test Status")
    def t2 = taskRepository.create("TEST", "Test Status")

    expect:
    t1.id != t2.id
  }

  void "looking up a task by id returns the same task"() {
    setup:
    def t1 = taskRepository.create("TEST", "Test Status")

    when:
    def t2 = taskRepository.get(t1.id)

    then:
    t1.is t2
  }

  void "listing tasks returns all avilable tasks"() {
    setup:
    def t1 = taskRepository.create "TEST", "Test Status"
    def t2 = taskRepository.create "TEST", "Test Status"

    when:
    def list = taskRepository.list()

    then:
    list.containsAll([t1, t2])
  }
}
