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


package com.netflix.spinnaker.kato.security

import com.netflix.spinnaker.amos.AccountCredentials
import com.netflix.spinnaker.amos.AccountCredentialsProvider
import com.netflix.spinnaker.kato.orchestration.AtomicOperationConverter
import groovy.transform.InheritConstructors
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.ResponseStatus

abstract class AbstractAtomicOperationsCredentialsSupport implements AtomicOperationConverter {
  @Autowired
  AccountCredentialsProvider accountCredentialsProvider

  def <T extends AccountCredentials> T getCredentialsObject(String name) {
    try {
      accountCredentialsProvider.getCredentials(name)
    } catch (Exception e) {
      throw new CredentialsNotFoundException(name)
    }
  }

  @ResponseStatus(value = HttpStatus.BAD_REQUEST, reason = "Credentials not found.")
  @InheritConstructors
  static class CredentialsNotFoundException extends RuntimeException {}
}
