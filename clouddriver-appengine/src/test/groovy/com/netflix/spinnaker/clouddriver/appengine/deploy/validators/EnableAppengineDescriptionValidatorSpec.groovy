/*
 * Copyright 2017 Google, Inc.
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
import com.netflix.spinnaker.clouddriver.appengine.model.AppengineServerGroup
import com.netflix.spinnaker.clouddriver.appengine.provider.view.AppengineClusterProvider
import com.netflix.spinnaker.clouddriver.appengine.security.AppengineCredentials
import com.netflix.spinnaker.clouddriver.appengine.security.AppengineNamedAccountCredentials
import com.netflix.spinnaker.clouddriver.deploy.ValidationErrors
import com.netflix.spinnaker.clouddriver.security.DefaultAccountCredentialsProvider
import com.netflix.spinnaker.clouddriver.security.MapBackedAccountCredentialsRepository
import spock.lang.Shared
import spock.lang.Specification

class EnableAppengineDescriptionValidatorSpec extends Specification {
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

  void "fails validation if server group cannot be found"() {
    setup:
      def validator = new EnableAppengineDescriptionValidator()
      validator.accountCredentialsProvider = accountCredentialsProvider
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

      1 * errors.rejectValue("enableAppengineAtomicOperationDescription.serverGroupName",
                             "enableAppengineAtomicOperationDescription.serverGroupName.invalid " +
                             "(Server group does-not-exist not found).")
  }

  void "passes validation if server group found in cache"() {
    setup:
      def validator = new EnableAppengineDescriptionValidator()
      validator.accountCredentialsProvider = accountCredentialsProvider
      validator.appengineClusterProvider = Mock(AppengineClusterProvider)

      def description = new EnableDisableAppengineDescription(
        serverGroupName: SERVER_GROUP_NAME,
        accountName: ACCOUNT_NAME,
        credentials: credentials
      )

      def errors = Mock(ValidationErrors)

    when:
      validator.validate([], description, errors)

    then:
      validator.appengineClusterProvider.getServerGroup(ACCOUNT_NAME, REGION, SERVER_GROUP_NAME) >> SERVER_GROUP

      0 * errors._
  }
}
