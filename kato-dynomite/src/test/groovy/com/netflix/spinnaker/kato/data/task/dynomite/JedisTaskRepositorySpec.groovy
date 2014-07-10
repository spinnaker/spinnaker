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

package com.netflix.spinnaker.kato.data.task.dynomite

import com.netflix.spinnaker.kato.config.JedisConfig
import redis.clients.jedis.JedisCommands
import spock.lang.Shared
import spock.lang.Specification

class JedisTaskRepositorySpec extends Specification {

  @Shared
  JedisTaskRepository taskRepository

  @Shared
  JedisCommands jedis

  @Shared
  JedisConfig config

  def setupSpec() {
    taskRepository = new JedisTaskRepository()
    config = new JedisConfig()
    jedis = config.jedis(0, '127.0.0.1', 'none')
    JedisTask.metaClass.getRepository = { taskRepository }
    taskRepository.jedis = jedis
  }

  def setup() {
    jedis.flushDB()
  }

  void cleanupSpec() {
    config.destroy()
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
    t1.id == t2.id
    t1.status.status == t2.status.status
    t1.status.phase == t2.status.phase
    t1.startTimeMs == t2.startTimeMs
    t1.status.isCompleted() == t2.status.isCompleted()
    t1.status.isFailed() == t2.status.isFailed()
    !t1.status.isCompleted()
    !t1.status.isFailed()
  }

  void "complete and failed are preserved"() {
    setup:
    def t1 = taskRepository.create("TEST", "Test Status")
    t1.fail()

    when:
    def t2 = taskRepository.get(t1.id)

    then:
    t2.status.isCompleted()
    t2.status.isFailed()
  }

  void "listing tasks returns all avilable tasks"() {
    setup:
    def t1 = taskRepository.create "TEST", "Test Status"
    def t2 = taskRepository.create "TEST", "Test Status"

    when:
    def list = taskRepository.list()

    then:
    list*.id.containsAll([t1.id, t2.id])
  }

  void "Can add a result object and retrieve it"() {
    setup:
    def t1 = taskRepository.create "Test", "Test Status"
    final String s = 'bah'

    expect:
    taskRepository.getResultObjects(t1).empty

    when:
    String result = s
    taskRepository.addResultObject(result, t1)
    List<Object> resultObjects = taskRepository.getResultObjects(t1)

    then:
    resultObjects.size() == 1
    resultObjects.first() == s

    when:
    taskRepository.addResultObject("new String", t1)
    resultObjects = taskRepository.getResultObjects(t1)

    then:
    resultObjects.size() == 2
  }

  void "task history is correctly persisted"() {
    given:
    def t1 = taskRepository.create "Test", "Test Status"
    taskRepository.addToHistory('Orchestration', 'started', t1)
    def history = taskRepository.getHistory(t1)
    def firstEntry = history.first()

    expect:
    history.size() == 1
    firstEntry.class.simpleName == 'TaskDisplayStatus'
    firstEntry.phase == 'Orchestration'
    firstEntry.status == 'started'

    when:
    3.times {
      taskRepository.addToHistory('Orchestration', "update ${it}", t1)
    }

    then:
    taskRepository.getHistory(t1).size() == 4
  }

}
