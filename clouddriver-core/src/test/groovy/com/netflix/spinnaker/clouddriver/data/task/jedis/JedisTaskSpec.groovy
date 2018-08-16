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

import com.netflix.spinnaker.clouddriver.data.task.DefaultTaskStatus
import com.netflix.spinnaker.clouddriver.data.task.Status
import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskState
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Subject

class JedisTaskSpec extends Specification {

  @Shared
  RedisTaskRepository repository

  @Subject
  JedisTask task

  @Shared
  DefaultTaskStatus initialState = new DefaultTaskStatus('orchestration', 'start', TaskState.STARTED)

  void setup() {
    repository = Mock(RedisTaskRepository)
    task = new JedisTask('666', System.currentTimeMillis(), repository, "owner", false)
  }

  void 'updating task status adds a history entry'() {
    when:
    task.updateStatus(newPhase, newState)

    then:
    1 * repository.currentState(task) >> initialState
    1 * repository.addToHistory(initialState.update(newPhase, newState), task)
    0 * _

    where:
    newPhase        | newState
    'orchestration' | 'end'
  }

  void 'accessing the list of history entries retrieves it from Jedis'() {
    when:
    task.history

    then:
    1 * repository.getHistory(task)
  }

  void 'task history excludes the final state that marked the task as complete'() {
    when:
    List<Status> history = task.history

    then:
    1 * repository.getHistory(task) >> [new DefaultTaskStatus('foo', 'bar', TaskState.STARTED), new DefaultTaskStatus('foo', 'baz', TaskState.STARTED), new DefaultTaskStatus('foo', 'biz', TaskState.COMPLETED)]
    history.size() == 2
    history.status == ['bar', 'baz']
  }

  void 'changing the status of a task to complete saves it to Jedis'() {
    when:
    task.complete()

    then:
    1 * repository.currentState(task) >> initialState
    1 * repository.addToHistory(initialState.update(TaskState.COMPLETED), task)
    0 * _
  }

  void 'failing the status of a task saves it to Jedis'() {
    when:
    task.fail()

    then:
    1 * repository.currentState(task) >> initialState
    1 * repository.addToHistory(initialState.update(TaskState.FAILED), task)
    0 * _
  }

  void 'cant update a completed task'() {
    when:
    task.updateStatus('explode', 'plz')

    then:
    1 * repository.currentState(task) >> initialState.update(testState)
    thrown IllegalStateException

    where:
    testState           | shouldFail
    TaskState.COMPLETED | true
    TaskState.FAILED    | true
  }
}
