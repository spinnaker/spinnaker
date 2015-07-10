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


package com.netflix.spinnaker.kato.controllers

import com.netflix.spectator.api.ExtendedRegistry
import com.netflix.spinnaker.kato.data.task.Task
import com.netflix.spinnaker.kato.deploy.DescriptionValidationErrors
import com.netflix.spinnaker.kato.deploy.DescriptionValidator
import com.netflix.spinnaker.kato.orchestration.AtomicOperation
import com.netflix.spinnaker.kato.orchestration.AtomicOperationConverter
import com.netflix.spinnaker.kato.orchestration.AtomicOperationException
import com.netflix.spinnaker.kato.orchestration.AtomicOperationNotFoundException
import com.netflix.spinnaker.kato.orchestration.OrchestrationProcessor
import com.netflix.spinnaker.kato.security.AllowedAccountsValidator
import com.netflix.spinnaker.security.AuthenticatedRequest
import groovy.transform.Canonical
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationContext
import org.springframework.context.MessageSource
import org.springframework.context.i18n.LocaleContextHolder
import org.springframework.http.HttpStatus
import org.springframework.validation.Errors
import org.springframework.validation.ObjectError
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

  @Autowired
  ExtendedRegistry extendedRegistry

  @Autowired(required = false)
  Collection<AllowedAccountsValidator> allowedAccountValidators = []

  @RequestMapping(method = RequestMethod.POST)
  Map<String, String> deploy(@RequestBody List<Map<String, Map>> requestBody) {
    def atomicOperations = collectAtomicOperations(requestBody)
    start atomicOperations
  }

  @RequestMapping(value = "/{name}", method = RequestMethod.POST)
  Map<String, String> atomic(@PathVariable("name") String name, @RequestBody Map requestBody) {
    def atomicOperations = collectAtomicOperations([[(name): requestBody]])
    start atomicOperations
  }

  private List<AtomicOperation> collectAtomicOperations(List<Map<String, Map>> inputs) {
    def results = convert(inputs)
    def atomicOperations = []
    for (bindingResult in results) {
      if (bindingResult.errors.hasErrors()) {
        throw new ValidationException(bindingResult.errors)
      } else {
        atomicOperations.addAll(bindingResult.atomicOperations)
      }
    }
    atomicOperations
  }

  @ExceptionHandler(ValidationException)
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  Map handleValidationException(HttpServletRequest req, ValidationException ex) {
    Locale locale = LocaleContextHolder.locale
    def errorStrings = []
    ex.errors.each { Errors errors ->
      errors.allErrors.each { ObjectError objectError ->
        def message = messageSource.getMessage(objectError.code, objectError.arguments, objectError.code, locale)
        errorStrings << ((message != objectError.code) ? message : (objectError.defaultMessage ?: message))
      }
    }
    [error: "Validation Failed.", errors: errorStrings, status: HttpStatus.BAD_REQUEST]
  }

  @ExceptionHandler(AtomicOperationException)
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  Map handleAtomicOperationException(HttpServletRequest req, AtomicOperationException e) {
    [error: e.error, errors: e.errors, status: HttpStatus.BAD_REQUEST]
  }

  private Map<String, String> start(List<AtomicOperation> atomicOperations) {
    Task task = orchestrationProcessor.process(atomicOperations)
    [id: task.id, resourceUri: "/task/${task.id}".toString()]
  }

  private List<AtomicOperationBindingResult> convert(List<Map<String, Map>> inputs) {
    def username = AuthenticatedRequest.getSpinnakerUser().orElse("unknown")
    def allowedAccounts = AuthenticatedRequest.getSpinnakerAccounts().orElse("").split(",") as List<String>

    def descriptions = []
    inputs.collectMany { input ->
      input.collect { k, v ->
        def converter = (AtomicOperationConverter) applicationContext.getBean(k)
        def description = converter.convertDescription(new HashMap(v))
        descriptions << description
        def errors = new DescriptionValidationErrors(description)
        if (applicationContext.containsBean("${k}Validator")) {
          def validator = (DescriptionValidator) applicationContext.getBean("${k}Validator")
          validator.validate(descriptions, description, errors)
        }

        allowedAccountValidators.each {
          it.validate(username, allowedAccounts, description, errors)
        }

        AtomicOperation atomicOperation = converter.convertOperation(v)
        if (!atomicOperation) {
          throw new AtomicOperationNotFoundException(k)
        }
        if (errors.hasErrors()) {
          extendedRegistry.counter("validationErrors", "operation", atomicOperation.class.simpleName).increment()
        }
        new AtomicOperationBindingResult(atomicOperation, errors)
      }
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
