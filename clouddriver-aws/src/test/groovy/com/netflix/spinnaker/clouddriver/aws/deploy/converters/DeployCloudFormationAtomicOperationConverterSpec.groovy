/*
 * Copyright (c) 2019 Schibsted Media Group.
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

package com.netflix.spinnaker.clouddriver.aws.deploy.converters

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.clouddriver.aws.deploy.converters.CopyLastAsgAtomicOperationConverter
import com.netflix.spinnaker.clouddriver.aws.deploy.description.BasicAmazonDeployDescription
import com.netflix.spinnaker.clouddriver.aws.deploy.description.DeployCloudFormationDescription
import com.netflix.spinnaker.clouddriver.aws.deploy.ops.CopyLastAsgAtomicOperation
import com.netflix.spinnaker.clouddriver.aws.deploy.ops.DeployCloudFormationAtomicOperation
import com.netflix.spinnaker.clouddriver.aws.security.NetflixAmazonCredentials
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider
import spock.lang.Shared
import spock.lang.Specification

class DeployCloudFormationAtomicOperationConverterSpec extends Specification {

  @Shared
  ObjectMapper mapper = new ObjectMapper()

  @Shared
  DeployCloudFormationAtomicOperationConverter converter

  def setupSpec() {
    this.converter = new DeployCloudFormationAtomicOperationConverter(objectMapper: mapper)
    def accountCredentialsProvider = Mock(AccountCredentialsProvider)
    def mockCredentials = Mock(NetflixAmazonCredentials)
    accountCredentialsProvider.getCredentials(_) >> mockCredentials
    converter.accountCredentialsProvider = accountCredentialsProvider
  }

  void "DeployCloudFormationConverter returns DeployCloudFormationDescription"() {
    setup:
    def input = [stackName      : "asgard",
                 templateBody   : [ field1: "field1" ],
                 parameters     : [ param1: "param1" ],
                 region         : "eu-west_1",
                 credentials    : "credentials"]

    when:
    def description = converter.convertDescription(input)

    then:
    description instanceof DeployCloudFormationDescription

    when:
    def operation = converter.convertOperation(input)

    then:
    operation instanceof DeployCloudFormationAtomicOperation
  }
}
