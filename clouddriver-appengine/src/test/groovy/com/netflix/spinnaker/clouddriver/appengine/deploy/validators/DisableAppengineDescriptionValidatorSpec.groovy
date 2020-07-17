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

import com.netflix.spinnaker.clouddriver.appengine.deploy.description.EnableDisableAppengineDescription
import com.netflix.spinnaker.clouddriver.appengine.model.AppengineLoadBalancer
import com.netflix.spinnaker.clouddriver.appengine.model.AppengineServerGroup
import com.netflix.spinnaker.clouddriver.appengine.model.AppengineTrafficSplit
import com.netflix.spinnaker.clouddriver.appengine.provider.view.AppengineClusterProvider
import com.netflix.spinnaker.clouddriver.appengine.provider.view.AppengineLoadBalancerProvider
import com.netflix.spinnaker.clouddriver.appengine.security.AppengineCredentials
import com.netflix.spinnaker.clouddriver.appengine.security.AppengineNamedAccountCredentials
import com.netflix.spinnaker.clouddriver.deploy.ValidationErrors
import com.netflix.spinnaker.clouddriver.security.DefaultAccountCredentialsProvider
import com.netflix.spinnaker.clouddriver.security.MapBackedAccountCredentialsRepository
import spock.lang.Shared
import spock.lang.Specification

class DisableAppengineDescriptionValidatorSpec extends Specification {
  private static final ACCOUNT_NAME = "my-appengine-account"
  private static final REGION = "us-central"
  private static final APPLICATION_NAME = "myapp"

  private static final SERVER_GROUP_NAME = "app-stack-detail-v000"
  private static final SERVER_GROUP = new AppengineServerGroup(
    loadBalancers: [LOAD_BALANCER_NAME]
  )
  private static final LOAD_BALANCER_NAME = "default"

  @Shared
  AppengineNamedAccountCredentials credentials

  @Shared
  DefaultAccountCredentialsProvider accountCredentialsProvider

  void setupSpec() {
    def credentialsRepo = new MapBackedAccountCredentialsRepository()
    def mockCredentials = Mock(AppengineCredentials)
    credentials = new AppengineNamedAccountCredentials.Builder()
      .name(ACCOUNT_NAME)
      .region(REGION)
      .applicationName(APPLICATION_NAME)
      .credentials(mockCredentials)
      .build()
    credentialsRepo.save(ACCOUNT_NAME, credentials)
    accountCredentialsProvider = new DefaultAccountCredentialsProvider(credentialsRepo)
  }

  void "passes validation if server group to be disabled does not have allocation of 1"() {
    setup:
      def validator = new DisableAppengineDescriptionValidator()
      validator.accountCredentialsProvider = accountCredentialsProvider
      validator.appengineLoadBalancerProvider = Mock(AppengineLoadBalancerProvider)
      validator.appengineClusterProvider = Mock(AppengineClusterProvider)

      def description = new EnableDisableAppengineDescription(
        serverGroupName: "app-stack-detail-v000",
        accountName: ACCOUNT_NAME,
        credentials: credentials
      )

      def errors = Mock(ValidationErrors)

      def loadBalancerWithValidAllocationsForDescription = new AppengineLoadBalancer(
        split: new AppengineTrafficSplit(allocations: [(SERVER_GROUP_NAME): 0.5, "another-server-group": 0.5])
      )

    when:
      validator.validate([], description, errors)

    then:
      validator.appengineClusterProvider.getServerGroup(ACCOUNT_NAME, REGION, SERVER_GROUP_NAME) >> SERVER_GROUP
      validator.appengineLoadBalancerProvider.getLoadBalancer(ACCOUNT_NAME, LOAD_BALANCER_NAME) >> loadBalancerWithValidAllocationsForDescription

      0 * errors._
  }

  void "fails validation if server group to be disabled has allocation of 1"() {
    setup:
      def validator = new DisableAppengineDescriptionValidator()
      validator.accountCredentialsProvider = accountCredentialsProvider
      validator.appengineLoadBalancerProvider = Mock(AppengineLoadBalancerProvider)
      validator.appengineClusterProvider = Mock(AppengineClusterProvider)

      def description = new EnableDisableAppengineDescription(
        serverGroupName: "app-stack-detail-v000",
        accountName: ACCOUNT_NAME,
        credentials: credentials
      )

      def errors = Mock(ValidationErrors)

      def loadBalancerWithInvalidAllocationsForDescription = new AppengineLoadBalancer(
        split: new AppengineTrafficSplit(allocations: [(SERVER_GROUP_NAME): 1])
      )

    when:
      validator.validate([], description, errors)

    then:
      validator.appengineClusterProvider.getServerGroup(ACCOUNT_NAME, REGION, SERVER_GROUP_NAME) >> SERVER_GROUP
      validator.appengineLoadBalancerProvider.getLoadBalancer(ACCOUNT_NAME, LOAD_BALANCER_NAME) >> loadBalancerWithInvalidAllocationsForDescription

      1 * errors.rejectValue("disableAppengineAtomicOperationDescription.serverGroupName",
                             "disableAppengineAtomicOperationDescription.serverGroupName.invalid " +
                             "(Server group app-stack-detail-v000 is the only server group" +
                             " receiving traffic from load balancer default).")
  }

  void "fails validation if server group cannot be found"() {
    setup:
      def validator = new DisableAppengineDescriptionValidator()
      validator.accountCredentialsProvider = accountCredentialsProvider
      validator.appengineLoadBalancerProvider = Mock(AppengineLoadBalancerProvider)
      validator.appengineClusterProvider = Mock(AppengineClusterProvider)

      def description = new EnableDisableAppengineDescription(
        serverGroupName: "does-not-exist",
        accountName: ACCOUNT_NAME,
        credentials: credentials
      )

      def errors = Mock(ValidationErrors)

    when:
      validator.validate([], description, errors)

    then:
      validator.appengineClusterProvider.getServerGroup(ACCOUNT_NAME, REGION, "does-not-exist") >> null

      1 * errors.rejectValue("disableAppengineAtomicOperationDescription.serverGroupName",
                             "disableAppengineAtomicOperationDescription.serverGroupName.invalid " +
                             "(Server group does-not-exist not found).")
  }

  void "fails validation if parent load balancer cannot be found"() {
    setup:
      def validator = new DisableAppengineDescriptionValidator()
      validator.accountCredentialsProvider = accountCredentialsProvider
      validator.appengineLoadBalancerProvider = Mock(AppengineLoadBalancerProvider)
      validator.appengineClusterProvider = Mock(AppengineClusterProvider)

      def description = new EnableDisableAppengineDescription(
        serverGroupName: "app-stack-detail-v000",
        accountName: ACCOUNT_NAME,
        credentials: credentials
      )

      def errors = Mock(ValidationErrors)

    when:
      validator.validate([], description, errors)

    then:
      validator.appengineClusterProvider.getServerGroup(ACCOUNT_NAME, REGION, SERVER_GROUP_NAME) >> SERVER_GROUP
      validator.appengineLoadBalancerProvider.getLoadBalancer(ACCOUNT_NAME, LOAD_BALANCER_NAME) >> null

      1 * errors.rejectValue("disableAppengineAtomicOperationDescription.serverGroupName",
                             "disableAppengineAtomicOperationDescription.serverGroupName.invalid " +
                             "(Could not find parent load balancer default for server group app-stack-detail-v000).")
  }
}
