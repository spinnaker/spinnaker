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


package com.netflix.spinnaker.kato.deploy.aws.description

import com.netflix.spinnaker.kato.deploy.DeployDescription
import groovy.transform.AutoClone

@AutoClone
class BasicAmazonDeployDescription extends AbstractAmazonCredentialsDescription implements DeployDescription {
  String application
  String amiName
  String stack
  String instanceType
  String vpcId
  String subnetType
  String iamRole
  String keyPair
  List<String> loadBalancers
  List<String> securityGroups
  Map<String, List<String>> availabilityZones = [:]
  Capacity capacity = new Capacity()

  static class Capacity {
    int min
    int max
    int desired
  }
}
