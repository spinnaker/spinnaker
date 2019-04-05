/*
 * Copyright 2018 Schibsted ASA.
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

package com.netflix.spinnaker.front50.controllers

import com.netflix.spinnaker.fiat.shared.FiatClientConfigurationProperties
import com.netflix.spinnaker.fiat.shared.FiatPermissionEvaluator
import com.netflix.spinnaker.fiat.shared.FiatService
import com.netflix.spinnaker.front50.model.serviceaccount.ServiceAccount
import com.netflix.spinnaker.front50.model.serviceaccount.ServiceAccountDAO
import spock.lang.Specification
import spock.lang.Subject

class ServiceAccountsControllerSpec extends Specification {
  def serviceAccountDAO = Mock(ServiceAccountDAO)
  def fiatService = Mock(FiatService)
  def fiatClientConfigurationProperties = Mock(FiatClientConfigurationProperties)
  def fiatPermissionsEvaluator = Mock(FiatPermissionEvaluator)

  @Subject
  def controller = new ServiceAccountsController(
    serviceAccountDAO: serviceAccountDAO,
    fiatService: fiatService,
    fiatClientConfigurationProperties: fiatClientConfigurationProperties,
    fiatPermissionEvaluator: fiatPermissionsEvaluator,
    roleSync: true
  )

  def "should invalidate local cache"() {
    given:
    def serviceAccount = new ServiceAccount(
      name: "test-svc-acct",
      memberOf: [
        "test-role"
      ]
    )
    when:
    serviceAccountDAO.create(serviceAccount.id, serviceAccount) >> serviceAccount
    fiatClientConfigurationProperties.isEnabled() >> true
    controller.createServiceAccount(serviceAccount)

    then:
    1 * fiatPermissionsEvaluator.invalidatePermission(_)
    1 * fiatService.sync(["test-role"])
  }

}
