/*
 * Copyright 2014 Netflix, Inc.
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


package com.netflix.spinnaker.kato.deploy.gce.converters

import com.netflix.spinnaker.amos.AccountCredentialsProvider
import com.netflix.spinnaker.kato.security.gce.GoogleNamedAccountCredentials
import com.netflix.spinnaker.kato.deploy.DeployAtomicOperation
import com.netflix.spinnaker.kato.deploy.gce.description.BasicGoogleDeployDescription
import spock.lang.Shared
import spock.lang.Specification

class BasicGoogleDeployAtomicOperationConverterUnitSpec extends Specification {

  @Shared
  BasicGoogleDeployAtomicOperationConverter converter

  def setupSpec() {
    this.converter = new BasicGoogleDeployAtomicOperationConverter()
    def accountCredentialsProvider = Mock(AccountCredentialsProvider)
    def mockCredentials = Mock(GoogleNamedAccountCredentials)
    accountCredentialsProvider.getCredentials(_) >> mockCredentials
    converter.accountCredentialsProvider = accountCredentialsProvider
  }

  void "basicGoogleDeployDescription type returns BasicGoogleDeployDescription and DeployAtomicOperation"() {
    setup:
      def input = [application: "asgard", stack: "asgard-test", image: "debian-7-wheezy-v20140415", type: "f1-micro",
                   zone: "us-central1-b", credentials: "test"]

    when:
      def description = converter.convertDescription(input)

    then:
      description instanceof BasicGoogleDeployDescription

    when:
      def operation = converter.convertOperation(input)

    then:
      operation instanceof DeployAtomicOperation
  }
}
