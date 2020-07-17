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

import com.netflix.spinnaker.clouddriver.appengine.deploy.description.StartStopAppengineDescription
import com.netflix.spinnaker.clouddriver.appengine.model.AppengineScalingPolicy
import com.netflix.spinnaker.clouddriver.appengine.model.AppengineServerGroup
import com.netflix.spinnaker.clouddriver.appengine.model.ScalingPolicyType
import com.netflix.spinnaker.clouddriver.appengine.provider.view.AppengineClusterProvider
import com.netflix.spinnaker.clouddriver.appengine.security.AppengineCredentials
import com.netflix.spinnaker.clouddriver.appengine.security.AppengineNamedAccountCredentials
import com.netflix.spinnaker.clouddriver.deploy.ValidationErrors
import com.netflix.spinnaker.clouddriver.security.DefaultAccountCredentialsProvider
import com.netflix.spinnaker.clouddriver.security.MapBackedAccountCredentialsRepository
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

class StartAppengineDescriptionValidatorSpec extends Specification {
  private static final ACCOUNT_NAME = "my-appengine-account"
  private static final REGION = "us-central"
  private static final APPLICATION_NAME = "myapp"
  private static final SERVER_GROUP_NAME = "app-stack-detail"

  @Shared
  StartAppengineDescriptionValidator validator

  @Shared
  AppengineNamedAccountCredentials credentials

  void setupSpec() {
    validator = new StartAppengineDescriptionValidator()

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
  }

  @Unroll
  void "pass validation with proper description inputs"() {
    setup:
      def description = new StartStopAppengineDescription(
        accountName: ACCOUNT_NAME,
        serverGroupName: SERVER_GROUP_NAME,
        credentials: credentials
      )
      def errors = Mock(ValidationErrors)
      validator.appengineClusterProvider = Mock(AppengineClusterProvider)
      validator.appengineClusterProvider.getServerGroup(ACCOUNT_NAME, REGION, SERVER_GROUP_NAME) >> serverGroup

    when:
      validator.validate([], description, errors)

    then:
      0 * errors._

    where:
      serverGroup << [
        new AppengineServerGroup(env: AppengineServerGroup.Environment.FLEXIBLE),
        new AppengineServerGroup(scalingPolicy: new AppengineScalingPolicy(type: ScalingPolicyType.BASIC)),
        new AppengineServerGroup(scalingPolicy: new AppengineScalingPolicy(type: ScalingPolicyType.MANUAL))
      ]
  }

  @Unroll
  void "fails validation if server group has wrong type"() {
    setup:
      def description = new StartStopAppengineDescription(
        accountName: ACCOUNT_NAME,
        serverGroupName: SERVER_GROUP_NAME,
        credentials: credentials
      )
      def errors = Mock(ValidationErrors)
      validator.appengineClusterProvider = Mock(AppengineClusterProvider)
      validator.appengineClusterProvider.getServerGroup(ACCOUNT_NAME, REGION, SERVER_GROUP_NAME) >> serverGroup

    when:
      validator.validate([], description, errors)

    then:
      1 * errors.rejectValue('startAppengineAtomicOperationDescription.serverGroupName',
                             'startAppengineAtomicOperationDescription.serverGroupName.invalid ' +
                             '(Only server groups that use the App Engine flexible environment, or use basic ' +
                             'or manual scaling can be started or stopped).')

    where:
      serverGroup << [
        new AppengineServerGroup(env: AppengineServerGroup.Environment.STANDARD),
        new AppengineServerGroup(scalingPolicy: new AppengineScalingPolicy(type: ScalingPolicyType.AUTOMATIC))
      ]
  }

  void "fails validation if server group not found"() {
    setup:
      def description = new StartStopAppengineDescription(
        accountName: ACCOUNT_NAME,
        serverGroupName: SERVER_GROUP_NAME,
        credentials: credentials
      )
      def errors = Mock(ValidationErrors)
      validator.appengineClusterProvider = Mock(AppengineClusterProvider)
      validator.appengineClusterProvider.getServerGroup(ACCOUNT_NAME, REGION, SERVER_GROUP_NAME) >> null

    when:
      validator.validate([], description, errors)

    then:
      1 * errors.rejectValue('startAppengineAtomicOperationDescription.serverGroupName',
                             'startAppengineAtomicOperationDescription.serverGroupName.invalid ' +
                             '(Server group app-stack-detail not found).')
  }

  void "null input fails validation"() {
    setup:
      def description = new StartStopAppengineDescription()
      def errors = Mock(ValidationErrors)

    when:
      validator.validate([], description, errors)

    then:
      1 * errors.rejectValue("startAppengineAtomicOperationDescription.account",
                             "startAppengineAtomicOperationDescription.account.empty")
  }
}
