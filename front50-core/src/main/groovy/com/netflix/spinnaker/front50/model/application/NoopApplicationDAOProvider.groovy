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
import org.springframework.stereotype.Component

/**
 * A null operation implementation.
 */
@Component
class NoopApplicationDAOProvider implements ApplicationDAOProvider<AccountCredentials> {
  @Override
  boolean supports(Class credentialsClass) {
    return false
  }

  @Override
  ApplicationDAO getForAccount(AccountCredentials credentials) {
    return null
  }
}
