/*
 * Copyright 2019 Google, Inc.
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
 *
 */

package com.netflix.spinnaker.clouddriver.kubernetes.description

import com.netflix.spinnaker.clouddriver.kubernetes.description.manifest.KubernetesApiGroup
import com.netflix.spinnaker.clouddriver.kubernetes.description.manifest.KubernetesApiVersion
import spock.lang.Specification
import spock.lang.Unroll

class KubernetesApiVersionSpec extends Specification {
  @Unroll
  void "creates built-in API versions by name"() {
    when:
    def apiVersion = KubernetesApiVersion.fromString(name)

    then:
    apiVersion.equals(expectedApiGroup)

    where:
    name                        | expectedApiGroup
    null                        | KubernetesApiVersion.NONE
    ""                          | KubernetesApiVersion.NONE
    "v1"                        | KubernetesApiVersion.V1
    "networking.k8s.io/v1beta1" | KubernetesApiVersion.NETWORKING_K8S_IO_V1BETA1
    "neTwoRkiNG.k8s.io/v1beTA1" | KubernetesApiVersion.NETWORKING_K8S_IO_V1BETA1
  }

  @Unroll
  void "creates custom API versions"() {
    when:
    def apiVersion = KubernetesApiVersion.fromString(name)

    then:
    noExceptionThrown()
    apiVersion.toString() == expectedName

    where:
    name                     | expectedName
    "test.api.group"         | "test.api.group"
    "test.api.group/version" | "test.api.group/version"
  }

  @Unroll
  void "correctly parses the group from the version"() {
    when:
    def apiVersion = KubernetesApiVersion.fromString(name)

    then:
    apiVersion.getApiGroup().equals(expectedGroup)

    where:
    name                     | expectedGroup
    null                     | KubernetesApiGroup.NONE
    ""                       | KubernetesApiGroup.NONE
    "test.api.group"         | KubernetesApiGroup.NONE
    "test.api.group/version" | KubernetesApiGroup.fromString("test.api.group")
    "apps/v1beta1"           | KubernetesApiGroup.APPS
  }
}
