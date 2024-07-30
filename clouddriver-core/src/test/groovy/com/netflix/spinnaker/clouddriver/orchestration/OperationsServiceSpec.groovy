/*
 * Copyright 2019 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
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
import com.netflix.spinnaker.clouddriver.deploy.DeployDescription
import com.netflix.spinnaker.clouddriver.deploy.DefaultDescriptionAuthorizer
import com.netflix.spinnaker.clouddriver.saga.persistence.SagaRepository
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsRepository
import com.netflix.spinnaker.kork.web.exceptions.ExceptionMessageDecorator
import com.netflix.spinnaker.orchestration.OperationDescription
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

class OperationsServiceSpec extends Specification {

  DefaultDescriptionAuthorizer descriptionAuthorizer = Mock(DefaultDescriptionAuthorizer)
  ExceptionMessageDecorator exceptionMessageDecorator = Mock(ExceptionMessageDecorator)

  @Subject
  OperationsService operationsService = new OperationsService(
    new AnnotationsBasedAtomicOperationsRegistry(
      applicationContext: new AnnotationConfigApplicationContext(TestConfig),
      cloudProviders: []
    ),
    [descriptionAuthorizer],
    Optional.empty(),
    Optional.empty(),
    Mock(AccountCredentialsRepository),
    Optional.of(Mock(SagaRepository)),
    new NoopRegistry(),
    new ObjectMapper(),
    exceptionMessageDecorator
  )

  void "many operation descriptions are resolved and returned in order"() {
    when:
    def atomicOperations = operationsService.collectAtomicOperations([[desc1: [:], desc2: [:]]])

    then:
    atomicOperations.flatten()*.getClass() == [Op1, Op2]
  }

  @Unroll
  void "should only pre-process inputs of supported description classes"() {
    when:
    def output = operationsService.processDescriptionInput(
      descriptionPreProcessors as Collection<AtomicOperationDescriptionPreProcessor>,
      converter,
      descriptionInput
    )

    then:
    output == expectedOutput

    where:
    descriptionPreProcessors                       | converter                                     | descriptionInput       || expectedOutput
    []                                             | new Provider2DeployAtomicOperationConverter() | ["a": "b"]             || ["a": "b"]
    [provider1PreProcessor]                        | new Provider2DeployAtomicOperationConverter() | ["a": "b"]             || ["a": "b"]
    [provider1PreProcessor, provider2PreProcessor] | new Provider2DeployAtomicOperationConverter() | ["provider2": "false"] || ["additionalKey": "additionalVal", "provider2": "true"]
  }

  @Shared
  def provider1PreProcessor = new AtomicOperationDescriptionPreProcessor() {
    @Override
    boolean supports(Class descriptionClass) {
      return descriptionClass == Provider1DeployDescription
    }

    @Override
    Map process(Map description) {
      return ["provider1": "true"]
    }
  }

  @Shared
  def provider2PreProcessor = new AtomicOperationDescriptionPreProcessor() {
    @Override
    boolean supports(Class descriptionClass) {
      return descriptionClass == Provider2DeployDescription
    }

    @Override
    Map process(Map description) {
      return new HashMap(description) + [
        "additionalKey": "additionalVal",
        "provider2"    : "true"
      ]
    }
  }

  @Configuration
  static class TestConfig {
    @Bean
    Converter1 desc1() {
      new Converter1()
    }

    @Bean
    Converter2 desc2() {
      new Converter2()
    }
  }

  private static class Provider1DeployDescription implements DeployDescription {
  }

  private static class Provider2DeployDescription implements DeployDescription {
  }

  private static class Provider2DeployAtomicOperationConverter implements AtomicOperationConverter {
    @Override
    AtomicOperation convertOperation(Map input) {
      throw new UnsupportedOperationException()
    }

    Provider2DeployDescription convertDescription(Map input) {
      return new ObjectMapper().convertValue(input, Provider2DeployDescription)
    }
  }


  static class Converter1 implements AtomicOperationConverter {
    AtomicOperation convertOperation(Map input) {
      new Op1()
    }

    OperationDescription convertDescription(Map input) {
      return null
    }
  }

  static class Converter2 implements AtomicOperationConverter {
    AtomicOperation convertOperation(Map input) {
      new Op2()
    }

    OperationDescription convertDescription(Map input) {
      return null
    }
  }

  static class Op1 implements AtomicOperation {
    Object operate(List priorOutputs) {
      return null
    }
  }

  static class Op2 implements AtomicOperation {
    Object operate(List priorOutputs) {
      return null
    }
  }
}
