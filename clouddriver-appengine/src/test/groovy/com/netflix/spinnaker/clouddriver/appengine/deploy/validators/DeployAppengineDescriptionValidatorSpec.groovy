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

import com.netflix.spinnaker.clouddriver.appengine.deploy.description.DeployAppengineDescription
import com.netflix.spinnaker.clouddriver.appengine.gitClient.AppengineGitCredentialType
import com.netflix.spinnaker.clouddriver.appengine.gitClient.AppengineGitCredentials
import com.netflix.spinnaker.clouddriver.appengine.security.AppengineCredentials
import com.netflix.spinnaker.clouddriver.appengine.security.AppengineNamedAccountCredentials
import com.netflix.spinnaker.clouddriver.deploy.ValidationErrors
import com.netflix.spinnaker.clouddriver.security.DefaultAccountCredentialsProvider
import com.netflix.spinnaker.clouddriver.security.MapBackedAccountCredentialsRepository
import spock.lang.Shared
import spock.lang.Specification

class DeployAppengineDescriptionValidatorSpec extends Specification {
  private static final ACCOUNT_NAME = "my-appengine-account"
  private static final APPLICATION_NAME = "myapp"
  private static final STACK = "stack"
  private static final FREE_FORM_DETAILS = "details"
  private static final REPOSITORY_URL = "git@github.com:spinnaker/clouddriver.git"
  private static final BRANCH = "appengine"
  private static final CONFIG_FILEPATHS = ["/path/to/app.yaml"]
  private static final REGION = "us-central"

  @Shared
  DeployAppengineDescriptionValidator validator

  @Shared
  AppengineNamedAccountCredentials credentials

  void setupSpec() {
    validator = new DeployAppengineDescriptionValidator()

    def credentialsRepo = new MapBackedAccountCredentialsRepository()
    def mockCredentials = Mock(AppengineCredentials)
    credentials = new AppengineNamedAccountCredentials.Builder()
      .name(ACCOUNT_NAME)
      .region(REGION)
      .applicationName(APPLICATION_NAME)
      .credentials(mockCredentials)
      .gitCredentials(new AppengineGitCredentials())
      .build()
    credentialsRepo.save(ACCOUNT_NAME, credentials)

    validator.accountCredentialsProvider = new DefaultAccountCredentialsProvider(credentialsRepo)
  }

  void "pass validation with proper description inputs"() {
    setup:
      def description = new DeployAppengineDescription(
        accountName: ACCOUNT_NAME,
        application: APPLICATION_NAME,
        stack: STACK,
        freeFormDetails: FREE_FORM_DETAILS,
        repositoryUrl: REPOSITORY_URL,
        branch: BRANCH,
        configFilepaths: CONFIG_FILEPATHS,
        promote: true,
        stopPreviousVersion: true,
        credentials: credentials,
        gitCredentialType: AppengineGitCredentialType.NONE)
      def errors = Mock(ValidationErrors)

    when:
      validator.validate([], description, errors)

    then:
      0 * errors._
  }

  void "pass validation without stack, freeFormDetails, promote or stopPreviousVersion"() {
    setup:
      def description = new DeployAppengineDescription(
        accountName: ACCOUNT_NAME,
        application: APPLICATION_NAME,
        repositoryUrl: REPOSITORY_URL,
        branch: BRANCH,
        configFilepaths: CONFIG_FILEPATHS,
        credentials: credentials,
        gitCredentialType: AppengineGitCredentialType.NONE)
      def errors = Mock(ValidationErrors)

    when:
      validator.validate([], description, errors)

    then:
      0 * errors._
  }

  void "null input fails validation"() {
    setup:
      def description = new DeployAppengineDescription()
      def errors = Mock(ValidationErrors)

    when:
      validator.validate([], description, errors)

    then:
      1 * errors.rejectValue('deployAppengineAtomicOperationDescription.account',
                             'deployAppengineAtomicOperationDescription.account.empty')
  }
}
