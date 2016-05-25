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

package com.netflix.spinnaker.clouddriver.titus.deploy.description

import com.netflix.spinnaker.clouddriver.deploy.DeployDescription
import groovy.transform.Canonical

class TitusDeployDescription extends AbstractTitusCredentialsDescription implements DeployDescription {
  String account
  String region
  String subnet
  List<String> zones
  List<String> securityGroups
  String application
  String stack
  String freeFormDetails
  String imageId
  Capacity capacity = new Capacity()
  Resources resources = new Resources()
  Map env
  Map labels
  String entryPoint
  String iamProfile
  String user

  @Canonical
  static class Capacity {
    int min
    int max
    int desired
  }

  @Canonical
  static class Resources {
    int cpu
    int memory
    int disk
    int[] ports
    boolean allocateIpAddress
  }
}
