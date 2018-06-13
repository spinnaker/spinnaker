/*
 * Copyright (c) 2017 Oracle America, Inc.
 *
 * The contents of this file are subject to the Apache License Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * If a copy of the Apache License Version 2.0 was not distributed with this file,
 * You can obtain one at https://www.apache.org/licenses/LICENSE-2.0.html
 */
package com.netflix.spinnaker.clouddriver.oracle.deploy.converter

import com.netflix.spinnaker.clouddriver.deploy.DeployAtomicOperation
import com.netflix.spinnaker.clouddriver.oracle.OracleOperation
import com.netflix.spinnaker.clouddriver.oracle.deploy.description.BasicOracleDeployDescription
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperations
import com.netflix.spinnaker.clouddriver.security.AbstractAtomicOperationsCredentialsSupport
import org.springframework.stereotype.Component

@OracleOperation(AtomicOperations.CREATE_SERVER_GROUP)
@Component("basicOracleDeployDescription")
class BasicOracleDeployAtomicOperationConverter extends AbstractAtomicOperationsCredentialsSupport {

  AtomicOperation convertOperation(Map input) {
    new DeployAtomicOperation(convertDescription(input))
  }

  BasicOracleDeployDescription convertDescription(Map input) {
    OracleAtomicOperationConverterHelper.convertDescription(input, this, BasicOracleDeployDescription)
  }
}
