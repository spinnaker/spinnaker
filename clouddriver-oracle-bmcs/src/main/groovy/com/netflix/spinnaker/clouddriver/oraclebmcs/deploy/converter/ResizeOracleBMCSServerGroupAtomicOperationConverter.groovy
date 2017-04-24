/*
 * Copyright (c) 2017 Oracle America, Inc.
 *
 * The contents of this file are subject to the Apache License Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * If a copy of the Apache License Version 2.0 was not distributed with this file,
 * You can obtain one at https://www.apache.org/licenses/LICENSE-2.0.html
 */
package com.netflix.spinnaker.clouddriver.oraclebmcs.deploy.converter

import com.netflix.spinnaker.clouddriver.oraclebmcs.OracleBMCSOperation
import com.netflix.spinnaker.clouddriver.oraclebmcs.deploy.description.ResizeOracleBMCSServerGroupDescription
import com.netflix.spinnaker.clouddriver.oraclebmcs.deploy.op.ResizeOracleBMCSServerGroupAtomicOperation
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperations
import com.netflix.spinnaker.clouddriver.security.AbstractAtomicOperationsCredentialsSupport
import org.springframework.stereotype.Component

@OracleBMCSOperation(AtomicOperations.RESIZE_SERVER_GROUP)
@Component("resizeOracleBMCSServerGroupDescription")
class ResizeOracleBMCSServerGroupAtomicOperationConverter extends AbstractAtomicOperationsCredentialsSupport {

  @Override
  AtomicOperation convertOperation(Map input) {
    new ResizeOracleBMCSServerGroupAtomicOperation(convertDescription(input))
  }

  @Override
  ResizeOracleBMCSServerGroupDescription convertDescription(Map input) {
    OracleBMCSAtomicOperationConverterHelper.convertDescription(input, this, ResizeOracleBMCSServerGroupDescription)
  }
}
