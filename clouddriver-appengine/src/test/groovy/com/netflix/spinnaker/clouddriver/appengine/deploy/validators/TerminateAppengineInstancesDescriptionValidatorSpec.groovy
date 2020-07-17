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

import com.netflix.spinnaker.clouddriver.appengine.deploy.description.TerminateAppengineInstancesDescription
import com.netflix.spinnaker.clouddriver.appengine.model.AppengineInstance
import com.netflix.spinnaker.clouddriver.appengine.provider.view.AppengineInstanceProvider
import com.netflix.spinnaker.clouddriver.appengine.security.AppengineCredentials
import com.netflix.spinnaker.clouddriver.appengine.security.AppengineNamedAccountCredentials
import com.netflix.spinnaker.clouddriver.deploy.ValidationErrors
import com.netflix.spinnaker.clouddriver.security.DefaultAccountCredentialsProvider
import com.netflix.spinnaker.clouddriver.security.MapBackedAccountCredentialsRepository
import spock.lang.Shared
import spock.lang.Specification

class TerminateAppengineInstancesDescriptionValidatorSpec extends Specification {
  private static final ACCOUNT_NAME = "my-appengine-account"
  private static final REGION = "us-central"
  private static final APPLICATION_NAME = "myapp"
  private static final INSTANCE_IDS = ["instance-1"]

  private static final INSTANCE = new AppengineInstance(
    name: "instance-1",
    serverGroup: "app-stack-detail-v000",
    loadBalancers: ["default"]
  )

  private static final INSTANCE_MISSING_FIELDS = new AppengineInstance(name: "missing-fields-instance")

  @Shared
  TerminateAppengineInstancesDescriptionValidator validator

  @Shared
  AppengineNamedAccountCredentials credentials

  void setupSpec() {
    validator = new TerminateAppengineInstancesDescriptionValidator()

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
    validator.appengineInstanceProvider = Mock(AppengineInstanceProvider)

    validator.appengineInstanceProvider.getInstance(ACCOUNT_NAME, REGION, "instance-1") >> INSTANCE
    validator.appengineInstanceProvider.getInstance(ACCOUNT_NAME, REGION, "instance-missing-fields") >> INSTANCE_MISSING_FIELDS
  }

  void "pass validation with proper description inputs"() {
    setup:
      def description = new TerminateAppengineInstancesDescription(
        accountName: ACCOUNT_NAME,
        instanceIds: INSTANCE_IDS,
        credentials: credentials
      )
      def errors = Mock(ValidationErrors)

    when:
      validator.validate([], description, errors)

    then:
      0 * errors._
  }

  void "fails validation for unknown instance"() {
    setup:
      def description = new TerminateAppengineInstancesDescription(
        accountName: ACCOUNT_NAME,
        instanceIds: ["instance-does-not-exist"],
        credentials: credentials
      )
      def errors = Mock(ValidationErrors)

    when:
      validator.validate([], description, errors)

    then:
      1 * errors.rejectValue("terminateAppengineInstancesAtomicOperationDescription.instanceIds",
                             "terminateAppengineInstancesAtomicOperationDescription.instanceIds.invalid" +
                             " (Instance instance-does-not-exist not found).")
  }

  void "fails validation for instance with missing fields"() {
    setup:
      def description = new TerminateAppengineInstancesDescription(
        accountName: ACCOUNT_NAME,
        instanceIds: ["instance-missing-fields"],
        credentials: credentials
      )
      def errors = Mock(ValidationErrors)

    when:
      validator.validate([], description, errors)

    then:
      1 * errors.rejectValue("terminateAppengineInstancesAtomicOperationDescription.instanceIds",
                             "terminateAppengineInstancesAtomicOperationDescription.instanceIds.invalid " +
                             "(Could not find parent server group for instance instance-missing-fields).")
  }

  void "null input fails validation"() {
    setup:
     def description = new TerminateAppengineInstancesDescription()
     def errors = Mock(ValidationErrors)

    when:
      validator.validate([], description, errors)

    then:
      1 * errors.rejectValue("terminateAppengineInstancesAtomicOperationDescription.account",
                             "terminateAppengineInstancesAtomicOperationDescription.account.empty")
  }
}
