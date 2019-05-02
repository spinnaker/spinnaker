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

package com.netflix.spinnaker.clouddriver.orchestration

import com.netflix.spectator.api.Spectator
import com.netflix.spinnaker.clouddriver.data.task.DefaultTask
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.security.AuthenticatedRequest
import org.slf4j.MDC
import org.springframework.beans.factory.config.AutowireCapableBeanFactory
import org.springframework.context.ApplicationContext
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Subject

import java.util.concurrent.TimeUnit

class DefaultOrchestrationProcessorSpec extends Specification {

  @Subject
  DefaultOrchestrationProcessor processor

  @Shared
  ApplicationContext applicationContext

  TaskRepository taskRepository

  String taskKey

  def setup() {
    taskKey = UUID.randomUUID().toString()
    processor = new DefaultOrchestrationProcessor()
    applicationContext = Mock(ApplicationContext)
    applicationContext.getAutowireCapableBeanFactory() >> Mock(AutowireCapableBeanFactory)
    taskRepository = Mock(TaskRepository)
    processor.applicationContext = applicationContext
    processor.taskRepository = taskRepository
    processor.registry = Spectator.globalRegistry()
  }

  void "complete the task when everything goes as planned"() {
    setup:
    def task = new DefaultTask("1")
    def atomicOperation = Mock(AtomicOperation)

    when:
    submitAndWait atomicOperation

    then:
    1 * taskRepository.create(_, _, taskKey) >> task
    task.status.isCompleted()
    !task.status.isFailed()
  }

  void "fail the task when exception is thrown"() {
    setup:
    def task = new DefaultTask("1")
    def atomicOperation = Mock(AtomicOperation)

    when:
    submitAndWait atomicOperation

    then:
    1 * taskRepository.create(_, _, taskKey) >> task
    1 * atomicOperation.operate(_) >> { throw new RuntimeException() }
    task.status.isFailed()
  }

  void "failure should be logged in the result objects"() {
    setup:
    def task = new DefaultTask("1")
    def atomicOperation = Mock(AtomicOperation)

    when:
    submitAndWait atomicOperation

    then:
    1 * taskRepository.create(_, _, taskKey) >> task
    1 * atomicOperation.operate(_) >> { throw new RuntimeException(message) }
    task.resultObjects.find { it.type == "EXCEPTION" }
    task.resultObjects.find { it.type == "EXCEPTION" }.message == message

    where:
    message = "foo"
  }

  void "does not re-run existing task based on clientRequestId"() {
    def task = new DefaultTask("1")
    def atomicOperation = Mock(AtomicOperation)

    when:
    submitAndWait atomicOperation

    then:
    taskRepository.getByClientRequestId(taskKey) >>> [null, task]
    1 * taskRepository.create(_, _, taskKey) >> task
    task.status.isCompleted()
    !task.status.isFailed()
  }

  void "should clear MDC thread local"() {
    given:
    MDC.put("myKey", "myValue")
    MDC.put(AuthenticatedRequest.Header.ACCOUNTS.header, "myAccounts")
    MDC.put(AuthenticatedRequest.Header.USER.header, "myUser")

    when:
    DefaultOrchestrationProcessor.resetMDC()

    then:
    MDC.get("myKey") == "myValue"
    MDC.get(AuthenticatedRequest.Header.ACCOUNTS.header) == null
    MDC.get(AuthenticatedRequest.Header.USER.header) == null
  }

  private void submitAndWait(AtomicOperation atomicOp) {
    processor.process([atomicOp], taskKey)
    processor.executorService.shutdown()
    processor.executorService.awaitTermination(5, TimeUnit.SECONDS)
  }
}
