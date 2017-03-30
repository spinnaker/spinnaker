/*
 * Copyright (c) 2017 Oracle America, Inc.
 *
 * The contents of this file are subject to the Apache License Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * If a copy of the Apache License Version 2.0 was not distributed with this file,
 * You can obtain one at https://www.apache.org/licenses/LICENSE-2.0.html
 */
package com.netflix.spinnaker.clouddriver.oraclebmcs

import com.netflix.spinnaker.clouddriver.core.CloudProvider
import org.springframework.stereotype.Component

import java.lang.annotation.Annotation

/**
 * OracleBMCS declaration as a {@link CloudProvider}.
 */
@Component
class OracleBMCSCloudProvider implements CloudProvider {

  static final String ID = "oraclebmcs"
  final String id = ID
  final String displayName = ID
  final Class<Annotation> operationAnnotationType = OracleBMCSOperation
}
