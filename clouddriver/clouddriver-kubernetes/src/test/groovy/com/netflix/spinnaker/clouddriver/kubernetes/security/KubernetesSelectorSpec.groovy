/*
 * Copyright 2017 Google, Inc.
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

package com.netflix.spinnaker.clouddriver.kubernetes.security

import spock.lang.Specification
import spock.lang.Unroll

class KubernetesSelectorSpec extends Specification {
  @Unroll
  void "correctly turns the desired selector into a k8s selector string (#result)"() {
    expect:
    selector.toString() == result

    where:
    selector                                             || result
    KubernetesSelector.any()                             || ""
    KubernetesSelector.equals("a", "b")                  || "a = b"
    KubernetesSelector.equals("xy", "b")                 || "xy = b"
    KubernetesSelector.notEquals("a", "b")               || "a != b"
    KubernetesSelector.notEquals("xy", "b")              || "xy != b"
    KubernetesSelector.contains("a", ["b"])              || "a in (b)"
    KubernetesSelector.contains("a", ["b", "c", "d"])    || "a in (b, c, d)"
    KubernetesSelector.notContains("a", ["b"])           || "a notin (b)"
    KubernetesSelector.notContains("a", ["b", "c", "d"]) || "a notin (b, c, d)"
    KubernetesSelector.exists("a")                       || "a"
    KubernetesSelector.exists("abc")                     || "abc"
    KubernetesSelector.notExists("a")                    || "!a"
    KubernetesSelector.notExists("abc")                  || "!abc"
  }
}
