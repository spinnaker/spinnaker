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

package com.netflix.spinnaker.clouddriver.kubernetes.caching

import com.netflix.spinnaker.clouddriver.kubernetes.description.manifest.KubernetesApiVersion
import com.netflix.spinnaker.clouddriver.kubernetes.description.manifest.KubernetesKind
import spock.lang.Specification
import spock.lang.Unroll

/**
 * WARNING: if you're modifying these tests due to a key format change, you're likely
 *          breaking all user's infrastructure caches. if this is intentional, keep in mind
 *          that every user will have to flush redis to get clouddriver to run correctly
 */
class KeysSpec extends Specification {
  @Unroll
  def "produces correct app keys #key"() {
    expect:
    Keys.ApplicationCacheKey.createKey(application) == key

    where:
    application || key
    "app"       || "kubernetes.v2:logical:applications:app"
    ""          || "kubernetes.v2:logical:applications:"
  }

  @Unroll
  def "produces correct cluster keys #key"() {
    expect:
    Keys.ClusterCacheKey.createKey(account, application, cluster) == key

    where:
    account | application | cluster   || key
    "ac"    | "app"       | "cluster" || "kubernetes.v2:logical:clusters:ac:app:cluster"
    ""      | ""          | ""        || "kubernetes.v2:logical:clusters:::"
  }

  @Unroll
  def "produces correct infra keys #key"() {
    expect:
    Keys.InfrastructureCacheKey.createKey(kind, account, namespace, name) == key

    where:
    kind                       | apiVersion                              | account | namespace   | name      || key
    KubernetesKind.REPLICA_SET | KubernetesApiVersion.EXTENSIONS_V1BETA1 | "ac"    | "namespace" | "v1-v000" || "kubernetes.v2:infrastructure:replicaSet:ac:namespace:v1-v000"
    KubernetesKind.SERVICE     | KubernetesApiVersion.V1                 | "ac"    | "namespace" | "v1"      || "kubernetes.v2:infrastructure:service:ac:namespace:v1"
    KubernetesKind.DEPLOYMENT  | KubernetesApiVersion.APPS_V1BETA1       | "ac"    | "namespace" | "v1"      || "kubernetes.v2:infrastructure:deployment:ac:namespace:v1"
  }

  @Unroll
  def "unpacks application key for #name"() {
    when:
    def key = "kubernetes.v2:logical:applications:$name"
    def parsed = Keys.parseKey(key).get()

    then:
    parsed instanceof Keys.ApplicationCacheKey
    def parsedApplicationKey = (Keys.ApplicationCacheKey) parsed
    parsedApplicationKey.name == name

    where:
    name  | unused
    "app" | ""
    ""    | ""
  }

  @Unroll
  def "unpacks cluster key for '#name' and '#account'"() {
    when:
    def key = "kubernetes.v2:logical:clusters:$account:$application:$name"
    def parsed = Keys.parseKey(key).get()

    then:
    parsed instanceof Keys.ClusterCacheKey
    def parsedClusterKey = (Keys.ClusterCacheKey) parsed
    parsedClusterKey.account == account
    parsedClusterKey.application == application
    parsedClusterKey.name == name

    where:
    account | application | name
    "ac"    | ""          | "name"
    ""      | "asdf"      | "sdf"
    "ac"    | "ll"        | ""
    ""      | ""          | ""
  }

