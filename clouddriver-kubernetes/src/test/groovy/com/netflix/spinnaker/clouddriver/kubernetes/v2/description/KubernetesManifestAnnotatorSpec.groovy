/*
 * Copyright 2017 Google, Inc.
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
 *
 */

package com.netflix.spinnaker.clouddriver.kubernetes.v2.description

import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesManifest
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesManifestAnnotater
import com.netflix.spinnaker.moniker.Moniker
import spock.lang.Specification
import spock.lang.Unroll

class KubernetesManifestAnnotatorSpec extends Specification {
  def clusterKey = "moniker.spinnaker.io/cluster"
  def applicationKey = "moniker.spinnaker.io/application"

  private KubernetesManifest freshManifest() {
    def result = new KubernetesManifest()
    result.put("kind", "replicaSet")
    result.put("metadata", ["annotations": [:]])
    return result
  }

  @Unroll
  void "manifests are annotated and deannotated symmetrically"() {
    expect:
    def manifest = freshManifest()
    def moniker = Moniker.builder()
      .cluster(cluster)
      .app(application)
      .build()

    KubernetesManifestAnnotater.annotateManifest(manifest, moniker)
    moniker == KubernetesManifestAnnotater.getMoniker(manifest)

    where:
    loadBalancers  | securityGroups   | cluster | application
    []             | []               | ""      | ""
    []             | []               | "  "    | ""
    null           | null             | null    | null
    []             | null             | ""      | null
    ["lb"]         | ["sg"]           | ""      | null
    ["lb1", "lb2"] | ["sg"]           | "x"     | "my app"
    ["lb1", "lb2"] | null             | null    | null
    null           | ["x1, x2", "x3"] | null    | null
    ["1"]          | ["1"]            | "1"     | "1"
  }

  @Unroll
  void "manifests are annotated with the expected prefix"() {
    expect:
    def manifest = freshManifest()
    def moniker = Moniker.builder()
      .cluster(cluster)
      .app(application)
      .build()

    KubernetesManifestAnnotater.annotateManifest(manifest, moniker)
    manifest.getAnnotations().get(clusterKey) == cluster
    manifest.getAnnotations().get(applicationKey) == application

    where:
    cluster | application
    ""      | ""
    "c"     | "a"
    ""      | "a"

  }
}
