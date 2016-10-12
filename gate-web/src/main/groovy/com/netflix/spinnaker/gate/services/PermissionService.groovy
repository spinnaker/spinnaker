/*
 * Copyright 2016 Google, Inc.
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

package com.netflix.spinnaker.gate.services

import com.netflix.spinnaker.config.FiatClientConfigurationProperties
import com.netflix.spinnaker.fiat.shared.FiatService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Component
class PermissionService {

  @Autowired
  FiatService fiatService

  @Autowired
  FiatClientConfigurationProperties fiatConfig

  void login(String userId) {
    if (fiatConfig.enabled) {
      fiatService.loginUser(userId, "")
    }
  }

  void loginSAML(String userId, Collection<String> roles) {
    if (fiatConfig.enabled) {
      fiatService.loginSAMLUser(userId, roles)
    }
  }

  void logout(String userId) {
    if (fiatConfig.enabled) {
      fiatService.logoutUser(userId)
    }
  }

  void sync() {
    if (fiatConfig.enabled) {
      fiatService.sync()
    }
  }
}
