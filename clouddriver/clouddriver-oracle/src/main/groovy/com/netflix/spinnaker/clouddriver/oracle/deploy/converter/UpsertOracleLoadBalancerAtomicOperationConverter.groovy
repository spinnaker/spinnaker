/*
 * Copyright (c) 2017 Oracle America, Inc.
 *
 * The contents of this file are subject to the Apache License Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * If a copy of the Apache License Version 2.0 was not distributed with this file,
 * You can obtain one at https://www.apache.org/licenses/LICENSE-2.0.html
 */
package com.netflix.spinnaker.clouddriver.oracle.deploy.converter

import com.netflix.spinnaker.clouddriver.oracle.OracleOperation
import com.netflix.spinnaker.clouddriver.oracle.deploy.description.UpsertLoadBalancerDescription
import com.netflix.spinnaker.clouddriver.oracle.deploy.op.UpsertOracleLoadBalancerAtomicOperation
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperations
import com.netflix.spinnaker.clouddriver.security.AbstractAtomicOperationsCredentialsSupport
import groovy.util.logging.Slf4j
import org.springframework.stereotype.Component

@Slf4j
@OracleOperation(AtomicOperations.UPSERT_LOAD_BALANCER)
@Component("upsertOracleLoadBalancerDescription")
class UpsertOracleLoadBalancerAtomicOperationConverter extends AbstractAtomicOperationsCredentialsSupport {

  @Override
  AtomicOperation convertOperation(Map input) {
    new UpsertOracleLoadBalancerAtomicOperation(convertDescription(input))
  }

  @Override
  UpsertLoadBalancerDescription convertDescription(Map input) {
    OracleAtomicOperationConverterHelper.convertDescription(input, this, UpsertLoadBalancerDescription)
  }
}
