/*
 * Copyright (c) 2017 Oracle America, Inc.
 *
 * The contents of this file are subject to the Apache License Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * If a copy of the Apache License Version 2.0 was not distributed with this file,
 * You can obtain one at https://www.apache.org/licenses/LICENSE-2.0.html
 */
package com.netflix.spinnaker.clouddriver.oraclebmcs.deploy.op

import com.netflix.spinnaker.clouddriver.oraclebmcs.deploy.description.EnableDisableOracleBMCSServerGroupDescription

class EnableOracleBMCSServerGroupAtomicOperation extends AbstractEnableDisableAtomicOperation {

  final String phaseName = "ENABLE_SERVER_GROUP"

  EnableOracleBMCSServerGroupAtomicOperation(EnableDisableOracleBMCSServerGroupDescription description) {
    super(description)
  }

  @Override
  boolean isDisable() {
    false
  }
}
