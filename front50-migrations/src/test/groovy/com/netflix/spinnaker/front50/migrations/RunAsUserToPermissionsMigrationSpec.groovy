/*
 * Copyright (c) 2019 Schibsted Media Group. All rights reserved
 */

package com.netflix.spinnaker.front50.migrations

import com.netflix.spinnaker.front50.model.pipeline.Pipeline
import com.netflix.spinnaker.front50.model.pipeline.PipelineDAO
import com.netflix.spinnaker.front50.model.serviceaccount.ServiceAccount
import com.netflix.spinnaker.front50.model.serviceaccount.ServiceAccountDAO
import spock.lang.Specification
import spock.lang.Subject

class RunAsUserToPermissionsMigrationSpec extends Specification {
  PipelineDAO pipelineDAO = Mock()
  ServiceAccountDAO serviceAccountDAO = Mock()

  @Subject
  def migration = new RunAsUserToPermissionsMigration(pipelineDAO, serviceAccountDAO)

  def "should migrate pipeline if one trigger is missing automatic service user"() {
    given:
    def pipeline = new Pipeline([
      application: "test",
      id: "1337",
      name: "My Pipeline",
      roles: [
        "My-Role"
      ],
      triggers: [
        [
          enabled: true,
          job: "org/repo/master",
          master: "travis",
          runAsUser: "my-existing-service-user@org.com",
          type: "travis"
        ], [
          enabled: true,
          job: "org/repo2/master",
          master: "jenkins",
          runAsUser: "1337@managed-service-account",
          type: "jenkins"
        ]
      ]
    ])

    def serviceAccount = new ServiceAccount(
      name: "my-existing-service-user@org.com",
      memberOf: ["Another-Role"]
    )

    when:
    migration.run()

    then:
    1 * serviceAccountDAO.all() >> [serviceAccount]
    1 * pipelineDAO.all() >> { return [pipeline] }
    1 * pipelineDAO.update("1337", pipeline)
    1 * serviceAccountDAO.create("1337@managed-service-account", _)

    pipeline.roles == ["my-role", "another-role"]
    pipeline.getTriggers()*.get("runAsUser") == ["1337@managed-service-account", "1337@managed-service-account"]
  }

  def "should not migrate a pipeline with an automatic service user already set"() {
    given:
    def pipeline = new Pipeline([
      application: "test",
      id: "1337",
      name: "My Pipeline",
      triggers: [
        [
          enabled: true,
          job: "org/repo/master",
          master: "travis",
          runAsUser: "1337@managed-service-account",
          type: "travis"
        ]
      ]
    ])

    when:
    migration.run()

    then:
    1 * serviceAccountDAO.all() >> []
    1 * pipelineDAO.all() >> { return [pipeline] }
    0 * pipelineDAO.update(_, _)
    0 * serviceAccountDAO.create(_, _)
  }

  def "should not migrate a pipeline with neither roles or runAsUser set"() {
    given:
    def pipeline = new Pipeline([
      application: "test",
      id: "1337",
      name: "My Pipeline",
      roles: [
      ],
      triggers: [
        [
          enabled: true,
          job: "org/repo/master",
          master: "travis",
          type: "travis"
        ]
      ]
    ])

    when:
    migration.run()

    then:
    1 * serviceAccountDAO.all() >> []
    1 * pipelineDAO.all() >> { return [pipeline] }
    0 * pipelineDAO.update(_, _)
    0 * serviceAccountDAO.create(_, _)
  }

  def "should migrate pipeline with a simple trigger"() {
    given:
    def pipeline = new Pipeline([
      application: "test",
      id: "1337",
      name: "My Pipeline",
      roles: [
      ],
      triggers: [
        [
          enabled: true,
          job: "org/repo/master",
          master: "travis",
          runAsUser: "my-existing-service-user@org.com",
          type: "travis"
        ]
      ]
    ])
    def serviceAccount = new ServiceAccount(
      name: "my-existing-service-user@org.com",
      memberOf: ["my-role", "another-role"]
    )

    when:
    migration.run()

    then:
    1 * serviceAccountDAO.all() >> [serviceAccount]
    1 * pipelineDAO.all() >> { return [pipeline] }
    1 * pipelineDAO.update("1337", pipeline)
    1 * serviceAccountDAO.create("1337@managed-service-account", _)

    pipeline.roles == ["my-role", "another-role"]
    pipeline.getTriggers()*.get("runAsUser") == ["1337@managed-service-account"]
  }

  def "should migrate pipeline with multiple triggers"() {
    given:
    def pipeline = new Pipeline([
      application: "test",
      id: "1337",
      name: "My Pipeline",
      roles: [],
      triggers: [
        [
          enabled: true,
          job: "org/repo/master",
          master: "travis",
          runAsUser: "my-existing-service-user@org.com",
          type: "travis"
        ], [
          enabled: true,
          job: "org/repo/master",
          master: "jenkins",
          type: "jenkins"
        ]
      ]
    ])
    def serviceAccount = new ServiceAccount(
      name: "my-existing-service-user@org.com",
      memberOf: ["my-role", "another-role"]
    )

    when:
    migration.run()

    then:
    1 * serviceAccountDAO.all() >> [serviceAccount]
    1 * pipelineDAO.all() >> { return [pipeline] }
    1 * pipelineDAO.update("1337", pipeline)
    1 * serviceAccountDAO.create("1337@managed-service-account", _)

    pipeline.roles == ["my-role", "another-role"]
    pipeline.getTriggers()*.get("runAsUser") == ["1337@managed-service-account", "1337@managed-service-account"]
  }

  def "should migrate pipeline with multiple triggers with different service accounts"() {
    given:
    def pipeline = new Pipeline([
      application: "test",
      id: "1337",
      name: "My Pipeline",
      roles: [],
      triggers: [
        [
          enabled: true,
          job: "org/repo/master",
          master: "travis",
          runAsUser: "my-existing-service-user@org.com",
          type: "travis"
        ], [
        enabled: true,
        job: "org/repo/master",
        master: "jenkins",
        runAsUser: "another-existing-service-user@org.com",
        type: "jenkins"
      ]
      ]
    ])
    def travisServiceAccount = new ServiceAccount(
      name: "my-existing-service-user@org.com",
      memberOf: ["my-role", "another-role"]
    )
    def jenkinsServiceAccount = new ServiceAccount(
      name: "another-existing-service-user@org.com",
      memberOf: ["foo", "bar"]
    )

    when:
    migration.run()

    then:
    1 * serviceAccountDAO.all() >> [travisServiceAccount, jenkinsServiceAccount]
    1 * pipelineDAO.all() >> { return [pipeline] }
    1 * pipelineDAO.update("1337", pipeline)
    1 * serviceAccountDAO.create("1337@managed-service-account", _)

    pipeline.roles.sort() == ["my-role", "another-role", "foo", "bar"].sort()
    pipeline.getTriggers()*.get("runAsUser") == ["1337@managed-service-account", "1337@managed-service-account"]
  }

}
