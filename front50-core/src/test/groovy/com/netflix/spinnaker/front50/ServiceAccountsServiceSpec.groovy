/*
 * Copyright 2020 Adevinta
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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

package com.netflix.spinnaker.front50

import com.netflix.spinnaker.fiat.shared.FiatClientConfigurationProperties
import com.netflix.spinnaker.fiat.shared.FiatPermissionEvaluator
import com.netflix.spinnaker.fiat.shared.FiatService
import com.netflix.spinnaker.front50.config.FiatConfigurationProperties
import com.netflix.spinnaker.kork.web.exceptions.NotFoundException
import com.netflix.spinnaker.front50.model.serviceaccount.ServiceAccount
import com.netflix.spinnaker.front50.model.serviceaccount.ServiceAccountDAO
import org.springframework.security.core.Authentication
import org.springframework.security.core.context.SecurityContext
import org.springframework.security.core.context.SecurityContextHolder
import spock.lang.Specification
import spock.lang.Subject

class ServiceAccountsServiceSpec extends Specification {
  ServiceAccountDAO serviceAccountDAO = Mock(ServiceAccountDAO)
  FiatService fiatService = Mock(FiatService)
  FiatClientConfigurationProperties fiatClientConfigurationProperties = Mock(FiatClientConfigurationProperties) {
    isEnabled() >> true
  }
  FiatConfigurationProperties fiatConfigurationProperties = Mock(FiatConfigurationProperties) {
    getRoleSync() >> Mock(FiatConfigurationProperties.RoleSyncConfigurationProperties) {
      isEnabled() >> true
    }
  }
  FiatPermissionEvaluator fiatPermissionsEvaluator = Mock(FiatPermissionEvaluator)

  @Subject
  def service = new ServiceAccountsService(
    serviceAccountDAO,
    Optional.of(fiatService),
    fiatClientConfigurationProperties,
    fiatConfigurationProperties,
    fiatPermissionsEvaluator
  )

  def "should invalidate local cache"() {
    given:
    def serviceAccount = new ServiceAccount(
      name: "test-svc-acct",
      memberOf: [
        "test-role"
      ]
    )
    def authentication = Mock(Authentication) {
      getPrincipal() >> (Object)"principal"
    }
    def securityContext = Mock(SecurityContext) {
      getAuthentication() >> authentication
    }
    SecurityContextHolder.setContext(securityContext)
    when:
    serviceAccountDAO.create(serviceAccount.id, serviceAccount) >> serviceAccount
    service.createServiceAccount(serviceAccount)

    then:
    1 * fiatPermissionsEvaluator.invalidatePermission(_)
    1 * fiatService.sync(["test-role"])
  }

  def "deleting multiple service account should call sync once"() {
    given:
    def serviceAccounts = [
      new ServiceAccount(
        name: "test-svc-acct-1",
        memberOf: [
          "test-role-1"
        ]
      ),
      new ServiceAccount(
        name: "test-svc-acct-2",
        memberOf: [
          "test-role-2"
        ]
      )]
    when:
    service.deleteServiceAccounts(serviceAccounts)

    then:
    1 * serviceAccountDAO.delete("test-svc-acct-1")
    1 * serviceAccountDAO.delete("test-svc-acct-2")
    1 * fiatService.sync(['test-role-1', 'test-role-2'])
  }

  def "unknown managed service accounts should not throw exception"() {
    given:
    def prefixes = ["test-1", "test-2"]
    def test1ServiceAccount = new ServiceAccount(
      name: "test-1@managed-service-account"
    )
    def test2ServiceAccount = new ServiceAccount(
      name: "test-2@managed-service-account"
    )
    when:
    service.deleteManagedServiceAccounts(prefixes)

    then:
    1 * serviceAccountDAO.findById(test1ServiceAccount.id) >> test1ServiceAccount
    1 * serviceAccountDAO.findById(test2ServiceAccount.id) >> { throw new NotFoundException(test2ServiceAccount.id) }
    1 * serviceAccountDAO.delete(test1ServiceAccount.id)
    0 * serviceAccountDAO.delete(test2ServiceAccount.id)
  }
}
