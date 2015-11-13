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

import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.kato.data.task.Task
import com.netflix.spinnaker.kato.deploy.DescriptionValidationErrors
import com.netflix.spinnaker.kato.deploy.DescriptionValidator
import com.netflix.spinnaker.kato.orchestration.AtomicOperation
import com.netflix.spinnaker.kato.orchestration.AtomicOperationException
import com.netflix.spinnaker.kato.orchestration.AtomicOperationNotFoundException
import com.netflix.spinnaker.kato.orchestration.AtomicOperationsRegistry
import com.netflix.spinnaker.kato.orchestration.OrchestrationProcessor
import com.netflix.spinnaker.kato.security.AllowedAccountsValidator
import com.netflix.spinnaker.security.AuthenticatedRequest
import groovy.transform.Canonical
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.MessageSource
import org.springframework.context.i18n.LocaleContextHolder
import org.springframework.http.HttpStatus
import org.springframework.validation.Errors
import org.springframework.validation.ObjectError
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestMethod
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

import javax.servlet.http.HttpServletRequest

@RestController
class OperationsController {

  @Autowired MessageSource messageSource
  @Autowired OrchestrationProcessor orchestrationProcessor
  @Autowired Registry registry
  @Autowired (required = false) Collection<AllowedAccountsValidator> allowedAccountValidators = []
  @Autowired AtomicOperationsRegistry atomicOperationsRegistry

  /*
   * APIs
   * ----------------------------------------------------------------------------------------------------------------------------
   */

  /**
   * @deprecated Use /{cloudProvider}/ops instead
   */
  @Deprecated
  @RequestMapping(value = "/ops", method = RequestMethod.POST)
  Map<String, String> operations(@RequestBody List<Map<String, Map>> requestBody) {
    List<AtomicOperation> atomicOperations = collectAtomicOperations(requestBody)
    start(atomicOperations)
  }

  /**
   * @deprecated Use /{cloudProvider}/ops/{name} instead
   */
  @Deprecated
  @RequestMapping(value = "/ops/{name}", method = RequestMethod.POST)
  Map<String, String> operation(@PathVariable("name") String name, @RequestBody Map requestBody) {
    List<AtomicOperation> atomicOperations = collectAtomicOperations([[(name): requestBody]])
    start(atomicOperations)
  }

  @RequestMapping(value = "/{cloudProvider}/ops", method = RequestMethod.POST)
  Map<String, String> cloudProviderOperations(@PathVariable("cloudProvider") String cloudProvider,
                                              @RequestBody List<Map<String, Map>> requestBody) {
    List<AtomicOperation> atomicOperations = collectAtomicOperations(cloudProvider, requestBody)
    start(atomicOperations)
  }

  @RequestMapping(value = "/{cloudProvider}/ops/{name}", method = RequestMethod.POST)
  Map<String, String> cloudProviderOperation(@PathVariable("cloudProvider") String cloudProvider,
                                             @PathVariable("name") String name,
                                             @RequestBody Map requestBody) {
    List<AtomicOperation> atomicOperations = collectAtomicOperations(cloudProvider, [[(name): requestBody]])
    start(atomicOperations)
  }

  /*
   * Error handlers
   * ----------------------------------------------------------------------------------------------------------------------------
   */

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
    [error: "Validation Failed", errors: errorStrings, status: HttpStatus.BAD_REQUEST]
  }

  @ExceptionHandler(AtomicOperationException)
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  Map handleAtomicOperationException(HttpServletRequest req, AtomicOperationException e) {
    [error: e.error, errors: e.errors, status: HttpStatus.BAD_REQUEST]
  }

  /*
   * ----------------------------------------------------------------------------------------------------------------------------
   */

  private List<AtomicOperation> collectAtomicOperations(List<Map<String, Map>> inputs) {
    collectAtomicOperations(null, inputs)
  }

  private List<AtomicOperation> collectAtomicOperations(String cloudProvider, List<Map<String, Map>> inputs) {
    def results = convert(cloudProvider, inputs)
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

  private List<AtomicOperationBindingResult> convert(String cloudProvider, List<Map<String, Map>> inputs) {
    def username = AuthenticatedRequest.getSpinnakerUser().orElse("unknown")
    def allowedAccounts = AuthenticatedRequest.getSpinnakerAccounts().orElse("").split(",") as List<String>

    def descriptions = []
    inputs.collectMany { Map<String, Map> input ->
      input.collect { String k, Map v ->
        def converter = atomicOperationsRegistry.getAtomicOperationConverter(k, cloudProvider ?: v.cloudProvider)
        def description = converter.convertDescription(new HashMap(v))
        descriptions << description
        def errors = new DescriptionValidationErrors(description)

        def validator = atomicOperationsRegistry.getAtomicOperationDescriptionValidator(
          DescriptionValidator.getValidatorName(k), cloudProvider ?: v.cloudProvider
        )
        if (validator) {
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
          registry.counter("validationErrors", "operation", atomicOperation.class.simpleName).increment()
        }
        new AtomicOperationBindingResult(atomicOperation, errors)
      }
    }

  }

  private Map<String, String> start(List<AtomicOperation> atomicOperations) {
    Task task = orchestrationProcessor.process(atomicOperations)
    [id: task.id, resourceUri: "/task/${task.id}".toString()]
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
