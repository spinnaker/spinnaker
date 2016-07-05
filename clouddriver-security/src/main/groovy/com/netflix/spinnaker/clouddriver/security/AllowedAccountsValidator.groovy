/*
 * Copyright 2016 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.security

import org.springframework.validation.Errors

interface AllowedAccountsValidator {
  /**
   * Verify that <code>user</code> is allowed to access the account associated with <code>description</code>.
   *
   * If not authorized, an appropriate rejection should be added to <code>errors</code>.
   */
  void validate(String user, Collection<String> allowedAccounts, Object description, Errors errors)
}
