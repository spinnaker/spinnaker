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

import redis.clients.jedis.JedisCommands
import spock.lang.Shared
import spock.lang.Specification

class JedisTaskSpec extends Specification {

  @Shared JedisTaskRepository repository
  @Shared JedisCommands jedis

  void setup() {
    repository = Mock(JedisTaskRepository)
    repository.jedis = Mock(JedisCommands)
  }

  void 'creating a new task should persist the task'() {
    when:
    new JedisTask('666', 'orchestration', 'start', repository)

    then:
    1 * repository.set('666', _)
    1 * repository.addToHistory('orchestration', 'start', _)
  }

  void 'retrieving an existing task should not save the task'() {
    when:
    new JedisTask('666', 'orchestration', 'start', 0, true, true)

    then:
    0 * repository._
  }

  void 'updating task status adds a history entry'() {
    given:
    JedisTask task = new JedisTask('666', 'orchestration', 'start', 0, false, false)
    task.repository = repository

    when:
    task.updateStatus('orchestration', 'end')

    then:
    1 * repository.set('666', _)
    1 * repository.addToHistory('orchestration', 'end', _)
  }

  void 'accessing the list of history entries retrieves it from Jedis'() {
    given:
    JedisTask task = new JedisTask('666', 'orchestration', 'start', 0, false, false)
    task.repository = repository

    when:
    task.history

    then:
    1 * repository.getHistory(task)
  }

  void 'changing the status of a task to complete saves it to Jedis'() {
    JedisTask task = new JedisTask('666', 'orchestration', 'start', 0, false, false)
    task.repository = repository

    when:
    task.complete()

    then:
    1 * repository.set('666', task)
  }

  void 'failing the status of a task saves it to Jedis'() {
    JedisTask task = new JedisTask('666', 'orchestration', 'start', 0, false, false)
    task.repository = repository

    when:
    task.fail()

    then:
    1 * repository.set('666', task)
  }

}
