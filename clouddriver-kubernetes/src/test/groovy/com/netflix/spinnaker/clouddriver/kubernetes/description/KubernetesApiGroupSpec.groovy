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
import spock.lang.Specification
import spock.lang.Unroll

class KubernetesApiGroupSpec extends Specification {
  @Unroll
  void "creates built-in API groups by name"() {
    when:
    def apiGroup = KubernetesApiGroup.fromString(name)

    then:
    apiGroup.equals(expectedApiGroup)

    where:
    name              | expectedApiGroup
    null              | KubernetesApiGroup.NONE
    ""                | KubernetesApiGroup.NONE
    "batch"           | KubernetesApiGroup.BATCH
    "BATCH"           | KubernetesApiGroup.BATCH
    "settings.k8s.io" | KubernetesApiGroup.SETTINGS_K8S_IO
    "seTtiNgs.k8S.IO" | KubernetesApiGroup.SETTINGS_K8S_IO
  }

  @Unroll
  void "creates custom API groups"() {
    when:
    def apiGroup = KubernetesApiGroup.fromString(name)

    then:
    noExceptionThrown()
    apiGroup.toString() == expectedName

    where:
    name             | expectedName
    "test.api.group" | "test.api.group"
    "TEST.api.Group" | "test.api.group"
  }

  @Unroll
  void "returns whether an API group is a native group"() {
    when:
    def apiGroup = KubernetesApiGroup.fromString(name)

    then:
    apiGroup.isNativeGroup() == isNative

    where:
    name             | isNative
    "test.api.group" | false
    "batch"          | true
    "apps"           | true
    ""               | true
  }
}
