/*
 * Copyright 2021 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.aws.deploy.asg

import com.netflix.spinnaker.clouddriver.aws.TestCredential
import com.netflix.spinnaker.kork.dynamicconfig.DynamicConfigService
import spock.lang.Specification
import spock.lang.Unroll

class LaunchTemplateRollOutConfigSpec extends Specification {
  def dynamicConfigService = Mock(DynamicConfigService)
  def launchTemplateRollOutConfig = new LaunchTemplateRollOutConfig(dynamicConfigService)

  void 'isIpv6EnabledForEnv returns expected results'() {
    when:
    def res = launchTemplateRollOutConfig.isIpv6EnabledForEnv("foo")

    then:
    1 * dynamicConfigService.isEnabled('aws.features.launch-templates.ipv6.foo', false) >> enabled

    and:
    res == enabled

    where:
    enabled << [true, false]
  }

  @Unroll
  void 'shouldUseLaunchTemplateForReq returns expected resultsrr'() {
    given:
    def excludedApp = "excludedApp"
    def excludedAcc = "excludedAcc"
    def allowAllApps = false

    def allowedApp = "myasg"
    def allowedAcc = "foo"
    def allowedReg = "us-east-1"

    when:
    def res = launchTemplateRollOutConfig.shouldUseLaunchTemplateForReq(app, TestCredential.named(account), region)

    then:
    callCounts[0] * dynamicConfigService.isEnabled('aws.features.launch-templates', false) >> ltEnabled

    callCounts[1] * dynamicConfigService.getConfig(String.class,"aws.features.launch-templates.excluded-applications", "") >> excludedApp
    callCounts[2] * dynamicConfigService.getConfig(String.class, "aws.features.launch-templates.excluded-accounts", "") >> excludedAcc
    callCounts[3] * dynamicConfigService.isEnabled('aws.features.launch-templates.all-applications', false) >> allowAllApps
    callCounts[4] * dynamicConfigService.getConfig(String.class,"aws.features.launch-templates.allowed-applications", "") >> allowedApp + ":" + allowedAcc + ":" + allowedReg
    callCounts[5] * dynamicConfigService.getConfig(String.class, "aws.features.launch-templates.allowed-accounts-regions", "") >> allowedAcc + ":" + allowedReg
    callCounts[6] * dynamicConfigService.getConfig(String.class, "aws.features.launch-templates.allowed-accounts", "") >> allowedAcc
    0 * dynamicConfigService._

    and:
    res == result

    where:
    ltEnabled |     app     |   region     |  account    |   callCounts    || result
      false   |   "myasg"   |  "us-east-1" |   "foo"     | [1,0,0,0,0,0,0] || false
      true    |"excludedApp"|  "us-east-1" |   "foo"     | [1,1,0,0,0,0,0] || false
      true    |   "myasg"   |  "us-east-1" |"excludedAcc"| [1,1,1,0,0,0,0] || false
      true    |   "myasg"   |  "us-east-1" |   "foo"     | [1,1,1,1,1,0,0] || true
      true    |   "asg"     |  "us-east-1" |   "foo"     | [1,1,1,1,1,1,0] || true
      true    |   "asg"     |  "us-west-1" |   "foo"     | [1,1,1,1,1,1,1] || true
      true    |   "asg"     |  "us-east-1" |   "acc"     | [1,1,1,1,1,1,1] || false
  }

  @Unroll
  void "should check if current app, account and region match launch template flag"() {
    when:
    def result = launchTemplateRollOutConfig.matchesAppAccountAndRegion(application, accountName, region, applicationAccountRegions)

    then:
    result == matches

    where:
    applicationAccountRegions           | application   | accountName | region      || matches
    "foo:test:us-east-1"                | "foo"         | "test"      | "us-east-1" || true
    "foo:test:us-east-1,us-west-2"      | "foo"         | "test"      | "eu-west-1" || false
    "foo:prod:us-east-1"                | "foo"         | "test"      | "us-east-1" || false
  }
}
