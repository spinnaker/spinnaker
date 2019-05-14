/*
 * Copyright (c) 2017, 2018, Oracle Corporation and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the Apache License Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * If a copy of the Apache License Version 2.0 was not distributed with this file,
 * You can obtain one at https://www.apache.org/licenses/LICENSE-2.0.html
 */
package com.netflix.spinnaker.clouddriver.oracle.deploy.validator;

import com.netflix.spinnaker.clouddriver.oracle.OracleOperation;
import com.netflix.spinnaker.clouddriver.oracle.deploy.description.UpsertLoadBalancerDescription;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperations;
import com.netflix.spinnaker.clouddriver.security.ProviderVersion;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.stereotype.Component;
import org.springframework.validation.Errors;

@OracleOperation(AtomicOperations.UPSERT_LOAD_BALANCER)
@Component("upsertLoadBalancerDescriptionValidator")
class UpsertLoadBalancerDescriptionValidator
    extends StandardOracleAttributeValidator<UpsertLoadBalancerDescription> {

  Set<String> validShapes = Stream.of("100Mbps", "400Mbps", "8000Mbps").collect(Collectors.toSet());

  @SuppressWarnings("rawtypes")
  @Override
  public void validate(
      List priorDescriptions, UpsertLoadBalancerDescription description, Errors errors) {
    context = "upsertLoadBalancerDescriptionValidator";
    validateNotEmptyString(errors, description.getApplication(), "application");
    if (description.getLoadBalancerId() == null) {
      validateNotEmptyString(errors, description.getShape(), "shape");
      if (!validShapes.contains(description.getShape())) {
        errors.rejectValue("${context}.shape", "${context}.shape.invalidLoadBalancerShape");
      }
      if (!description.getIsPrivate() && description.getSubnetIds().size() <= 1) {
        errors.rejectValue(
            "${context}.subnetIds", "${context}.subnetIds.publicLoadBalancerRequiresTwoSubnets");
      }
    }
    if (description.getCertificates() != null) {
      description
          .getCertificates()
          .forEach(
              (name, certificate) -> {
                // existing cert sends only the certificateName
                validateNotEmptyString(
                    errors, certificate.getCertificateName(), "certificate.certificateName");
                if (certificate.getPublicCertificate() != null) {
                  validateNotEmptyString(
                      errors, certificate.getPrivateKey(), "certificate.privateKey");
                  validateNotEmptyString(
                      errors, certificate.getPublicCertificate(), "certificate.publicCertificate");
                }
              });
    }
    if (description.getBackendSets() != null) {
      description
          .getBackendSets()
          .forEach(
              (name, backendSet) -> {
                validateLimit(errors, name, 32, "backendSet.name");
                validateNotNull(errors, backendSet.getHealthChecker(), "backendSet.healthChecker");
                validateNotEmptyString(errors, backendSet.getPolicy(), "backendSet.policy");
                if (backendSet.getHealthChecker() != null) {
                  validateNotEmptyString(
                      errors,
                      backendSet.getHealthChecker().getProtocol(),
                      "backendSet.healthChecker.protocol");
                  validateNotNull(
                      errors,
                      backendSet.getHealthChecker().getPort(),
                      "backendSet.healthChecker.port");
                  validateNotEmptyString(
                      errors,
                      backendSet.getHealthChecker().getUrlPath(),
                      "backendSet.healthChecker.urlPath");
                }
              });
    }
    if (description.getListeners() != null) {
      description
          .getListeners()
          .forEach(
              (name, listener) -> {
                validateNotEmptyString(
                    errors, listener.getDefaultBackendSetName(), "listener.defaultBackendSetName");
                validateNotEmptyString(errors, listener.getProtocol(), "listener.protocol");
                validateNotNull(errors, listener.getPort(), "listener.port");
              });
    }
  }

  @Override
  public boolean acceptsVersion(ProviderVersion version) {
    return version == ProviderVersion.v1;
  }
}
