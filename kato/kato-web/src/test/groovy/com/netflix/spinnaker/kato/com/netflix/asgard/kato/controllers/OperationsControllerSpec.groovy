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

package com.netflix.spinnaker.kato.com.netflix.asgard.kato.controllers

import com.netflix.spinnaker.kato.controllers.OperationsController
import com.netflix.spinnaker.kato.data.task.Task
import com.netflix.spinnaker.kato.orchestration.AnnotationsBasedAtomicOperationsRegistry
import com.netflix.spinnaker.kato.orchestration.AtomicOperation
import com.netflix.spinnaker.kato.orchestration.AtomicOperationConverter
import com.netflix.spinnaker.kato.orchestration.OrchestrationProcessor
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import spock.lang.Specification

class OperationsControllerSpec extends Specification {

  void "controller takes many operation descriptions, resolves them from the spring context, and executes them in order"() {
    setup:
    """
        AtomicOperationConverter beans must be registered in the application context, with the bean name that corresponds to the key
        that is describing them in the request. For example, a description that looks like this:
           { "desc1": {} }
        will go to the Spring context for a bean named "desc1", and will call the "convertOperation" method on it, with the description as input.
      """
    OrchestrationProcessor orchestrationProcessor = Mock(OrchestrationProcessor)
    def mvc = MockMvcBuilders.standaloneSetup(
      new OperationsController(
        orchestrationProcessor: orchestrationProcessor,
        atomicOperationsRegistry: new AnnotationsBasedAtomicOperationsRegistry(
          applicationContext: new AnnotationConfigApplicationContext(TestConfig),
          cloudProviders: []
        )
      )).build()

    when:
    mvc.perform(MockMvcRequestBuilders.post("/ops").contentType(MediaType.APPLICATION_JSON).content('[ { "desc1": {}, "desc2": {} } ]')).andReturn()

    then:
    "Operations were supplied IN ORDER to the orchestration processor."
    1 * orchestrationProcessor.process(*_) >> {
      // The need for this flatten is weird -- seems like a bug in spock.
      assert it?.flatten()*.getClass() == [Op1, Op2]
      Mock(Task)
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

  static class Converter1 implements AtomicOperationConverter {
    AtomicOperation convertOperation(Map input) {
      new Op1()
    }

    Object convertDescription(Map input) {
      return null
    }
  }

  static class Converter2 implements AtomicOperationConverter {
    AtomicOperation convertOperation(Map input) {
      new Op2()
    }

    Object convertDescription(Map input) {
      return null
    }
  }
}
