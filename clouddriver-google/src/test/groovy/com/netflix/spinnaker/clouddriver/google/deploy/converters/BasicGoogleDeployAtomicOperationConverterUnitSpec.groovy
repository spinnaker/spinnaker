/*
 * Copyright 2014 Google, Inc.
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

package com.netflix.spinnaker.clouddriver.google.deploy.converters

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.clouddriver.deploy.DeployAtomicOperation
import com.netflix.spinnaker.clouddriver.google.deploy.description.BasicGoogleDeployDescription
import com.netflix.spinnaker.clouddriver.google.security.GoogleNamedAccountCredentials
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider
import spock.lang.Shared
import spock.lang.Specification

class BasicGoogleDeployAtomicOperationConverterUnitSpec extends Specification {
  private static final APPLICATION = "spinnaker"
  private static final STACK = "spinnaker-test"
  private static final FREE_FORM_DETAILS = "detail"
  private static final TARGET_SIZE = 3
  private static final IMAGE = "debian-7-wheezy-v20140415"
  private static final INSTANCE_TYPE = "f1-micro"
  private static final ZONE = "us-central1-b"
  private static final INSTANCE_METADATA =
          ["startup-script": "apt-get update && apt-get install -y apache2 && hostname > /var/www/index.html",
           "testKey": "testValue"]
  private static final TAGS = ["some-tag-1", "some-tag-2", "some-tag-3"]
  private static final LOAD_BALANCERS = ["testlb1", "testlb2"]
  private static final ACCOUNT_NAME = "auto"

  @Shared
  ObjectMapper mapper = new ObjectMapper()

  @Shared
  BasicGoogleDeployAtomicOperationConverter converter

  def setupSpec() {
    this.converter = new BasicGoogleDeployAtomicOperationConverter(objectMapper: mapper)
    def accountCredentialsProvider = Mock(AccountCredentialsProvider)
    def mockCredentials = Mock(GoogleNamedAccountCredentials)
    accountCredentialsProvider.getCredentials(_) >> mockCredentials
    converter.accountCredentialsProvider = accountCredentialsProvider
  }

  void "basicGoogleDeployDescription type returns BasicGoogleDeployDescription and DeployAtomicOperation"() {
    setup:
      def input = [application: APPLICATION,
                   stack: STACK,
                   targetSize: TARGET_SIZE,
                   image: IMAGE,
                   instanceType: INSTANCE_TYPE,
                   zone: ZONE,
                   credentials: ACCOUNT_NAME]

    when:
      def description = converter.convertDescription(input)

    then:
      description instanceof BasicGoogleDeployDescription

    when:
      def operation = converter.convertOperation(input)

    then:
      operation instanceof DeployAtomicOperation
  }

  void "basicGoogleDeployDescription type with free-form details returns BasicGoogleDeployDescription and DeployAtomicOperation"() {
    setup:
      def input = [application: APPLICATION,
                   stack: STACK,
                   freeFormDetails: FREE_FORM_DETAILS,
                   targetSize: TARGET_SIZE,
                   image: IMAGE,
                   instanceType: INSTANCE_TYPE,
                   zone: ZONE,
                   credentials: ACCOUNT_NAME]

    when:
      def description = converter.convertDescription(input)

    then:
      description instanceof BasicGoogleDeployDescription

    when:
      def operation = converter.convertOperation(input)

    then:
      operation instanceof DeployAtomicOperation
  }

  void "basicGoogleDeployDescription type with instance metadata returns BasicGoogleDeployDescription and DeployAtomicOperation"() {
    setup:
      def input = [application: APPLICATION,
                   stack: STACK,
                   targetSize: TARGET_SIZE,
                   image: IMAGE,
                   instanceType: INSTANCE_TYPE,
                   zone: ZONE,
                   instanceMetadata: INSTANCE_METADATA,
                   credentials: ACCOUNT_NAME]

    when:
      def description = converter.convertDescription(input)

    then:
      description instanceof BasicGoogleDeployDescription
      description.instanceMetadata == INSTANCE_METADATA

    when:
      def operation = converter.convertOperation(input)

    then:
      operation instanceof DeployAtomicOperation
  }

  void "basicGoogleDeployDescription type with tags returns BasicGoogleDeployDescription and DeployAtomicOperation"() {
    setup:
    def input = [application: APPLICATION,
                 stack: STACK,
                 targetSize: TARGET_SIZE,
                 image: IMAGE,
                 instanceType: INSTANCE_TYPE,
                 zone: ZONE,
                 instanceMetadata: INSTANCE_METADATA,
                 tags: TAGS,
                 credentials: ACCOUNT_NAME]

    when:
    def description = converter.convertDescription(input)

    then:
    description instanceof BasicGoogleDeployDescription
    description.tags == TAGS

    when:
    def operation = converter.convertOperation(input)

    then:
    operation instanceof DeployAtomicOperation
  }

  void "basicGoogleDeployDescription type with network load balancers returns BasicGoogleDeployDescription and DeployAtomicOperation"() {
    setup:
      def input = [application: APPLICATION,
                   stack: STACK,
                   targetSize: TARGET_SIZE,
                   image: IMAGE,
                   instanceType: INSTANCE_TYPE,
                   zone: ZONE,
                   loadBalancers: LOAD_BALANCERS,
                   credentials: ACCOUNT_NAME]

    when:
      def description = converter.convertDescription(input)
      description.loadBalancers == LOAD_BALANCERS

    then:
      description instanceof BasicGoogleDeployDescription

    when:
      def operation = converter.convertOperation(input)

    then:
      operation instanceof DeployAtomicOperation
  }

  void "should not fail to serialize unknown properties"() {
    setup:
      def input = [application: application, unknownProp: "this"]

    when:
      def description = converter.convertDescription(input)

    then:
      description.application == application

    where:
      application = "kato"
  }

  void "should convert target size to ints"() {
    setup:
      def input = [application: "app", targetSize: desired]

    when:
      def description = converter.convertDescription(input)

    then:
      description.targetSize == desired as int

    where:
      desired = "8"
  }
}
