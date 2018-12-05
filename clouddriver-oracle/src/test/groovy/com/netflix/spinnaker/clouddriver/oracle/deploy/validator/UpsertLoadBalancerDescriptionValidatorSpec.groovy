/*
 * Copyright (c) 2017, 2018, Oracle Corporation and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the Apache License Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * If a copy of the Apache License Version 2.0 was not distributed with this file,
 * You can obtain one at https://www.apache.org/licenses/LICENSE-2.0.html
 */
package com.netflix.spinnaker.clouddriver.oracle.deploy.validator

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.oracle.deploy.converter.UpsertOracleLoadBalancerAtomicOperationConverter
import com.netflix.spinnaker.clouddriver.oracle.deploy.description.UpsertLoadBalancerDescription
import com.netflix.spinnaker.clouddriver.oracle.deploy.op.UpsertOracleLoadBalancerAtomicOperation
import com.netflix.spinnaker.clouddriver.oracle.deploy.OracleWorkRequestPoller
import com.netflix.spinnaker.clouddriver.oracle.security.OracleNamedAccountCredentials
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider
import com.oracle.bmc.loadbalancer.LoadBalancerClient
import com.oracle.bmc.loadbalancer.model.CreateLoadBalancerDetails
import com.oracle.bmc.loadbalancer.requests.CreateLoadBalancerRequest
import com.oracle.bmc.loadbalancer.responses.CreateLoadBalancerResponse
import org.springframework.validation.Errors
import spock.lang.Shared
import spock.lang.Specification

class UpsertLoadBalancerDescriptionValidatorSpec extends Specification {
  
  @Shared ObjectMapper mapper = new ObjectMapper()
  @Shared UpsertOracleLoadBalancerAtomicOperationConverter converter
  @Shared UpsertLoadBalancerDescriptionValidator validator
  @Shared String context = 'upsertLoadBalancerDescriptionValidator.'
  

  def setupSpec() {
    this.converter = new UpsertOracleLoadBalancerAtomicOperationConverter(objectMapper: mapper)
    converter.accountCredentialsProvider = Mock(AccountCredentialsProvider)
    converter.accountCredentialsProvider.getCredentials(_) >> Mock(OracleNamedAccountCredentials)
    validator = new UpsertLoadBalancerDescriptionValidator()
  }

  def "Create LoadBalancer with invalid Cert"() {
    setup:
    def req = read('createLoadBalancer_invalidCert.json')
    def desc = converter.convertDescription(req[0].upsertLoadBalancer)
    def errors = Mock(Errors)

    when:
    validator.validate([], desc, errors)

    then:
    2 * errors.rejectValue('certificate.privateKey', 
      context + 'certificate.privateKey.empty')
    2 * errors.rejectValue('certificate.certificateName', 
      context + 'certificate.certificateName.empty')
    1 * errors.rejectValue('certificate.publicCertificate', 
      context + 'certificate.publicCertificate.empty')
  }

  def "Create LoadBalancer with invalid Listener"() {
    setup:
    def req = read('createLoadBalancer_invalidListener.json')
    def desc = converter.convertDescription(req[0].upsertLoadBalancer)
    def errors = Mock(Errors)

    when:
    validator.validate([], desc, errors)

    then:
    3 * errors.rejectValue('listener.defaultBackendSetName', 
      context + 'listener.defaultBackendSetName.empty')
    2 * errors.rejectValue('listener.protocol', 
      context + 'listener.protocol.empty')
    1 * errors.rejectValue('listener.port', 
      context + 'listener.port.null')
  }
  
  def "Create LoadBalancer with invalid BackendSet"() {
    setup:
    def req = read('createLoadBalancer_invalidBackendSet.json')
    def desc = converter.convertDescription(req[0].upsertLoadBalancer)
    def errors = Mock(Errors)

    when:
    validator.validate([], desc, errors)

    then:
    2 * errors.rejectValue('backendSet.name', 
      context + 'backendSet.name.exceedsLimit')
    1 * errors.rejectValue('backendSet.healthChecker', 
      context + 'backendSet.healthChecker.null')
    1 * errors.rejectValue('backendSet.policy', 
      context + 'backendSet.policy.empty')
    1 * errors.rejectValue('backendSet.healthChecker.protocol', 
      context + 'backendSet.healthChecker.protocol.empty')
    1 * errors.rejectValue('backendSet.healthChecker.urlPath', 
      context + 'backendSet.healthChecker.urlPath.empty')
    1 * errors.rejectValue('backendSet.healthChecker.port', 
      context + 'backendSet.healthChecker.port.null')
  }

  def read(String fileName) {
    def json = new File(getClass().getResource('/desc/' + fileName).toURI()).text
    List<Map<String, Object>> data = mapper.readValue(json, new TypeReference<List<Map<String, Object>>>(){});
    return data;
  }
}
