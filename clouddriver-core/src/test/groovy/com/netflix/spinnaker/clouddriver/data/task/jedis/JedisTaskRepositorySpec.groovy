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

package com.netflix.spinnaker.clouddriver.data.task.jedis

import com.netflix.spinnaker.cats.redis.test.LocalRedisCheck
import com.netflix.spinnaker.clouddriver.data.task.DefaultTaskStatus
import com.netflix.spinnaker.clouddriver.data.task.TaskState
import redis.clients.jedis.Jedis
import redis.clients.jedis.JedisPool
import spock.lang.IgnoreIf
import spock.lang.Shared
import spock.lang.Specification

@IgnoreIf({ LocalRedisCheck.redisUnavailable() })
class JedisTaskRepositorySpec extends Specification {
  @Shared
  JedisTaskRepository taskRepository

  @Shared
  JedisPool jedisPool

  def setupSpec() {
    taskRepository = new JedisTaskRepository()
    jedisPool = new JedisPool("localhost", 6379)
    taskRepository.jedisPool = jedisPool
  }

  def setup() {
    jedisPool.resource.withCloseable {
      ((Jedis) it).flushDB()
    }
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

  void "listing tasks returns all running tasks"() {
    setup:
    def t1 = taskRepository.create "TEST", "Test Status"
    def t2 = taskRepository.create "TEST", "Test Status"

    when:
    def list = taskRepository.list()

    then:
    list*.id.containsAll([t1.id, t2.id])

    when:
    t1.complete()

    then:
    !taskRepository.list()*.id.contains(t1.id)

    and:
    taskRepository.list()*.id.contains(t2.id)
  }

  void "Can add a result object and retrieve it"() {
    setup:
    JedisTask t1 = taskRepository.create "Test", "Test Status"
    final TestObject s = new TestObject(name:'blimp', value:'bah')

    expect:
    taskRepository.getResultObjects(t1).empty

    when:
    taskRepository.addResultObjects([s], t1)
    List<Object> resultObjects = taskRepository.getResultObjects(t1)

    then:
    resultObjects.size() == 1
    resultObjects.first().name == s.name
    resultObjects.first().value == s.value

    when:
    taskRepository.addResultObjects([new TestObject(name:"t1", value:'h2')], t1)
    resultObjects = taskRepository.getResultObjects(t1)

    then:
    resultObjects.size() == 2
  }

  void "ResultObjects are retrieved in insertion order"() {
    given:
    JedisTask t1 = taskRepository.create "Test", "Test Status"
    4.times {
      taskRepository.addResultObjects([new TestObject(name:"Object${it}", value:'value')], t1)
    }
    expect:
    taskRepository.getResultObjects(t1).collect{it.name} == ['Object0', 'Object1', 'Object2', 'Object3']
  }

  void "task history is correctly persisted"() {
    given:
    JedisTask t1 = taskRepository.create "Test", "Test Status"
    def history = taskRepository.getHistory(t1)

    expect:
    history.size() == 1

    when:
    taskRepository.addToHistory(new DefaultTaskStatus('Orchestration', 'started', TaskState.STARTED), t1)
    history = taskRepository.getHistory(t1)
    def newEntry = history[1]

    then:
    history.size() == 2
    newEntry.class.simpleName == 'TaskDisplayStatus'
    newEntry.phase == 'Orchestration'
    newEntry.status == 'started'

    when:
    3.times {
      taskRepository.addToHistory(new DefaultTaskStatus('Orchestration', "update ${it}", TaskState.STARTED), t1)
    }

    then:
    taskRepository.getHistory(t1).size() == 5
  }

  class TestObject{
    String name
    String value
  }
}
