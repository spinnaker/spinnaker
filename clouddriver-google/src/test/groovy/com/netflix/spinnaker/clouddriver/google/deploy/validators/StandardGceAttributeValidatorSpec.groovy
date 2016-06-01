/*
 * Copyright 2015 Google, Inc.
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

package com.netflix.spinnaker.clouddriver.google.deploy.validators

import com.netflix.spinnaker.clouddriver.google.security.GoogleNamedAccountCredentials
import com.netflix.spinnaker.clouddriver.security.DefaultAccountCredentialsProvider
import com.netflix.spinnaker.clouddriver.security.MapBackedAccountCredentialsRepository
import org.springframework.validation.Errors
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

class StandardGceAttributeValidatorSpec extends Specification {
  private static final ACCOUNT_NAME = "auto"
  private static final DECORATOR = "decorator"
  private static final ALL_REGIONS = [
    "us-central1", "europe-west1", "asia-east1",
  ]
  private static final US_ZONES = [
    "us-central1-a", "us-central1-b", "us-central1-c", "us-central1-f",
  ]
  private static final EURO_ZONES = [
    "europe-west1-a", "europe-west1-b", "europe-west1-c", "europe-west1-d",
  ]
  private static final ASIA_ZONES = [
    "asia-east1-a", "asia-east1-b", "asia-east1-c",
  ]
  private static final ALL_ZONES = US_ZONES + EURO_ZONES + ASIA_ZONES

  @Shared
  DefaultAccountCredentialsProvider accountCredentialsProvider

  void setupSpec() {
    def credentialsRepo = new MapBackedAccountCredentialsRepository()
    accountCredentialsProvider = new DefaultAccountCredentialsProvider(credentialsRepo)
    def credentials = new GoogleNamedAccountCredentials.Builder().name(ACCOUNT_NAME).regionLookupEnabled(false).build()
    credentialsRepo.save(ACCOUNT_NAME, credentials)
  }

  void "generic non-empty ok"() {
    setup:
      def errors = Mock(Errors)
      def validator = new StandardGceAttributeValidator(DECORATOR, errors)
      def label = "testAttribute"

    when:
      validator.validateNotEmpty("something", label)
    then:
      0 * errors._

    when:
      validator.validateNotEmpty(["something"], label)
    then:
      0 * errors._

    when:
      // Even though the list contains empty elements, the list itself is not empty.
      validator.validateNotEmpty([""], label)
    then:
      0 * errors._

    when:
      // Even though the list contains null elements, the list itself is not empty.
      validator.validateNotEmpty([null], label)
    then:
      0 * errors._
  }

  @Unroll
  void "expect non-empty ok with numeric values"() {
    setup:
    def errors = Mock(Errors)
    def validator = new StandardGceAttributeValidator(DECORATOR, errors)
    def label = "testAttribute"

    when:
    validator.validateNotEmpty(new Integer(intValue), label)
    then:
    0 * errors._

    where:
    intValue << [-1, 0, 1]
  }

  void "expect non-empty to fail with empty"() {
    setup:
      def errors = Mock(Errors)
      def validator = new StandardGceAttributeValidator(DECORATOR, errors)
      def label = "testAttribute"

    when:
      validator.validateNotEmpty(null, label)
    then:
      1 * errors.rejectValue(label, "${DECORATOR}.${label}.empty")
      0 * errors._

    when:
      validator.validateNotEmpty([], label)
    then:
      1 * errors.rejectValue(label, "${DECORATOR}.${label}.empty")
      0 * errors._
  }

  void "nonNegativeInt ok if non-negative"() {
    setup:
      def errors = Mock(Errors)
      def validator = new StandardGceAttributeValidator(DECORATOR, errors)
      def label = "testAttribute"

    when:
      validator.validateNonNegativeLong(0, label)
    then:
      0 * errors._

    when:
      validator.validateNonNegativeLong(1, label)
    then:
      0 * errors._

    when:
      validator.validateNonNegativeLong(1 << 30, label)  // unlimited
    then:
      0 * errors._
  }

  void "nonNegativeInt invalid if negative"() {
    setup:
      def errors = Mock(Errors)
      def validator = new StandardGceAttributeValidator(DECORATOR, errors)
      def label = "testAttribute"

    when:
      validator.validateNonNegativeLong(-1, label)
    then:
      1 * errors.rejectValue(label, "${DECORATOR}.${label}.negative")
      0 * errors._
  }

  void "valid generic name"() {
    setup:
      def errors = Mock(Errors)
      def validator = new StandardGceAttributeValidator(DECORATOR, errors)

    when:
      validator.validateName("Unchecked", "label")
    then:
      0 * errors._

    when:
      validator.validateName(" ", "label")
    then:
      0 * errors._
  }

  void "invalid generic name"() {
    setup:
      def label = "label"
      def errors = Mock(Errors)
      def validator = new StandardGceAttributeValidator(DECORATOR, errors)

    when:
      validator.validateName("", label)
    then:
      1 * errors.rejectValue(label, "${DECORATOR}.${label}.empty")
      0 * errors._
  }

  void "validate simple account name"() {
    setup:
      def errors = Mock(Errors)
      def validator = new StandardGceAttributeValidator(DECORATOR, errors)

    when:
      validator.validateCredentials(ACCOUNT_NAME, accountCredentialsProvider)
    then:
      0 * errors._
  }

  void "empty account name"() {
    setup:
      def errors = Mock(Errors)
      def validator = new StandardGceAttributeValidator(DECORATOR, errors)

    when:
      validator.validateCredentials(null, accountCredentialsProvider)
    then:
      1 * errors.rejectValue("credentials", "${DECORATOR}.credentials.empty")
      0 * errors._

    when:
      validator.validateCredentials("", accountCredentialsProvider)
    then:
      1 * errors.rejectValue("credentials", "${DECORATOR}.credentials.empty")
      0 * errors._
  }

  void "unknown account name"() {
    setup:
      def errors = Mock(Errors)
      def validator = new StandardGceAttributeValidator(DECORATOR, errors)

    when:
      validator.validateCredentials("Unknown", accountCredentialsProvider)

    then:
      1 * errors.rejectValue("credentials", "${DECORATOR}.credentials.invalid")
      0 * errors._
  }

  void "valid server group name"() {
    setup:
      def errors = Mock(Errors)
      def validator = new StandardGceAttributeValidator(DECORATOR, errors)

    when:
      validator.validateServerGroupName("Unchecked ")
    then:
      0 * errors._

    when:
      validator.validateServerGroupName(" ")
    then:
      0 * errors._
  }

  void "invalid server group name"() {
    setup:
      def errors = Mock(Errors)
      def validator = new StandardGceAttributeValidator(DECORATOR, errors)

    when:
      validator.validateServerGroupName("")
    then:
      1 * errors.rejectValue("serverGroupName", "${DECORATOR}.serverGroupName.empty")
      0 * errors._
  }

  void "valid region name"() {
    setup:
      def errors = Mock(Errors)
      def validator = new StandardGceAttributeValidator(DECORATOR, errors)

    when:
      ALL_REGIONS.each { validator.validateRegion(it) }
    then:
      0 * errors._

    when:
      validator.validateRegion("Unchecked")
    then:
      0 * errors._

    when:
      validator.validateRegion(" ")
    then:
      0 * errors._
  }

  void "invalid region name"() {
    setup:
      def errors = Mock(Errors)
      def validator = new StandardGceAttributeValidator(DECORATOR, errors)

    when:
      validator.validateRegion("")
    then:
      1 * errors.rejectValue("region", "${DECORATOR}.region.empty")
      0 * errors._
  }

  void "valid zone name"() {
    setup:
      def errors = Mock(Errors)
      def validator = new StandardGceAttributeValidator(DECORATOR, errors)

    when:
      ALL_ZONES.each { validator.validateZone(it) }
    then:
      0 * errors._

    when:
      validator.validateZone("Unchecked")
    then:
      0 * errors._

    when:
      validator.validateZone(" ")
    then:
      0 * errors._
  }

  void "invalid zone name"() {
    setup:
      def errors = Mock(Errors)
      def validator = new StandardGceAttributeValidator(DECORATOR, errors)

    when:
      validator.validateZone("")
    then:
      1 * errors.rejectValue("zone", "${DECORATOR}.zone.empty")
      0 * errors._
  }

  void "valid network name"() {
    setup:
      def errors = Mock(Errors)
      def validator = new StandardGceAttributeValidator(DECORATOR, errors)

    when:
      validator.validateNetwork("Unchecked")
    then:
      0 * errors._

    when:
      validator.validateNetwork(" ")
    then:
      0 * errors._
  }

  void "invalid network name"() {
    setup:
      def errors = Mock(Errors)
      def validator = new StandardGceAttributeValidator(DECORATOR, errors)

    when:
      validator.validateNetwork("")
    then:
      1 * errors.rejectValue("network", "${DECORATOR}.network.empty")
      0 * errors._
  }

  void "valid image name"() {
    setup:
      def errors = Mock(Errors)
      def validator = new StandardGceAttributeValidator(DECORATOR, errors)

    when:
      validator.validateImage("Unchecked")
    then:
      0 * errors._

    when:
      validator.validateImage(" ")
    then:
      0 * errors._
  }

  void "invalid image name"() {
    setup:
      def errors = Mock(Errors)
      def validator = new StandardGceAttributeValidator(DECORATOR, errors)

    when:
      validator.validateImage("")
    then:
      1 * errors.rejectValue("image", "${DECORATOR}.image.empty")
      0 * errors._
  }

  void "valid instance name"() {
    setup:
      def errors = Mock(Errors)
      def validator = new StandardGceAttributeValidator(DECORATOR, errors)

    when:
      validator.validateInstanceName("Unchecked")
    then:
      0 * errors._

    when:
      validator.validateInstanceName(" ")
    then:
      0 * errors._
  }

  void "invalid instance name"() {
    setup:
      def errors = Mock(Errors)
      def validator = new StandardGceAttributeValidator(DECORATOR, errors)

    when:
      validator.validateInstanceName("")
    then:
      1 * errors.rejectValue("instanceName", "${DECORATOR}.instanceName.empty")
      0 * errors._
  }

  void "valid instance type"() {
    setup:
      def errors = Mock(Errors)
      def validator = new StandardGceAttributeValidator(DECORATOR, errors)

    when:
      validator.validateInstanceType("Unchecked")
    then:
      0 * errors._

    when:
      validator.validateInstanceType(" ")
    then:
      0 * errors._
  }

  void "invalid instance type"() {
    setup:
      def errors = Mock(Errors)
      def validator = new StandardGceAttributeValidator(DECORATOR, errors)

    when:
      validator.validateInstanceType("")
    then:
      1 * errors.rejectValue("instanceType", "${DECORATOR}.instanceType.empty")
      0 * errors._
  }

  void "valid name list"() {
    setup:
      def errors = Mock(Errors)
      def validator = new StandardGceAttributeValidator(DECORATOR, errors)

    when:
      validator.validateNameList(["Unchecked"], "loadBalancerName")
      then:
      0 * errors._

    when:
      validator.validateNameList([" "], "loadBalancerName")
    then:
      0 * errors._

    when:
      validator.validateNameList(["a", "b", "c"], "loadBalancerName")
    then:
      0 * errors._
  }

  void "invalid name list"() {
    setup:
      def errors = Mock(Errors)
      def validator = new StandardGceAttributeValidator(DECORATOR, errors)

    when:
      validator.validateNameList([], "loadBalancerName")
    then:
      1 * errors.rejectValue("loadBalancerNames", "${DECORATOR}.loadBalancerNames.empty")
      0 * errors._

    when:
      validator.validateNameList([""], "loadBalancerName")
    then:
      1 * errors.rejectValue("loadBalancerNames", "${DECORATOR}.loadBalancerName0.empty")
      0 * errors._
  }

  void "mixed valid/invalid name list"() {
    setup:
      def errors = Mock(Errors)
      def validator = new StandardGceAttributeValidator(DECORATOR, errors)

    when:
      validator.validateNameList(["good", ""], "loadBalancerName")
    then:
      1 * errors.rejectValue("loadBalancerNames", "${DECORATOR}.loadBalancerName1.empty")
      0 * errors._

    when:
      validator.validateNameList(["good", "", "another", ""], "loadBalancerName")
    then:
      1 * errors.rejectValue("loadBalancerNames", "${DECORATOR}.loadBalancerName1.empty")
      1 * errors.rejectValue("loadBalancerNames", "${DECORATOR}.loadBalancerName3.empty")
      0 * errors._
  }

  void "valid instance ids"() {
    setup:
      def errors = Mock(Errors)
      def validator = new StandardGceAttributeValidator(DECORATOR, errors)

    when:
      validator.validateInstanceIds(["Unchecked"])
    then:
      0 * errors._

    when:
      validator.validateInstanceIds([" "])
    then:
      0 * errors._

    when:
      validator.validateInstanceIds(["a", "b", "c"])
    then:
      0 * errors._
  }

  void "invalid instance ids"() {
    setup:
      def errors = Mock(Errors)
      def validator = new StandardGceAttributeValidator(DECORATOR, errors)

    when:
      validator.validateInstanceIds([])
    then:
      1 * errors.rejectValue("instanceIds", "${DECORATOR}.instanceIds.empty")
      0 * errors._

    when:
      validator.validateInstanceIds([""])
    then:
      1 * errors.rejectValue("instanceIds", "${DECORATOR}.instanceId0.empty")
      0 * errors._
  }

  void "mixed valid/invalid instance ids"() {
    setup:
      def errors = Mock(Errors)
      def validator = new StandardGceAttributeValidator(DECORATOR, errors)

    when:
      validator.validateInstanceIds(["good", ""])
    then:
      1 * errors.rejectValue("instanceIds", "${DECORATOR}.instanceId1.empty")
      0 * errors._

    when:
      validator.validateInstanceIds(["good", "", "another", ""])
    then:
      1 * errors.rejectValue("instanceIds", "${DECORATOR}.instanceId1.empty")
      1 * errors.rejectValue("instanceIds", "${DECORATOR}.instanceId3.empty")
      0 * errors._
  }
}
