/*
 * Copyright (c) 2017, 2018, Oracle Corporation and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the Apache License Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * If a copy of the Apache License Version 2.0 was not distributed with this file,
 * You can obtain one at https://www.apache.org/licenses/LICENSE-2.0.html
 */
package com.netflix.spinnaker.clouddriver.oracle.deploy.converter;

import com.netflix.spinnaker.clouddriver.oracle.OracleOperation;
import com.netflix.spinnaker.clouddriver.oracle.deploy.description.DeleteLoadBalancerDescription;
import com.netflix.spinnaker.clouddriver.oracle.deploy.op.DeleteOracleLoadBalancerAtomicOperation;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperations;
import com.netflix.spinnaker.clouddriver.security.AbstractAtomicOperationsCredentialsSupport;
import com.netflix.spinnaker.clouddriver.security.ProviderVersion;
import groovy.util.logging.Slf4j;
import java.util.Map;
import org.springframework.stereotype.Component;

@Slf4j
@OracleOperation(AtomicOperations.DELETE_LOAD_BALANCER)
@Component("deleteOracleLoadBalancerDescription")
public class DeleteOracleLoadBalancerAtomicOperationConverter extends AbstractAtomicOperationsCredentialsSupport {

  @SuppressWarnings("rawtypes")
  @Override
  public AtomicOperation convertOperation(Map input) {
    return new DeleteOracleLoadBalancerAtomicOperation(convertDescription(input));
  }

  @SuppressWarnings("rawtypes")
  @Override
  public DeleteLoadBalancerDescription convertDescription(Map input) {
    return OracleAtomicOperationConverterHelper.convertDescription(input, this, DeleteLoadBalancerDescription.class);
  }

  @Override
  public boolean acceptsVersion(ProviderVersion version) {
    return version == ProviderVersion.v1;
  }
}
