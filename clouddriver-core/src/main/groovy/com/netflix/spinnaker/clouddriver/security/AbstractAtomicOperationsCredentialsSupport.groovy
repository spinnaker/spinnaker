/*
 * Copyright 2014 Netflix, Inc.
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

package com.netflix.spinnaker.clouddriver.security

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperationConverter
import com.netflix.spinnaker.kork.web.exceptions.InvalidRequestException
import org.springframework.beans.factory.annotation.Autowired

abstract class AbstractAtomicOperationsCredentialsSupport implements AtomicOperationConverter {
  @Autowired
  AccountCredentialsProvider accountCredentialsProvider

  ObjectMapper objectMapper

  @Autowired
  public void setObjectMapper(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper
            .configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false)
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

  }

  def <T extends AccountCredentials> T getCredentialsObject(String name) {
    if (name == null) {
      throw new InvalidRequestException("credential name is required")
    }
    T credential
    try {
      def repoCredential = accountCredentialsProvider.getCredentials(name)
      if (repoCredential == null) {
        throw new NullPointerException()
      }
      credential = (T) repoCredential
    } catch (Exception e) {
      throw new InvalidRequestException("credential not found (name: ${name}, names: ${accountCredentialsProvider.getAll()*.name})", e)
    }

    return credential
  }
}
