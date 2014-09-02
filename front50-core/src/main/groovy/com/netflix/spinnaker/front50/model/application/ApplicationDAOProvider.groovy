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

package com.netflix.spinnaker.front50.model.application

import com.netflix.spinnaker.amos.AccountCredentials

/**
 * A factory for retrieving {@link ApplicationDAO} instances for a provided account
 *
 * @author Dan Woods
 */
interface ApplicationDAOProvider<T extends AccountCredentials> {

  /**
   * Checks if the credentials class is support for the implementation. Different providers may need different properties from their corresponding {@link AccountCredentials} objects
   *
   * @param credentialsClass
   * @return true/false
   */
  boolean supports(Class<?> credentialsClass)

  /**
   * This call should be preceded by a {@link #supports(java.lang.Class)} call. Implementations will need to ensure that the supports call and this call's AccountCredentials types align.
   *
   * @param credentials
   * @return
   */
  ApplicationDAO getForAccount(T credentials)
}
