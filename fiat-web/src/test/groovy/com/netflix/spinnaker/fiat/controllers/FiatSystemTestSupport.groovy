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

package com.netflix.spinnaker.fiat.controllers

import com.netflix.spinnaker.fiat.model.resources.Account
import com.netflix.spinnaker.fiat.model.resources.Application
import com.netflix.spinnaker.fiat.model.resources.Role
import com.netflix.spinnaker.fiat.model.resources.ServiceAccount

class FiatSystemTestSupport {
  Role roleA = new Role("roleA")
  Role roleB = new Role("roleB").setSource(Role.Source.EXTERNAL)

  Application unrestrictedApp = new Application().setName("unrestrictedApp")
  Application restrictedApp = new Application().setName("restrictedApp")
                                               .setRequiredGroupMembership([roleA.name])

  Account unrestrictedAccount = new Account().setName("unrestrictedAcct")
  Account restrictedAccount = new Account().setName("restrictedAcct")
                                           .setRequiredGroupMembership([roleB.name])

  ServiceAccount serviceAccount = new ServiceAccount().setName("svcAcct@group.com")
  Role roleServiceAccount = new Role(serviceAccount.requiredGroupMembership.first())
}
