/*
 * Copyright 2016 Google, Inc.
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

package com.netflix.spinnaker.clouddriver.appengine.deploy.validators

import com.netflix.spinnaker.clouddriver.appengine.deploy.description.UpsertAppengineLoadBalancerDescription
import com.netflix.spinnaker.clouddriver.appengine.model.AppengineServerGroup
import com.netflix.spinnaker.clouddriver.appengine.model.AppengineTrafficSplit
import com.netflix.spinnaker.clouddriver.appengine.model.ShardBy
import com.netflix.spinnaker.clouddriver.appengine.provider.view.AppengineClusterProvider
import com.netflix.spinnaker.clouddriver.appengine.security.AppengineCredentials
import com.netflix.spinnaker.clouddriver.appengine.security.AppengineNamedAccountCredentials
import com.netflix.spinnaker.clouddriver.deploy.ValidationErrors
import com.netflix.spinnaker.clouddriver.security.DefaultAccountCredentialsProvider
import com.netflix.spinnaker.clouddriver.security.MapBackedAccountCredentialsRepository
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

class UpsertAppengineLoadBalancerDescriptionValidatorSpec extends Specification {
  private static final ACCOUNT_NAME = "my-appengine-account"
  private static final APPLICATION_NAME = "myapp"
  private static final REGION = "us-central"
  private static final LOAD_BALANCER_NAME = "default"
  private static final MIGRATE_TRAFFIC = false

  private static final SERVER_GROUP_NAME_1 = "app-stack-detail-v000"
  private static final SERVER_GROUP_1 = new AppengineServerGroup(
    name: SERVER_GROUP_NAME_1,
    loadBalancers: [LOAD_BALANCER_NAME]
  )

  private static final SERVER_GROUP_NAME_2 = "app-stack-detail-v001"
  private static final SERVER_GROUP_2 = new AppengineServerGroup(
    name: SERVER_GROUP_NAME_2,
    loadBalancers: [LOAD_BALANCER_NAME]
  )

  private static final SERVER_GROUP_NAME_3 = "allows-gradual-migration"
  private static final SERVER_GROUP_3 = new AppengineServerGroup(
    name: SERVER_GROUP_NAME_3,
    loadBalancers: [LOAD_BALANCER_NAME],
    allowsGradualTrafficMigration: true
  )

  @Shared
  UpsertAppengineLoadBalancerDescriptionValidator validator

  @Shared
  AppengineNamedAccountCredentials credentials

  void setupSpec() {
    validator = new UpsertAppengineLoadBalancerDescriptionValidator()

    def credentialsRepo = new MapBackedAccountCredentialsRepository()
    def mockCredentials = Mock(AppengineCredentials)
    credentials = new AppengineNamedAccountCredentials.Builder()
      .name(ACCOUNT_NAME)
      .region(REGION)
      .applicationName(APPLICATION_NAME)
      .credentials(mockCredentials)
      .build()
    credentialsRepo.save(ACCOUNT_NAME, credentials)

    validator.accountCredentialsProvider = new DefaultAccountCredentialsProvider(credentialsRepo)
    validator.appengineClusterProvider = Mock(AppengineClusterProvider)

    validator.appengineClusterProvider.getServerGroup(ACCOUNT_NAME, REGION, SERVER_GROUP_NAME_1) >> SERVER_GROUP_1
    validator.appengineClusterProvider.getServerGroup(ACCOUNT_NAME, REGION, SERVER_GROUP_NAME_2) >> SERVER_GROUP_2
    validator.appengineClusterProvider.getServerGroup(ACCOUNT_NAME, REGION, SERVER_GROUP_NAME_3) >> SERVER_GROUP_3
  }

  void "pass validation with proper description inputs"() {
    setup:
      def validSplit = new AppengineTrafficSplit(
        allocations: ["app-stack-detail-v000": 0.6, "app-stack-detail-v001": 0.4],
        shardBy: ShardBy.IP)
      def description = new UpsertAppengineLoadBalancerDescription(
        accountName: ACCOUNT_NAME,
        loadBalancerName: LOAD_BALANCER_NAME,
        split: validSplit,
        migrateTraffic: MIGRATE_TRAFFIC,
        credentials: credentials)
      def errors = Mock(ValidationErrors)

    when:
      validator.validate([], description, errors)

    then:
      0 * errors._
  }

  void "description with allocations that do not sum to 1 fails validation"() {
    setup:
      def validSplit = new AppengineTrafficSplit(
        allocations: ["app-stack-detail-v000": 0.7, "app-stack-detail-v001": 0.4],
        shardBy: ShardBy.IP)
      def description = new UpsertAppengineLoadBalancerDescription(
        accountName: ACCOUNT_NAME,
        loadBalancerName: LOAD_BALANCER_NAME,
        split: validSplit,
        migrateTraffic: MIGRATE_TRAFFIC,
        credentials: credentials)
      def errors = Mock(ValidationErrors)

    when:
      validator.validate([], description, errors)

    then:
      1 * errors.rejectValue('upsertAppengineLoadBalancerAtomicOperationDescription.split.allocations',
                             'upsertAppengineLoadBalancerAtomicOperationDescription.split.allocations.invalid (Allocations must sum to 1)')
  }

  @Unroll
  void "validates allowed inputs for gradual migration"() {
    setup:
      def description = new UpsertAppengineLoadBalancerDescription(
        accountName: ACCOUNT_NAME,
        loadBalancerName: LOAD_BALANCER_NAME,
        split: split,
        migrateTraffic: true,
        credentials: credentials
      )
      def errors = Mock(ValidationErrors)

    when:
      validator.validate([], description, errors)

    then:
      1 * errors.rejectValue(errorPrefix, errorMessage)

    where:
      split << [
        new AppengineTrafficSplit(allocations: ["app-stack-detail-v000": 0.5, "app-stack-detail-v001": 0.5], shardBy: ShardBy.IP),
        new AppengineTrafficSplit(allocations: [(SERVER_GROUP_NAME_1): 1], shardBy: ShardBy.IP),
        new AppengineTrafficSplit(shardBy: ShardBy.IP),
      ]

      errorPrefix << [
        'upsertAppengineLoadBalancerAtomicOperationDescription.migrateTraffic',
        'upsertAppengineLoadBalancerAtomicOperationDescription.migrateTraffic',
        'upsertAppengineLoadBalancerAtomicOperationDescription.split.allocations',
      ]

      errorMessage << [
        'upsertAppengineLoadBalancerAtomicOperationDescription.migrateTraffic.invalid (Cannot gradually migrate traffic to multiple server groups).',
        'upsertAppengineLoadBalancerAtomicOperationDescription.migrateTraffic.invalid (Cannot gradually migrate traffic to this server group.'
          + ' Gradual migration is allowed only for server groups in the App Engine standard environment that use automatic scaling and have warmup requests enabled).',
        'upsertAppengineLoadBalancerAtomicOperationDescription.split.allocations.empty'
      ]
  }

  @Unroll
  void "validates shardBy based on traffic split allocations and gradual migration option"() {
    setup:
      def description = new UpsertAppengineLoadBalancerDescription(
        accountName: ACCOUNT_NAME,
        loadBalancerName: LOAD_BALANCER_NAME,
        split: split,
        migrateTraffic: migrateTraffic,
        credentials: credentials
      )
      def errors = Mock(ValidationErrors)

    when:
      validator.validate([], description, errors)

    then:
      1 * errors.rejectValue('upsertAppengineLoadBalancerAtomicOperationDescription.split.shardBy', errorMessage)

    where:
      split << [
        new AppengineTrafficSplit(allocations: [(SERVER_GROUP_NAME_3): 1]),
        new AppengineTrafficSplit(allocations: ["app-stack-detail-v000": 0.5, "app-stack-detail-v001": 0.5]),
      ]

      migrateTraffic << [true, false]

      errorMessage << [
        'upsertAppengineLoadBalancerAtomicOperationDescription.split.shardBy.invalid'
         + ' (A shardBy value must be specified for gradual traffic migration).',
        'upsertAppengineLoadBalancerAtomicOperationDescription.split.shardBy.invalid '
          + '(A shardBy value must be specified if traffic will be split between multiple server groups).'
      ]
  }

  void "traffic split with just shardBy value passes validation"() {
    setup:
      def validSplit = new AppengineTrafficSplit(
        shardBy: ShardBy.IP
      )
      def description = new UpsertAppengineLoadBalancerDescription(
        accountName: ACCOUNT_NAME,
        loadBalancerName: LOAD_BALANCER_NAME,
        split: validSplit,
        migrateTraffic: MIGRATE_TRAFFIC,
        credentials: credentials
      )
      def errors = Mock(ValidationErrors)

    when:
      validator.validate([], description, errors)

    then:
      0 * errors._
  }

  void "allocation with uncached server group fails validation"() {
    setup:
      def invalidSplit = new AppengineTrafficSplit(
        allocations: ["does-not-exist": 1],
        shardBy: ShardBy.IP)
      def description = new UpsertAppengineLoadBalancerDescription(
        accountName: ACCOUNT_NAME,
        loadBalancerName: LOAD_BALANCER_NAME,
        split: invalidSplit,
        migrateTraffic: MIGRATE_TRAFFIC,
        credentials: credentials)
      def errors = Mock(ValidationErrors)

    when:
      validator.validate([], description, errors)

    then:
      1 * errors.rejectValue('upsertAppengineLoadBalancerAtomicOperationDescription.split.allocations',
                             'upsertAppengineLoadBalancerAtomicOperationDescription.split.allocations.invalid ' +
                             '(Server group does-not-exist not found).')
  }

  void "null input fails validation"() {
    setup:
      def description = new UpsertAppengineLoadBalancerDescription()
      def errors = Mock(ValidationErrors)

    when:
      validator.validate([], description, errors)

    then:
      1 * errors.rejectValue("upsertAppengineLoadBalancerAtomicOperationDescription.account",
                             "upsertAppengineLoadBalancerAtomicOperationDescription.account.empty")
  }
}
