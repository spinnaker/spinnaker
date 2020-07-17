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

import com.netflix.spinnaker.clouddriver.appengine.deploy.description.DeleteAppengineLoadBalancerDescription
import com.netflix.spinnaker.clouddriver.appengine.security.AppengineCredentials
import com.netflix.spinnaker.clouddriver.appengine.security.AppengineNamedAccountCredentials
import com.netflix.spinnaker.clouddriver.deploy.ValidationErrors
import com.netflix.spinnaker.clouddriver.security.DefaultAccountCredentialsProvider
import com.netflix.spinnaker.clouddriver.security.MapBackedAccountCredentialsRepository
import spock.lang.Shared
import spock.lang.Specification

class DeleteAppengineLoadBalancerDescriptionValidatorSpec extends Specification {
  private static final ACCOUNT_NAME = "my-appengine-account"
  private static final REGION = "us-central"
  private static final APPLICATION_NAME = "myapp"
  private static final LOAD_BALANCER_NAME = "mobile"

  @Shared
  DeleteAppengineLoadBalancerDescriptionValidator validator

  void setupSpec() {
    validator = new DeleteAppengineLoadBalancerDescriptionValidator()

    def credentialsRepo = new MapBackedAccountCredentialsRepository()
    def mockCredentials = Mock(AppengineCredentials)
    def namedAccountCredentials = new AppengineNamedAccountCredentials.Builder()
      .name(ACCOUNT_NAME)
      .region(REGION)
      .applicationName(APPLICATION_NAME)
      .credentials(mockCredentials)
      .build()
    credentialsRepo.save(ACCOUNT_NAME, namedAccountCredentials)

    validator.accountCredentialsProvider = new DefaultAccountCredentialsProvider(credentialsRepo)
  }

  void "pass validation with proper description inputs"() {
    setup:
      def description = new DeleteAppengineLoadBalancerDescription(accountName: ACCOUNT_NAME, loadBalancerName: LOAD_BALANCER_NAME)
      def errors = Mock(ValidationErrors)

    when:
      validator.validate([], description, errors)

    then:
      0 * errors._
  }

  void "description with loadBalancerName == \"default\" fails validation"() {
    setup:
      def description = new DeleteAppengineLoadBalancerDescription(accountName: ACCOUNT_NAME, loadBalancerName: "default")
      def errors = Mock(ValidationErrors)

    when:
      validator.validate([], description, errors)

    then:
      1 * errors.rejectValue("deleteAppengineLoadBalancerAtomicOperationDescription.loadBalancerName",
                             "deleteAppengineLoadBalancerAtomicOperationDescription.loadBalancerName.invalid (Cannot delete default service).")
  }

  void "null input fails validation"() {
    setup:
      def description = new DeleteAppengineLoadBalancerDescription()
      def errors = Mock(ValidationErrors)

    when:
      validator.validate([], description, errors)

    then:
      1 * errors.rejectValue("deleteAppengineLoadBalancerAtomicOperationDescription.account",
                             "deleteAppengineLoadBalancerAtomicOperationDescription.account.empty")
  }
}
