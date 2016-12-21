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

import com.netflix.spinnaker.clouddriver.appengine.deploy.description.EnableDisableAppEngineDescription
import com.netflix.spinnaker.clouddriver.appengine.model.AppEngineLoadBalancer
import com.netflix.spinnaker.clouddriver.appengine.model.AppEngineServerGroup
import com.netflix.spinnaker.clouddriver.appengine.model.AppEngineTrafficSplit
import com.netflix.spinnaker.clouddriver.appengine.provider.view.AppEngineClusterProvider
import com.netflix.spinnaker.clouddriver.appengine.provider.view.AppEngineLoadBalancerProvider
import com.netflix.spinnaker.clouddriver.appengine.security.AppEngineCredentials
import com.netflix.spinnaker.clouddriver.appengine.security.AppEngineNamedAccountCredentials
import com.netflix.spinnaker.clouddriver.security.DefaultAccountCredentialsProvider
import com.netflix.spinnaker.clouddriver.security.MapBackedAccountCredentialsRepository
import org.springframework.validation.Errors
import spock.lang.Shared
import spock.lang.Specification

class DisableAppEngineDescriptionValidatorSpec extends Specification {
  private static final ACCOUNT_NAME = "my-appengine-account"
  private static final REGION = "us-central"
  private static final APPLICATION_NAME = "myapp"

  private static final SERVER_GROUP_NAME = "app-stack-detail-v000"
  private static final SERVER_GROUP = new AppEngineServerGroup(
    loadBalancers: [LOAD_BALANCER_NAME]
  )
  private static final LOAD_BALANCER_NAME = "default"

  @Shared
  AppEngineNamedAccountCredentials credentials

  @Shared
  DefaultAccountCredentialsProvider accountCredentialsProvider

  void setupSpec() {
    def credentialsRepo = new MapBackedAccountCredentialsRepository()
    def mockCredentials = Mock(AppEngineCredentials)
    credentials = new AppEngineNamedAccountCredentials.Builder()
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
      def validator = new DisableAppEngineDescriptionValidator()
      validator.accountCredentialsProvider = accountCredentialsProvider
      validator.appEngineLoadBalancerProvider = Mock(AppEngineLoadBalancerProvider)
      validator.appEngineClusterProvider = Mock(AppEngineClusterProvider)

      def description = new EnableDisableAppEngineDescription(
        serverGroupName: "app-stack-detail-v000",
        accountName: ACCOUNT_NAME,
        credentials: credentials
      )

      def errors = Mock(Errors)

      def loadBalancerWithValidAllocationsForDescription = new AppEngineLoadBalancer(
        split: new AppEngineTrafficSplit(allocations: [(SERVER_GROUP_NAME): 0.5, "another-server-group": 0.5])
      )

    when:
      validator.validate([], description, errors)

    then:
      validator.appEngineClusterProvider.getServerGroup(ACCOUNT_NAME, REGION, SERVER_GROUP_NAME) >> SERVER_GROUP
      validator.appEngineLoadBalancerProvider.getLoadBalancer(ACCOUNT_NAME, LOAD_BALANCER_NAME) >> loadBalancerWithValidAllocationsForDescription

      0 * errors._
  }

  void "fails validation if server group to be disabled has allocation of 1"() {
    setup:
      def validator = new DisableAppEngineDescriptionValidator()
      validator.accountCredentialsProvider = accountCredentialsProvider
      validator.appEngineLoadBalancerProvider = Mock(AppEngineLoadBalancerProvider)
      validator.appEngineClusterProvider = Mock(AppEngineClusterProvider)

      def description = new EnableDisableAppEngineDescription(
        serverGroupName: "app-stack-detail-v000",
        accountName: ACCOUNT_NAME,
        credentials: credentials
      )

      def errors = Mock(Errors)

      def loadBalancerWithInvalidAllocationsForDescription = new AppEngineLoadBalancer(
        split: new AppEngineTrafficSplit(allocations: [(SERVER_GROUP_NAME): 1])
      )

    when:
      validator.validate([], description, errors)

    then:
      validator.appEngineClusterProvider.getServerGroup(ACCOUNT_NAME, REGION, SERVER_GROUP_NAME) >> SERVER_GROUP
      validator.appEngineLoadBalancerProvider.getLoadBalancer(ACCOUNT_NAME, LOAD_BALANCER_NAME) >> loadBalancerWithInvalidAllocationsForDescription

      1 * errors.rejectValue("disableAppEngineAtomicOperationDescription.serverGroupName",
                             "disableAppEngineAtomicOperationDescription.serverGroupName.invalid " +
                             "(Server group app-stack-detail-v000 is the only server group" +
                             " receiving traffic from load balancer default).")
  }

  void "fails validation if server group cannot be found"() {
    setup:
      def validator = new DisableAppEngineDescriptionValidator()
      validator.accountCredentialsProvider = accountCredentialsProvider
      validator.appEngineLoadBalancerProvider = Mock(AppEngineLoadBalancerProvider)
      validator.appEngineClusterProvider = Mock(AppEngineClusterProvider)

      def description = new EnableDisableAppEngineDescription(
        serverGroupName: "does-not-exist",
        accountName: ACCOUNT_NAME,
        credentials: credentials
      )

      def errors = Mock(Errors)

    when:
      validator.validate([], description, errors)

    then:
      validator.appEngineClusterProvider.getServerGroup(ACCOUNT_NAME, REGION, "does-not-exist") >> null

      1 * errors.rejectValue("disableAppEngineAtomicOperationDescription.serverGroupName",
                             "disableAppEngineAtomicOperationDescription.serverGroupName.invalid " +
                             "(Server group does-not-exist not found).")
  }

  void "fails validation if parent load balancer cannot be found"() {
    setup:
      def validator = new DisableAppEngineDescriptionValidator()
      validator.accountCredentialsProvider = accountCredentialsProvider
      validator.appEngineLoadBalancerProvider = Mock(AppEngineLoadBalancerProvider)
      validator.appEngineClusterProvider = Mock(AppEngineClusterProvider)

      def description = new EnableDisableAppEngineDescription(
        serverGroupName: "app-stack-detail-v000",
        accountName: ACCOUNT_NAME,
        credentials: credentials
      )

      def errors = Mock(Errors)

    when:
      validator.validate([], description, errors)

    then:
      validator.appEngineClusterProvider.getServerGroup(ACCOUNT_NAME, REGION, SERVER_GROUP_NAME) >> SERVER_GROUP
      validator.appEngineLoadBalancerProvider.getLoadBalancer(ACCOUNT_NAME, LOAD_BALANCER_NAME) >> null

      1 * errors.rejectValue("disableAppEngineAtomicOperationDescription.serverGroupName",
                             "disableAppEngineAtomicOperationDescription.serverGroupName.invalid " +
                             "(Could not find parent load balancer default for server group app-stack-detail-v000).")
  }
}
