/*
 * Copyright 2020 Schibsted ASA.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 *
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.front50.migrations

import com.netflix.spinnaker.front50.api.model.pipeline.Pipeline
import com.netflix.spinnaker.front50.api.model.pipeline.Trigger;
import com.netflix.spinnaker.front50.model.pipeline.PipelineDAO
import com.netflix.spinnaker.front50.model.serviceaccount.ServiceAccount
import com.netflix.spinnaker.front50.model.serviceaccount.ServiceAccountDAO
import spock.lang.Specification
import spock.lang.Subject

class DeleteDanglingServiceAccountsMigrationSpec extends Specification {
  PipelineDAO pipelineDAO = Mock()
  ServiceAccountDAO serviceAccountDAO = Mock()

  @Subject
  def migration = new DeleteDanglingServiceAccountsMigration(pipelineDAO, serviceAccountDAO, true, false)

  def "should delete service account not found in any triggers"() {
    given:
    def pipeline1 = new Pipeline([
      application: "test",
      id         : "1",
      name       : "My Pipeline 1",
      triggers   : [
        new Trigger([
          enabled  : true,
          job      : "org/repo/master",
          master   : "travis",
          runAsUser: "my-existing-service-user@org.com",
          type     : "travis"
        ]), new Trigger([
          enabled  : true,
          job      : "org/repo2/master",
          master   : "jenkins",
          runAsUser: "1@managed-service-account",
          type     : "jenkins"
        ]),
      ]
    ])

    def pipeline2 = new Pipeline([
      application: "test",
      id         : "2",
      name       : "My Pipeline 2",
      triggers   : [
        new Trigger([
          enabled  : true,
          job      : "org/repo/master",
          master   : "travis",
          runAsUser: "2@managed-service-account",
          type     : "travis"
        ]), new Trigger([
          enabled  : true,
          job      : "org/repo2/master",
          master   : "jenkins",
          runAsUser: "2@managed-service-account",
          type     : "jenkins"
        ]),
      ]
    ])

    def serviceAccount1 = new ServiceAccount(
      name: "my-existing-service-user@org.com"
    )

    def serviceAccount2 = new ServiceAccount(
      name: "1@managed-service-account"
    )

    def serviceAccount3 = new ServiceAccount(
      name: "2@managed-service-account"
    )
    def serviceAccount4 = new ServiceAccount(
      name: "3@managed-service-account"
    )
    def serviceAccount5 = new ServiceAccount(
      name: "another-existing-service-user@org.com"
    )
    def serviceAccount6 = new ServiceAccount(
      name: "shared-account@shared-managed-service-account"
    )

    when:
    migration.run()

    then:
    1 * serviceAccountDAO.all() >> [serviceAccount1, serviceAccount2, serviceAccount3, serviceAccount4, serviceAccount5]
    1 * pipelineDAO.all() >> [pipeline1, pipeline2]
    1 * serviceAccountDAO.delete("3@managed-service-account")
    0 * serviceAccountDAO.delete(_)
  }

  def "when deleteDanglingSharedManagedServiceAccounts enabled should delete dangling shared managed service account"() {
    given:
    def migration = new DeleteDanglingServiceAccountsMigration(pipelineDAO, serviceAccountDAO, false, true)

    def serviceAccountName = "test-managed-service-account@managed-service-account"
    def serviceAccount = new ServiceAccount(
      name: serviceAccountName
    )

    def sharedServiceAccountName = "test-shared-managed-service-account@shared-managed-service-account"
    def sharedServiceAccount = new ServiceAccount(
      name: sharedServiceAccountName
    )

    when:
    migration.run()

    then:
    1 * serviceAccountDAO.all() >> [serviceAccount, sharedServiceAccount]
    1 * pipelineDAO.all() >> []
    1 * serviceAccountDAO.delete(sharedServiceAccountName)
    0 * serviceAccountDAO.delete(_)
  }
}
