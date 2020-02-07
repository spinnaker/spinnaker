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

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spectator.api.NoopRegistry
import com.netflix.spinnaker.clouddriver.config.ExceptionClassifierConfigurationProperties
import com.netflix.spinnaker.clouddriver.data.task.DefaultTask
import com.netflix.spinnaker.clouddriver.data.task.SagaId
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.kork.dynamicconfig.DynamicConfigService
import com.netflix.spinnaker.kork.web.context.AuthenticatedRequestContextProvider
import com.netflix.spinnaker.security.AuthenticatedRequest
import org.slf4j.MDC
import org.springframework.beans.factory.config.AutowireCapableBeanFactory
import org.springframework.context.ApplicationContext
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

import java.util.concurrent.TimeUnit

class DefaultOrchestrationProcessorSpec extends Specification {

  @Subject
  DefaultOrchestrationProcessor processor

  @Shared
  ApplicationContext applicationContext

  TaskRepository taskRepository

  DynamicConfigService dynamicConfigService

  String taskKey
  private AuthenticatedRequestContextProvider contextProvider

  def setup() {
    taskKey = UUID.randomUUID().toString()

    taskRepository = Mock(TaskRepository)
    applicationContext = Mock(ApplicationContext)
    applicationContext.getAutowireCapableBeanFactory() >> Mock(AutowireCapableBeanFactory)
    dynamicConfigService = Mock(DynamicConfigService)
    contextProvider = new AuthenticatedRequestContextProvider()

    processor = new DefaultOrchestrationProcessor(
      taskRepository,
      applicationContext,
      new NoopRegistry(),
      Optional.empty(),
      new ObjectMapper(),
      new ExceptionClassifier(new ExceptionClassifierConfigurationProperties(
        retryableClasses: [RetryableException.class.getName()]
      ), dynamicConfigService),
      contextProvider
    )
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

  @Unroll
  void "fail the task when exception is thrown (#exception.class.simpleName, #sagaId)"() {
    setup:
    dynamicConfigService.getConfig(
      String.class,
      "clouddriver.exception-classifier.retryable-exceptions",
      'com.netflix.spinnaker.clouddriver.orchestration.DefaultOrchestrationProcessorSpec$RetryableException'
    ) >> { 'com.netflix.spinnaker.clouddriver.orchestration.DefaultOrchestrationProcessorSpec$SomeDynamicException,com.netflix.spinnaker.clouddriver.orchestration.DefaultOrchestrationProcessorSpec$AnotherDynamicException' }
    def task = new DefaultTask("1")
    if (sagaId) {
      task.sagaIdentifiers.add(sagaId)
    }
    def atomicOperation = Mock(AtomicOperation)

    when:
    submitAndWait atomicOperation

    then:
    1 * taskRepository.create(_, _, taskKey) >> task
    1 * atomicOperation.operate(_) >> { throw exception }
    task.status.isFailed()
    task.status.retryable == retryable

    //Tasks without SagaIds (i.e., not a saga) are not retryable
    where:
    exception                     | sagaId               || retryable
    new RuntimeException()        | null                 || false
    new RetryableException()      | null                 || false
    new RuntimeException()        | new SagaId("a", "a") || false
    new NonRetryableException()   | new SagaId("a", "a") || false
    new RetryableException()      | new SagaId("a", "a") || true
    new SomeDynamicException()    | new SagaId("a", "a") || true
    new AnotherDynamicException() | new SagaId("a", "a") || true
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
    def context = contextProvider.get()
    MDC.put("myKey", "myValue")
    context.setAccounts("myAccounts")
    context.setUser( "myUser")

    when:
    processor.clearRequestContext()

    then:
    MDC.get("myKey") == "myValue"
    !context.getAccounts().isPresent()
    !context.getUser().isPresent()
  }

  private void submitAndWait(AtomicOperation atomicOp) {
    processor.process([atomicOp], taskKey)
    processor.executorService.shutdown()
    processor.executorService.awaitTermination(5, TimeUnit.SECONDS)
  }

  private static class NonRetryableException extends RuntimeException {}
  private static class RetryableException extends RuntimeException {}
  private static class SomeDynamicException extends RuntimeException {}
  private static class AnotherDynamicException extends RuntimeException {}
}
