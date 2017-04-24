/*
 * Copyright (c) 2017 Oracle America, Inc.
 *
 * The contents of this file are subject to the Apache License Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * If a copy of the Apache License Version 2.0 was not distributed with this file,
 * You can obtain one at https://www.apache.org/licenses/LICENSE-2.0.html
 */
package com.netflix.spinnaker.clouddriver.oraclebmcs.deploy.converter

import com.fasterxml.jackson.databind.DeserializationFeature
import com.netflix.spinnaker.clouddriver.oraclebmcs.security.OracleBMCSNamedAccountCredentials
import com.netflix.spinnaker.clouddriver.security.AbstractAtomicOperationsCredentialsSupport

class OracleBMCSAtomicOperationConverterHelper {

  static <T> T convertDescription(Map input,
                                  AbstractAtomicOperationsCredentialsSupport credentialsSupport,
                                  Class<T> targetDescriptionType) {
    if (!input.accountName) {
      input.accountName = input.credentials
    }

    if (input.accountName) {
      input.credentials = credentialsSupport.getCredentialsObject(input.accountName as String)
    }

    // Save these to re-assign after ObjectMapper does its work.
    def credentials = input.remove("credentials")

    def converted = credentialsSupport.objectMapper
      .copy()
      .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
      .convertValue(input, targetDescriptionType)

    // Re-assign the credentials.
    converted.credentials = credentials in OracleBMCSNamedAccountCredentials ? credentials : null
    return converted
  }
}
