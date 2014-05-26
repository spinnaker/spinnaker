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

package com.netflix.bluespar.kato.controllers

import com.netflix.bluespar.kato.data.task.Task
import com.netflix.bluespar.kato.deploy.DescriptionValidationErrors
import com.netflix.bluespar.kato.deploy.DescriptionValidator
import com.netflix.bluespar.kato.orchestration.AtomicOperation
import com.netflix.bluespar.kato.orchestration.AtomicOperationConverter
import com.netflix.bluespar.kato.orchestration.AtomicOperationNotFoundException
import com.netflix.bluespar.kato.orchestration.OrchestrationProcessor
import groovy.transform.Canonical
import groovy.transform.Immutable
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationContext
import org.springframework.context.MessageSource
import org.springframework.context.i18n.LocaleContextHolder
import org.springframework.http.HttpStatus
import org.springframework.validation.Errors
import org.springframework.web.bind.annotation.*

import javax.servlet.http.HttpServletRequest

@RestController
@RequestMapping("/ops")
class OperationsController {
  @Autowired
  MessageSource messageSource

  @Autowired
  ApplicationContext applicationContext

  @Autowired
  OrchestrationProcessor orchestrationProcessor

  @RequestMapping(method = RequestMethod.POST)
  Map<String, String> deploy(@RequestBody List<Map<String, Map>> requestBody) {
    def atomicOperations = []
    requestBody.each { Map<String, Map> input ->
      collectAtomicOperations input, atomicOperations
    }
    start atomicOperations
  }

  @RequestMapping(value = "/{name}", method = RequestMethod.POST)
  Map<String, String> atomic(@PathVariable("name") String name, @RequestBody Map requestBody) {
    def atomicOperations = []
    collectAtomicOperations([(name): requestBody], atomicOperations)
    start atomicOperations
  }

  private void collectAtomicOperations(Map<String, Map> input, List<AtomicOperation> atomicOperations) {
    def results = convert(input)
    for (bindingResult in results) {
      if (bindingResult.errors.hasErrors()) {
        throw new ValidationException(bindingResult.errors)
      } else {
        atomicOperations.addAll bindingResult.atomicOperations
      }
    }
  }

  @ExceptionHandler(ValidationException)
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  Errors handleValidationException(HttpServletRequest req, ValidationException ex) {
    ex.errors
  }

  private Map<String, String> start(List<AtomicOperation> atomicOperations) {
    Task task = orchestrationProcessor.process(atomicOperations)
    [id: task.id, resourceUri: "/task/${task.id}".toString()]
  }

  private List<AtomicOperationBindingResult> convert(Map<String, Map> input) {
    def descriptions = []
    input.collect { k, v ->
      def converter = (AtomicOperationConverter) applicationContext.getBean(k)
      def description = converter.convertDescription(new HashMap(v))
      descriptions << description
      def errors = new DescriptionValidationErrors(description)
      if (applicationContext.containsBean("${k}Validator")) {
        def validator = (DescriptionValidator) applicationContext.getBean("${k}Validator")
        validator.validate(descriptions, description, errors)
      }
      AtomicOperation atomicOperation = converter.convertOperation(v)
      if (!atomicOperation) {
        throw new AtomicOperationNotFoundException(k)
      }
      new AtomicOperationBindingResult(atomicOperation, errors)
    }
  }

  @Canonical
  static class AtomicOperationBindingResult {
    AtomicOperation atomicOperations
    Errors errors
  }

  @Canonical
  static class ValidationException extends RuntimeException {
    Errors errors
  }
}
