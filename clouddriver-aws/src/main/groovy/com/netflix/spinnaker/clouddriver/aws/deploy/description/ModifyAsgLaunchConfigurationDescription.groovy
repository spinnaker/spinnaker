/*
 * Copyright 2015 Netflix, Inc.
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

package com.netflix.spinnaker.clouddriver.aws.deploy.description

import com.netflix.spinnaker.clouddriver.aws.model.AmazonBlockDevice
import com.netflix.spinnaker.clouddriver.security.resources.ServerGroupsNameable
import groovy.transform.AutoClone
import groovy.transform.Canonical

@AutoClone
@Canonical
class ModifyAsgLaunchConfigurationDescription extends AbstractAmazonCredentialsDescription implements ServerGroupsNameable {
  String region
  String asgName
  String amiName
  String instanceType
  String subnetType
  String iamRole
  String keyPair
  Boolean associatePublicIpAddress
  String spotPrice
  String ramdiskId
  Boolean instanceMonitoring
  Boolean ebsOptimized
  String classicLinkVpcId
  List<String> classicLinkVpcSecurityGroups
  Boolean legacyUdf
  String base64UserData

  List<AmazonBlockDevice> blockDevices
  List<String> securityGroups
  Boolean securityGroupsAppendOnly

  /**
   * If false, the newly created server group will not pick up block device mapping customizations from an ancestor group
   */
  boolean copySourceCustomBlockDeviceMappings = true

  @Override
  Collection<String> getServerGroupNames() {
    return [asgName]
  }
}
