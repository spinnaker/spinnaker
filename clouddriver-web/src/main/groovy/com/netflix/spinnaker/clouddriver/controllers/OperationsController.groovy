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

package com.netflix.spinnaker.clouddriver.controllers

import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.deploy.DescriptionAuthorizer
import com.netflix.spinnaker.clouddriver.deploy.DescriptionValidationErrors
import com.netflix.spinnaker.clouddriver.deploy.DescriptionValidationException
import com.netflix.spinnaker.clouddriver.deploy.DescriptionValidator
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperationConverter
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperationDescriptionPreProcessor
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperationNotFoundException
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperationsRegistry
import com.netflix.spinnaker.clouddriver.orchestration.OrchestrationProcessor
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsRepository
import com.netflix.spinnaker.clouddriver.security.AllowedAccountsValidator
import com.netflix.spinnaker.clouddriver.security.ProviderVersion
import com.netflix.spinnaker.clouddriver.security.config.SecurityConfig
import com.netflix.spinnaker.security.AuthenticatedRequest
import groovy.transform.Canonical
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.MessageSource
import org.springframework.validation.Errors
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestMethod
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@Slf4j
@RestController
class OperationsController {

  @Autowired MessageSource messageSource
  @Autowired OrchestrationProcessor orchestrationProcessor
  @Autowired Registry registry
  @Autowired (required = false) Collection<AllowedAccountsValidator> allowedAccountValidators = []
  @Autowired (required = false) List<AtomicOperationDescriptionPreProcessor> atomicOperationDescriptionPreProcessors = []
  @Autowired AtomicOperationsRegistry atomicOperationsRegistry
  @Autowired SecurityConfig.OperationsSecurityConfigurationProperties opsSecurityConfigProps
  @Autowired AccountCredentialsRepository accountCredentialsRepository

  @Autowired DescriptionAuthorizer descriptionAuthorizer

  /*
   * APIs
   * ----------------------------------------------------------------------------------------------------------------------------
   */

  /**
   * @deprecated Use /{cloudProvider}/ops instead
   */
  @Deprecated
  @RequestMapping(value = "/ops", method = RequestMethod.POST)
  Map<String, String> operations(@RequestParam(value = "clientRequestId", required = false) String clientRequestId,
                                 @RequestBody List<Map<String, Map>> requestBody) {
    List<AtomicOperation> atomicOperations = collectAtomicOperations(requestBody)
    start(atomicOperations, clientRequestId)
  }

  /**
   * @deprecated Use /{cloudProvider}/ops/{name} instead
   */
  @Deprecated
  @RequestMapping(value = "/ops/{name}", method = RequestMethod.POST)
  Map<String, String> operation(@PathVariable("name") String name,
                                @RequestParam(value = "clientRequestId", required = false) String clientRequestId,
                                @RequestBody Map requestBody) {
    List<AtomicOperation> atomicOperations = collectAtomicOperations([[(name): requestBody]])
    start(atomicOperations, clientRequestId)
  }

  @RequestMapping(value = "/{cloudProvider}/ops", method = RequestMethod.POST)
  Map<String, String> cloudProviderOperations(@PathVariable("cloudProvider") String cloudProvider,
                                              @RequestParam(value = "clientRequestId", required = false) String clientRequestId,
                                              @RequestBody List<Map<String, Map>> requestBody) {
    List<AtomicOperation> atomicOperations = collectAtomicOperations(cloudProvider, requestBody)
    start(atomicOperations, clientRequestId)
  }

  @RequestMapping(value = "/{cloudProvider}/ops/{name}", method = RequestMethod.POST)
  Map<String, String> cloudProviderOperation(@PathVariable("cloudProvider") String cloudProvider,
                                             @PathVariable("name") String name,
                                             @RequestParam(value = "clientRequestId", required = false) String clientRequestId,
                                             @RequestBody Map requestBody) {
    List<AtomicOperation> atomicOperations = collectAtomicOperations(cloudProvider, [[(name): requestBody]])
    start(atomicOperations, clientRequestId)
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
        throw new DescriptionValidationException(bindingResult.errors)
      } else {
        atomicOperations.addAll(bindingResult.atomicOperations)
      }
    }
    atomicOperations
  }

  private ProviderVersion getOperationVersion(Map operation) {
    def providerVersion = ProviderVersion.v1
    try {
      String accountName = operation.credentials ?: operation.accountName ?: operation.account
      if (accountName) {
        def credentials = accountCredentialsRepository.getOne(accountName)
        providerVersion = credentials.getProviderVersion()
      } else {
        log.warn "Unable to get account name from operation: $operation"
      }
    } catch (Exception e) {
      log.warn "Unable to determine account version", e
    }

    return providerVersion
  }

  private List<AtomicOperationBindingResult> convert(String cloudProvider, List<Map<String, Map>> inputs) {
    def username = AuthenticatedRequest.getSpinnakerUser().orElse("unknown")
    def allowedAccounts = AuthenticatedRequest.getSpinnakerAccounts().orElse("").split(",") as List<String>

    def descriptions = []
    inputs.collectMany { Map<String, Map> input ->
      input.collect { String k, Map v ->
        def providerVersion = getOperationVersion(v)
        def converter = atomicOperationsRegistry.getAtomicOperationConverter(k, cloudProvider ?: v.cloudProvider, providerVersion)

        v = processDescriptionInput(atomicOperationDescriptionPreProcessors, converter, v)
        def description = converter.convertDescription(v)

        descriptions << description
        def errors = new DescriptionValidationErrors(description)

        def validator = atomicOperationsRegistry.getAtomicOperationDescriptionValidator(
          DescriptionValidator.getValidatorName(k), cloudProvider ?: v.cloudProvider, providerVersion
        )
        if (validator) {
          validator.validate(descriptions, description, errors)
        } else {
          log.warn(
            "No validator found for operation `${description?.class?.simpleName}` and cloud provider $cloudProvider"
          );
        }

        allowedAccountValidators.each {
          it.validate(username, allowedAccounts, description, errors)
        }

        descriptionAuthorizer.authorize(description, errors)

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

  private Map<String, String> start(List<AtomicOperation> atomicOperations, String key) {
    key = key ?: UUID.randomUUID().toString()
    Task task = orchestrationProcessor.process(atomicOperations, key)
    [id: task.id, resourceUri: "/task/${task.id}".toString()]
  }

  static Map processDescriptionInput(Collection<AtomicOperationDescriptionPreProcessor> descriptionPreProcessors,
                                     AtomicOperationConverter converter,
                                     Map descriptionInput) {
    def descriptionClass = converter.metaClass.methods.find { it.name == "convertDescription" }.returnType
    descriptionPreProcessors.findAll { it.supports(descriptionClass) }.each {
      descriptionInput = it.process(descriptionInput)
    }

    return descriptionInput
  }

  @Canonical
  static class AtomicOperationBindingResult {
    AtomicOperation atomicOperations
    Errors errors
  }
}
