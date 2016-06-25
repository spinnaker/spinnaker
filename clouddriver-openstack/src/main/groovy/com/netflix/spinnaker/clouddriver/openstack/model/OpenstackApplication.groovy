/*
 * Copyright 2016 Target Inc.
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

package com.netflix.spinnaker.clouddriver.openstack.model

import com.fasterxml.jackson.core.type.TypeReference
import com.netflix.spinnaker.clouddriver.model.Application
import groovy.transform.EqualsAndHashCode

@EqualsAndHashCode
class OpenstackApplication implements Application {
  public static final TypeReference<Map<String, String>> ATTRIBUTES = new TypeReference<Map<String, String>>() {}
  final String name
  final Map<String, String> attributes
  final Map<String, Set<String>> clusterNames

  OpenstackApplication(String name, Map<String, String> attributes, Map<String, Set<String>> clusterNames) {
    this.name = name
    this.attributes = attributes
    this.clusterNames = clusterNames
  }
}
