/*
 * Copyright 2016 Veritas Technologies LLC.
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

package com.netflix.spinnaker.clouddriver.openstack.config

import com.netflix.spinnaker.clouddriver.consul.config.ConsulConfig
import groovy.transform.ToString

@ToString(includeNames = true)
class OpenstackConfigurationProperties {

  @ToString(includeNames = true, excludes = "password")
  static class ManagedAccount {
    String name
    String environment
    String accountType
    String username
    String password
    String projectName
    String domainName
    String authUrl
    List<String> regions
    Boolean insecure
    String heatTemplatePath
    LbaasConfig lbaas
    ConsulConfig consul
    String userDataFile
  }

  static class LbaasConfig {
    Integer pollTimeout
    Integer pollInterval
  }

  List<ManagedAccount> accounts = []
}
