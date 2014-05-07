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

package com.netflix.kato.deploy.aws.converters

import com.netflix.kato.deploy.DeployAtomicOperation
import com.netflix.kato.deploy.aws.description.BasicAmazonDeployDescription
import com.netflix.kato.security.NamedAccountCredentials
import com.netflix.kato.security.NamedAccountCredentialsHolder
import spock.lang.Shared
import spock.lang.Specification

class BasicAmazonDeployAtomicOperationConverterUnitSpec extends Specification {

  @Shared
  BasicAmazonDeployAtomicOperationConverter converter

  def setupSpec() {
    this.converter = new BasicAmazonDeployAtomicOperationConverter()
    def namedAccountCredentialsHolder = Mock(NamedAccountCredentialsHolder)
    def mockCredentials = Mock(NamedAccountCredentials)
    namedAccountCredentialsHolder.getCredentials(_) >> mockCredentials
    converter.namedAccountCredentialsHolder = namedAccountCredentialsHolder
  }

  void "basicAmazonDeployDescription type returns BasicAmazonDeployDescription and DeployAtomicOperation"() {
    setup:
    def input = [application      : "asgard", amiName: "ami-000", stack: "asgard-test", instanceType: "m3.medium",
                 availabilityZones: ["us-west-1": ["us-west-1a"]], capacity: [min: 1, max: 2, desired: 5],
                 credentials      : "test"]

    when:
    def description = converter.convertDescription(input)

    then:
    description instanceof BasicAmazonDeployDescription

    when:
    def operation = converter.convertOperation(input)

    then:
    operation instanceof DeployAtomicOperation
  }
}
