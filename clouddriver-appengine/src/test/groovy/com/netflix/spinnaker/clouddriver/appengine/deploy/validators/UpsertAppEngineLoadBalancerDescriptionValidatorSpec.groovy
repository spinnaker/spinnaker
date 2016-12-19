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

import com.netflix.spinnaker.clouddriver.appengine.deploy.description.UpsertAppEngineLoadBalancerDescription
import com.netflix.spinnaker.clouddriver.appengine.model.AppEngineServerGroup
import com.netflix.spinnaker.clouddriver.appengine.model.AppEngineTrafficSplit
import com.netflix.spinnaker.clouddriver.appengine.model.ShardBy
import com.netflix.spinnaker.clouddriver.appengine.provider.view.AppEngineClusterProvider
import com.netflix.spinnaker.clouddriver.appengine.security.AppEngineCredentials
import com.netflix.spinnaker.clouddriver.appengine.security.AppEngineNamedAccountCredentials
import com.netflix.spinnaker.clouddriver.security.DefaultAccountCredentialsProvider
import com.netflix.spinnaker.clouddriver.security.MapBackedAccountCredentialsRepository
import org.springframework.validation.Errors
import spock.lang.Shared
import spock.lang.Specification

class UpsertAppEngineLoadBalancerDescriptionValidatorSpec extends Specification {
  private static final ACCOUNT_NAME = "my-appengine-account"
  private static final APPLICATION_NAME = "myapp"
  private static final REGION = "us-central"
  private static final LOAD_BALANCER_NAME = "default"
  private static final MIGRATE_TRAFFIC = false

  private static final SERVER_GROUP_NAME_1 = "app-stack-detail-v000"
  private static final SERVER_GROUP_1 = new AppEngineServerGroup(
    name: SERVER_GROUP_NAME_1,
    loadBalancers: [LOAD_BALANCER_NAME]
  )

  private static final SERVER_GROUP_NAME_2 = "app-stack-detail-v001"
  private static final SERVER_GROUP_2 = new AppEngineServerGroup(
    name: SERVER_GROUP_NAME_2,
    loadBalancers: [LOAD_BALANCER_NAME]
  )

  @Shared
  UpsertAppEngineLoadBalancerDescriptionValidator validator

  @Shared
  AppEngineNamedAccountCredentials credentials

  void setupSpec() {
    validator = new UpsertAppEngineLoadBalancerDescriptionValidator()

    def credentialsRepo = new MapBackedAccountCredentialsRepository()
    def mockCredentials = Mock(AppEngineCredentials)
    credentials = new AppEngineNamedAccountCredentials.Builder()
      .name(ACCOUNT_NAME)
      .region(REGION)
      .applicationName(APPLICATION_NAME)
      .credentials(mockCredentials)
      .build()
    credentialsRepo.save(ACCOUNT_NAME, credentials)

    validator.accountCredentialsProvider = new DefaultAccountCredentialsProvider(credentialsRepo)
    validator.appEngineClusterProvider = Mock(AppEngineClusterProvider)

    validator.appEngineClusterProvider.getServerGroup(ACCOUNT_NAME, REGION, SERVER_GROUP_NAME_1) >> SERVER_GROUP_1
    validator.appEngineClusterProvider.getServerGroup(ACCOUNT_NAME, REGION, SERVER_GROUP_NAME_2) >> SERVER_GROUP_2
  }

  void "pass validation with proper description inputs"() {
    setup:
      def validSplit = new AppEngineTrafficSplit(
        allocations: ["app-stack-detail-v000": 0.6, "app-stack-detail-v001": 0.4],
        shardBy: ShardBy.IP)
      def description = new UpsertAppEngineLoadBalancerDescription(
        accountName: ACCOUNT_NAME,
        loadBalancerName: LOAD_BALANCER_NAME,
        split: validSplit,
        migrateTraffic: MIGRATE_TRAFFIC,
        credentials: credentials)
      def errors = Mock(Errors)

    when:
      validator.validate([], description, errors)

    then:
      0 * errors._
  }

  void "description with allocations that do not sum to 1 fails validation"() {
    setup:
      def validSplit = new AppEngineTrafficSplit(
        allocations: ["app-stack-detail-v000": 0.7, "app-stack-detail-v001": 0.4],
        shardBy: ShardBy.IP)
      def description = new UpsertAppEngineLoadBalancerDescription(
        accountName: ACCOUNT_NAME,
        loadBalancerName: LOAD_BALANCER_NAME,
        split: validSplit,
        migrateTraffic: MIGRATE_TRAFFIC,
        credentials: credentials)
      def errors = Mock(Errors)

    when:
      validator.validate([], description, errors)

    then:
      1 * errors.rejectValue('upsertAppEngineLoadBalancerAtomicOperationDescription.split.allocations',
                             'upsertAppEngineLoadBalancerAtomicOperationDescription.split.allocations.invalid (Allocations must sum to 1)')
  }

  void "allocation with uncached server group fails validation"() {
    setup:
      def validSplit = new AppEngineTrafficSplit(
        allocations: ["does-not-exist": 1],
        shardBy: ShardBy.IP)
      def description = new UpsertAppEngineLoadBalancerDescription(
        accountName: ACCOUNT_NAME,
        loadBalancerName: LOAD_BALANCER_NAME,
        split: validSplit,
        migrateTraffic: MIGRATE_TRAFFIC,
        credentials: credentials)
      def errors = Mock(Errors)

    when:
      validator.validate([], description, errors)

    then:
      1 * errors.rejectValue('upsertAppEngineLoadBalancerAtomicOperationDescription.split.allocations',
                             'upsertAppEngineLoadBalancerAtomicOperationDescription.split.allocations.invalid ' +
                             '(Server group does-not-exist cannot be enabled for load balancer default).')
  }

  void "null input fails validation"() {
    setup:
      def description = new UpsertAppEngineLoadBalancerDescription()
      def errors = Mock(Errors)

    when:
      validator.validate([], description, errors)

    then:
      1 * errors.rejectValue("upsertAppEngineLoadBalancerAtomicOperationDescription.account",
                             "upsertAppEngineLoadBalancerAtomicOperationDescription.account.empty")
  }
}
