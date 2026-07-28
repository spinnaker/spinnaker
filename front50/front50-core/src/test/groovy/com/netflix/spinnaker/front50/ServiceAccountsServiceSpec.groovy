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

import com.netflix.spinnaker.kork.web.exceptions.NotFoundException
import com.netflix.spinnaker.front50.model.serviceaccount.ServiceAccount
import com.netflix.spinnaker.front50.model.serviceaccount.ServiceAccountDAO
import spock.lang.Specification
import spock.lang.Subject

class ServiceAccountsServiceSpec extends Specification {
  ServiceAccountDAO serviceAccountDAO = Mock(ServiceAccountDAO)

  @Subject
  def service = new ServiceAccountsService(serviceAccountDAO)

  def "creating a service account persists it to the owner-local store"() {
    given:
    def serviceAccount = new ServiceAccount(
      name: "test-svc-acct",
      memberOf: [
        "test-role"
      ]
    )

    when:
    service.createServiceAccount(serviceAccount)

    then:
    // Owner-local enforcement: Front50 owns service-account ACLs. The run-as minter resolves
    // roles from this store at mint time, so there is no external service to sync to.
    1 * serviceAccountDAO.create(serviceAccount.id, serviceAccount) >> serviceAccount
    0 * _
  }

  def "deleting multiple service accounts removes each from the local store"() {
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
    0 * _
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