  @Unroll
  def "unpacks infrastructure key for '#kind' and '#version'"() {
    when:
    def key = "kubernetes.v2:infrastructure:$kind:$account:$namespace:$name"
    def parsed = Keys.parseKey(key).get()

    then:
    parsed instanceof Keys.InfrastructureCacheKey
    def parsedInfrastructureKey = (Keys.InfrastructureCacheKey) parsed
    parsedInfrastructureKey.kubernetesKind == kind
    parsedInfrastructureKey.account == account
    parsedInfrastructureKey.namespace == namespace
    parsedInfrastructureKey.name == name

    where:
    kind                       | version                                 | account   | namespace   | name
    KubernetesKind.DEPLOYMENT  | KubernetesApiVersion.APPS_V1BETA1       | "ac"      | "name"      | "nameer"
    KubernetesKind.REPLICA_SET | KubernetesApiVersion.EXTENSIONS_V1BETA1 | ""        | ""          | ""
    KubernetesKind.SERVICE     | KubernetesApiVersion.V1                 | "account" | "namespace" | ""
    KubernetesKind.INGRESS     | KubernetesApiVersion.EXTENSIONS_V1BETA1 | "ac"      | ""          | "nameer"
  }

  def "correctly unpacks resource names containing a ';' character"() {
    when:
    def key = "kubernetes.v2:infrastructure:clusterRole:k8s::system;controller;resourcequota-controller"
    def parsed = Keys.parseKey(key).get()

    then:
    parsed instanceof Keys.InfrastructureCacheKey
    def parsedInfrastructureKey = (Keys.InfrastructureCacheKey) parsed
    parsedInfrastructureKey.kubernetesKind == KubernetesKind.CLUSTER_ROLE
    parsedInfrastructureKey.account == "k8s"
    parsedInfrastructureKey.namespace == ""
    parsedInfrastructureKey.name == "system:controller:resourcequota-controller"
  }

  @Unroll
  def "Kind fromString returns the correct kind"() {
    expect:
    result == Keys.Kind.fromString(input)

    where:
    input               | result
    "logical"           | Keys.Kind.LOGICAL
    "LOGICAL"           | Keys.Kind.LOGICAL
    "lOgiCAl"           | Keys.Kind.LOGICAL
    "artifacT"          | Keys.Kind.ARTIFACT
    "InfraStructurE"    | Keys.Kind.INFRASTRUCTURE
    "KUBERNETES_METRIC" | Keys.Kind.KUBERNETES_METRIC
    "kubernetes_metric" | Keys.Kind.KUBERNETES_METRIC
    "kUbernetEs_meTriC" | Keys.Kind.KUBERNETES_METRIC
  }

  @Unroll
  def "Kind toString correctly serializes the kind to lowercase"() {
    expect:
    result == input.toString()

    where:
    input                       | result
    Keys.Kind.LOGICAL           | "logical"
    Keys.Kind.ARTIFACT          | "artifact"
    Keys.Kind.INFRASTRUCTURE    | "infrastructure"
    Keys.Kind.KUBERNETES_METRIC | "kubernetes_metric"
  }

  @Unroll
  def "LogicalKind toString returns the correct string"() {
    expect:
    result == Keys.LogicalKind.fromString(input)

    where:
    input          | result
    "applications" | Keys.LogicalKind.APPLICATIONS
    "APPLICATIONS" | Keys.LogicalKind.APPLICATIONS
    "appliCatiOns" | Keys.LogicalKind.APPLICATIONS
    "clusters"     | Keys.LogicalKind.CLUSTERS
    "CLUSTERS"     | Keys.LogicalKind.CLUSTERS
    "clUsTerS"     | Keys.LogicalKind.CLUSTERS
  }

  @Unroll
  def "LogicalKind toString correctly serializes the logical kind to lowercase"() {
    expect:
    result == input.toString()

    where:
    input                         | result
    Keys.LogicalKind.APPLICATIONS | "applications"
    Keys.LogicalKind.CLUSTERS     | "clusters"
  }

  def "serialization and deserialization are inverses"() {
    when:
    def parsed = Keys.parseKey(key).get()

    then:
    parsed.toString() == key

    where:
    key << [
      "kubernetes.v2:artifact:kubernetes/replicaSet:spinnaker-io:docs-site:v046",
      "kubernetes.v2:infrastructure:secret:k8s:spin:spinnaker",
      "kubernetes.v2:logical:applications:spinnaker",
      "kubernetes.v2:logical:clusters:k8s:docs:docs-site"
    ]
  }
}
