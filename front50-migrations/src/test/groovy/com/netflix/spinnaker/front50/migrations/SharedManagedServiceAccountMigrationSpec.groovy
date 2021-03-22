/*
 * Copyright 2021 Netflix, Inc.
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
 *
 */

package com.netflix.spinnaker.front50.migrations

import com.netflix.spinnaker.front50.model.pipeline.Pipeline
import com.netflix.spinnaker.front50.model.pipeline.PipelineDAO
import com.netflix.spinnaker.front50.model.serviceaccount.ServiceAccount
import com.netflix.spinnaker.front50.model.serviceaccount.ServiceAccountDAO
import spock.lang.Specification
import spock.lang.Subject

class SharedManagedServiceAccountMigrationSpec extends Specification {
  PipelineDAO pipelineDAO = Mock()
  ServiceAccountDAO serviceAccountDAO = Mock()

  @Subject
  def migration = new SharedManagedServiceAccountsMigration(pipelineDAO, serviceAccountDAO);

  def "should migrate pipeline if using regular managed service account"() {
    given:
    def pipeline = new Pipeline([
      application: "test",
      id: "some-guid",
      name: "My Pipeline",
      roles: [
        "test-pipeline-role"
      ],
      triggers: [
        [
          enabled: true,
          job: "org/repo/master",
          master: "travis",
          runAsUser: "some-guid@managed-service-account",
          type: "travis"
        ], [
          enabled: true,
          job: "org/repo2/master",
          master: "jenkins",
          runAsUser: "some-guid@managed-service-account",
          type: "jenkins"
        ]
      ]
    ])

    def serviceAccount = new ServiceAccount(
      name: "some-guid@managed-service-account",
      memberOf: ["test-trigger-role"]
    )

    def expectedSharedManagedServiceAccountName = "541d1b1224fa0834144513243f4e02289e8d1bbbced8b652cd29f9ba9ca7b88b@shared-managed-service-account"

    when:
    migration.run()

    then:
    1 * serviceAccountDAO.all() >> [serviceAccount]
    1 * pipelineDAO.all() >> { return [pipeline] }
    1 * pipelineDAO.update("some-guid", pipeline)
    1 * serviceAccountDAO.create(expectedSharedManagedServiceAccountName, _)

    pipeline.roles == ["test-pipeline-role", "test-trigger-role"]
    pipeline.getTriggers()*.get("runAsUser") == [
      expectedSharedManagedServiceAccountName,
      expectedSharedManagedServiceAccountName]
  }

  def "should not migrate pipeline if using regular manual service account"() {
    given:
    def pipeline = new Pipeline([
      application: "test",
      id: "some-guid",
      name: "My Pipeline",
      roles: [
        "test-pipeline-role"
      ],
      triggers: [
        [
          enabled: true,
          job: "org/repo/master",
          master: "travis",
          runAsUser: "some-user@some-org",
          type: "travis"
        ], [
          enabled: true,
          job: "org/repo2/master",
          master: "jenkins",
          runAsUser: "some-user@some-org",
          type: "jenkins"
        ]
      ]
    ])

    def serviceAccount = new ServiceAccount(
      name: "some-user@some-org",
      memberOf: ["test-trigger-role"]
    )

    when:
    migration.run()

    then:
    1 * serviceAccountDAO.all() >> [serviceAccount]
    1 * pipelineDAO.all() >> { return [pipeline] }
    0 * pipelineDAO.update(_, _)
    0 * serviceAccountDAO.create(_, _)

    pipeline.roles == ["test-pipeline-role"]
    pipeline.getTriggers()*.get("runAsUser") == [
      "some-user@some-org",
      "some-user@some-org"]
  }

  def "should not migrate pipeline if already using shared managed service account"() {
    given:
    def expectedSharedManagedServiceAccountName = "541d1b1224fa0834144513243f4e02289e8d1bbbced8b652cd29f9ba9ca7b88b@shared-managed-service-account"

    def pipeline = new Pipeline([
      application: "test",
      id: "some-guid",
      name: "My Pipeline",
      roles: [
        "test-pipeline-role", "test-trigger-role"
      ],
      "runAsUser": expectedSharedManagedServiceAccountName,
      triggers: [
        [
          enabled: true,
          job: "org/repo/master",
          master: "travis",
          runAsUser: expectedSharedManagedServiceAccountName,
          type: "travis"
        ], [
          enabled: true,
          job: "org/repo2/master",
          master: "jenkins",
          runAsUser: expectedSharedManagedServiceAccountName,
          type: "jenkins"
        ]
      ]
    ])

    def serviceAccount = new ServiceAccount(
      name: expectedSharedManagedServiceAccountName,
      memberOf: ["test-pipeline-role", "test-trigger-role"]
    )

    when:
    migration.run()

    then:
    1 * serviceAccountDAO.all() >> [serviceAccount]
    1 * pipelineDAO.all() >> { return [pipeline] }
    0 * pipelineDAO.update(_, _)
    0 * serviceAccountDAO.create(_, _)

    pipeline.roles == ["test-pipeline-role", "test-trigger-role"]
    pipeline.getTriggers()*.get("runAsUser") == [
      expectedSharedManagedServiceAccountName,
      expectedSharedManagedServiceAccountName]
  }
}
