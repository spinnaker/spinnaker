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


package com.netflix.spinnaker.gate.model.discovery

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonRootName
import com.netflix.frigga.Names
import groovy.transform.EqualsAndHashCode

@JsonRootName("application")
@EqualsAndHashCode
class DiscoveryApplication {

  String name

  @JsonProperty('instance')
  List<DiscoveryInstance> instances

  DiscoveryInstance getRandomUpInstance() {
    if (!this.instances) return null
    def instances = []
    instances.addAll(this.instances)
    Collections.shuffle(instances)
    instances.find { it.status == 'UP' }
  }

  DiscoveryInstance getRandomUpInstance(String stack) {
    if (!this.instances) return null
    def instances = []
    instances.addAll(this.instances)
    Collections.shuffle(instances)
    instances.find { DiscoveryInstance instance ->
      def names = Names.parseName(instance.asgName)
      instance.status == 'UP' && names.stack == stack
    }
  }
}

