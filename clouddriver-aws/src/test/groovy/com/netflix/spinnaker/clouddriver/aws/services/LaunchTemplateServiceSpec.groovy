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
package com.netflix.spinnaker.clouddriver.aws.services

import com.amazonaws.services.ec2.AmazonEC2;
import com.netflix.spinnaker.clouddriver.aws.deploy.userdata.LocalFileUserDataProperties;
import com.netflix.spinnaker.clouddriver.aws.deploy.userdata.UserDataProviderAggregator;
import com.netflix.spinnaker.clouddriver.aws.model.AmazonBlockDevice
import com.netflix.spinnaker.clouddriver.aws.services.LaunchTemplateService
import spock.lang.Specification
import spock.lang.Unroll

class LaunchTemplateServiceSpec extends Specification {

  def launchTemplateService = new LaunchTemplateService(
    Mock(AmazonEC2),
    Mock(UserDataProviderAggregator),
    Mock(LocalFileUserDataProperties)
  )

  void 'should match ebs encryption'() {
    when:
    def result = launchTemplateService.getLaunchTemplateEbsBlockDeviceRequest(blockDevice)

    then:
    result.getEncrypted() == encrypted && result.getKmsKeyId() == kmsKeyId

    where:
    blockDevice                                             | encrypted | kmsKeyId
    new AmazonBlockDevice()                                 | null      | null
    new AmazonBlockDevice(encrypted: true)                  | true      | null
    new AmazonBlockDevice(encrypted: true, kmsKeyId: "xxx") | true      | "xxx"
  }
}
