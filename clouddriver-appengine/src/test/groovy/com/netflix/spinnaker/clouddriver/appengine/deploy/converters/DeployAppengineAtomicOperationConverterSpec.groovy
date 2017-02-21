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

package com.netflix.spinnaker.clouddriver.appengine.deploy.converters

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.clouddriver.appengine.deploy.description.DeployAppengineDescription
import com.netflix.spinnaker.clouddriver.appengine.deploy.ops.DeployAppengineAtomicOperation
import com.netflix.spinnaker.clouddriver.appengine.security.AppengineNamedAccountCredentials
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider
import spock.lang.Shared
import spock.lang.Specification

class DeployAppengineAtomicOperationConverterSpec extends Specification {
  private static final ACCOUNT_NAME = "my-appengine-account"
  private static final APPLICATION = "myapp"
  private static final REPO_URL = "https://github.com/spinnaker/clouddriver.git"
  private static final BRANCH = "new-feature"
  private static final CONFIG_FILEPATHS = ["/path/to/app.yaml"]

  @Shared
  ObjectMapper mapper = new ObjectMapper()

  @Shared
  DeployAppengineAtomicOperationConverter converter

  def setupSpec() {
    this.converter = new DeployAppengineAtomicOperationConverter(objectMapper: mapper)
    def accountCredentialsProvider = Mock(AccountCredentialsProvider)
    def mockCredentials = Mock(AppengineNamedAccountCredentials)
    accountCredentialsProvider.getCredentials(_) >> mockCredentials
    converter.accountCredentialsProvider = accountCredentialsProvider
  }

  void "deployAppengineDescription type returns DeployAppengineDescription and DeployAppengineAtomicOperation"() {
    setup:
      def input = [
        credentials: ACCOUNT_NAME,
        application: APPLICATION,
        repositoryUrl: REPO_URL,
        branch: BRANCH,
        configFilepaths: CONFIG_FILEPATHS
      ]

    when:
      def description = converter.convertDescription(input)

    then:
      description instanceof DeployAppengineDescription

    when:
      def operation = converter.convertOperation(input)

    then:
      operation instanceof DeployAppengineAtomicOperation
  }
}
