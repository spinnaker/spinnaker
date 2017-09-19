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

package com.netflix.spinnaker.clouddriver.kubernetes.v1.deploy.converters

import com.fasterxml.jackson.databind.DeserializationFeature
import com.netflix.spinnaker.clouddriver.kubernetes.v1.deploy.description.KubernetesAtomicOperationDescription
import com.netflix.spinnaker.clouddriver.kubernetes.security.KubernetesNamedAccountCredentials
import com.netflix.spinnaker.clouddriver.security.AbstractAtomicOperationsCredentialsSupport

class KubernetesAtomicOperationConverterHelper {
  static Object convertDescription(Map input,
                                   AbstractAtomicOperationsCredentialsSupport credentialsSupport,
                                   Class targetDescriptionType) {
    def account = input.account as String
    def removedAccount = input.remove('credentials')
    account = account ?: removedAccount

    // Save these to re-assign after ObjectMapper does its work.
    def credentials = (KubernetesNamedAccountCredentials) credentialsSupport.getCredentialsObject(account as String)

    def converted = (KubernetesAtomicOperationDescription) credentialsSupport.objectMapper
      .copy()
      .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
      .convertValue(input, targetDescriptionType)

    // Re-assign the credentials.
    converted.credentials = credentials
    if (removedAccount) {
      input.credentials = removedAccount
      converted.account = removedAccount
    }

    converted
  }
}
