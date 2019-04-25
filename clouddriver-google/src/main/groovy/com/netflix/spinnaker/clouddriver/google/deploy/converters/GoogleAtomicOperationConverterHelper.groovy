/*
 * Copyright 2015 Google, Inc.
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

package com.netflix.spinnaker.clouddriver.google.deploy.converters

import com.fasterxml.jackson.databind.DeserializationFeature
import com.netflix.spinnaker.clouddriver.google.security.GoogleNamedAccountCredentials
import com.netflix.spinnaker.clouddriver.security.AbstractAtomicOperationsCredentialsSupport
import com.netflix.spinnaker.clouddriver.security.resources.CredentialsNameable

class GoogleAtomicOperationConverterHelper {
  static <T extends CredentialsNameable> T convertDescription(Map input,
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
    converted.credentials = credentials in GoogleNamedAccountCredentials ? credentials : null
    converted
  }
}
