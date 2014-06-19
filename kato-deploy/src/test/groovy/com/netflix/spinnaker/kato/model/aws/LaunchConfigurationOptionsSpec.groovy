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
package com.netflix.spinnaker.kato.model.aws

import com.amazonaws.services.autoscaling.model.*
import spock.lang.Specification

class LaunchConfigurationOptionsSpec extends Specification {
  LaunchConfigurationOptions lcOptions = new LaunchConfigurationOptions(
    launchConfigurationName: 'launchConfigurationName1',
    imageId: 'imageId1',
    keyName: 'keyName1',
    securityGroups: ['sg-1'],
    userData: 'userData1',
    instanceType: 'instanceType1',
    kernelId: 'kernelId1',
    ramdiskId: 'ramdiskId1',
    blockDeviceMappings: [new BlockDeviceMapping(deviceName: 'deviceName1', ebs: new Ebs(volumeSize: 256))],
    instanceMonitoringIsEnabled: false,
    instancePriceType: InstancePriceType.ON_DEMAND,
    iamInstanceProfile: 'iamInstanceProfile1',
    ebsOptimized: false
  )

  LaunchConfiguration awsLaunchConfiguration = new LaunchConfiguration(
    launchConfigurationName: 'launchConfigurationName1',
    imageId: 'imageId1',
    keyName: 'keyName1',
    securityGroups: ['sg-1'],
    userData: 'userData1',
    instanceType: 'instanceType1',
    kernelId: 'kernelId1',
    ramdiskId: 'ramdiskId1',
    blockDeviceMappings: [new BlockDeviceMapping(deviceName: 'deviceName1', ebs: new Ebs(volumeSize: 256))],
    instanceMonitoring: new InstanceMonitoring(enabled: false),
    iamInstanceProfile: 'iamInstanceProfile1',
    ebsOptimized: false
  )
  CreateLaunchConfigurationRequest createLaunchConfigurationRequest = new CreateLaunchConfigurationRequest(
    launchConfigurationName: 'launchConfigurationName1',
    imageId: 'imageId1',
    keyName: 'keyName1',
    securityGroups: ['sg-1'],
    userData: 'userData1',
    instanceType: 'instanceType1',
    kernelId: 'kernelId1',
    ramdiskId: 'ramdiskId1',
    blockDeviceMappings: [new BlockDeviceMapping(deviceName: 'deviceName1', ebs: new Ebs(volumeSize: 256))],
    instanceMonitoring: new InstanceMonitoring().withEnabled(false),
    iamInstanceProfile: 'iamInstanceProfile1',
    ebsOptimized: false
  )

  def 'getCreateLaunchConfigurationRequest should have instance monitoring turned off by default'() {
    def request = lcOptions.getCreateLaunchConfigurationRequest()

    expect:
    request.instanceMonitoring != null
  }

  def 'should deep copy'() {
    when:
    LaunchConfigurationOptions actualLc = LaunchConfigurationOptions.from(lcOptions)

    then:
    lcOptions == actualLc

    when:
    actualLc.blockDeviceMappings.iterator().next().deviceName = 'deviceName2'

    then:
    lcOptions != actualLc
  }

  def 'should copy with null collection'() {
    lcOptions.securityGroups = null

    when:
    LaunchConfigurationOptions actualLc = LaunchConfigurationOptions.from(lcOptions)

    then:
    lcOptions == actualLc

    when:
    actualLc.blockDeviceMappings.iterator().next().deviceName = 'deviceName2'

    then:
    lcOptions != actualLc
  }

  def 'should create from AWS LaunchConfiguration'() {
    expect:
    LaunchConfigurationOptions.from(awsLaunchConfiguration) == lcOptions
  }

  def 'should create from AWS LaunchConfiguration with spot price'() {
    awsLaunchConfiguration.spotPrice = '100'
    lcOptions.instancePriceType = InstancePriceType.SPOT

    expect:
    LaunchConfigurationOptions.from(awsLaunchConfiguration) == lcOptions
  }

  def 'should create CreateLaunchConfigurationRequest'() {
    expect:
    lcOptions.getCreateLaunchConfigurationRequest() == createLaunchConfigurationRequest
  }

}

