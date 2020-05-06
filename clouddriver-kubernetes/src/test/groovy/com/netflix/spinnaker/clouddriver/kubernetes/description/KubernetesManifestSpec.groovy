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

package com.netflix.spinnaker.clouddriver.kubernetes.description

import com.fasterxml.jackson.databind.ObjectMapper
import com.google.gson.Gson
import com.netflix.spinnaker.clouddriver.kubernetes.description.manifest.KubernetesApiVersion
import com.netflix.spinnaker.clouddriver.kubernetes.description.manifest.KubernetesKind
import com.netflix.spinnaker.clouddriver.kubernetes.description.manifest.KubernetesManifest
import groovy.text.SimpleTemplateEngine
import spock.lang.Specification
import spock.lang.Unroll

class KubernetesManifestSpec extends Specification {
  def objectMapper = new ObjectMapper()

  def gsonObj = new Gson()
  def NAME = "my-name"
  def NAMESPACE = "my-namespace"
  def KIND = KubernetesKind.REPLICA_SET
  def API_VERSION = KubernetesApiVersion.EXTENSIONS_V1BETA1
  def KEY = "hi"
  def VALUE = "there"
  def CRD_NAME = "default-custom1"
  def CRD_KIND = "Custom1"
  def CRD_API_VERSION = "test.example/v1alpha1"

  String basicManifestSource() {
    def sourceJson = KubernetesManifest.class.getResource("manifest.json").getText("utf-8")
    def templateEngine = new SimpleTemplateEngine()
    def binding = [
      "name": getNAME(),
      "namespace": getNAMESPACE(),
      "api_version": getAPI_VERSION(),
      "key": getKEY(),
      "value": getVALUE(),
      "kind": getKIND()
    ]
    def template = templateEngine.createTemplate(sourceJson).make(binding)
    return template.toString()
  }


  KubernetesManifest objectToManifest(Object input) {
    return objectMapper.convertValue(input, KubernetesManifest)
  }

  void "correctly reads fields from basic manifest definition"() {
    when:
    def testPayload =  gsonObj.fromJson(basicManifestSource(), Object)
    KubernetesManifest manifest = objectToManifest(testPayload)

    then:
    manifest.getName() == NAME
    manifest.getNamespace() == NAMESPACE
    manifest.getKind() == KIND
    manifest.getApiVersion() == API_VERSION
    manifest.getSpecTemplateAnnotations().get().get(KEY) == VALUE
  }

  @Unroll
  void "correctly parses a fully qualified resource name #kind/#name"() {
    expect:
    def pair = KubernetesManifest.fromFullResourceName(fullResourceName)
    pair.getRight() == name
    pair.getLeft() == kind

    where:
    fullResourceName || kind                       | name
    "replicaSet abc" || KubernetesKind.REPLICA_SET | "abc"
    "rs abc"         || KubernetesKind.REPLICA_SET | "abc"
    "service abc"    || KubernetesKind.SERVICE     | "abc"
    "SERVICE abc"    || KubernetesKind.SERVICE     | "abc"
    "ingress abc"    || KubernetesKind.INGRESS     | "abc"
  }

  void "correctly reads observedGeneration from status"() {
    when:
    def statusJson = """
{
  "status": {
     "observedGeneration": 1
  }
}
"""

    def testStatusJson = gsonObj.fromJson(statusJson, Object)
    def testPayload = gsonObj.fromJson(basicManifestSource(), Object)
    KubernetesManifest manifest = objectToManifest(testPayload << testStatusJson)

    then:
    manifest.getObservedGeneration() == 1
  }

  void "correctly reads generation from manifest"() {
    when:
    def testPayload =  gsonObj.fromJson(basicManifestSource(), Object)
    KubernetesManifest manifest = objectToManifest(testPayload)

    then:
    manifest.getGeneration() == 3
  }

  void "correctly determines isNewerThanObservedGeneration"() {
    when:
    def statusJson = """
{
  "status": {
     "observedGeneration": 1
  }
}
"""

    def testStatusJson = gsonObj.fromJson(statusJson, Object)
    def testPayload = gsonObj.fromJson(basicManifestSource(), Object)
    KubernetesManifest manifest = objectToManifest(testPayload << testStatusJson)
    then:
    manifest.isNewerThanObservedGeneration()
  }

  void "correctly handles a change to the manifest's kind"() {
    when:
    def testPayload =  gsonObj.fromJson(basicManifestSource(), Object)
    KubernetesManifest manifest = objectToManifest(testPayload)

    then:
    manifest.getKind() == KIND

    when:
    manifest.setKind(KubernetesKind.DEPLOYMENT)

    then:
    manifest.getKind() == KubernetesKind.DEPLOYMENT
  }

  void "correctly handles a change to the manifest's API group"() {
    when:
    def testPayload =  gsonObj.fromJson(basicManifestSource(), Object)
    KubernetesManifest manifest = objectToManifest(testPayload)

    then:
    manifest.getApiVersion() == KubernetesApiVersion.EXTENSIONS_V1BETA1

    when:
    manifest.setApiVersion(KubernetesApiVersion.NETWORKING_K8S_IO_V1)

    then:
    manifest.getApiVersion() == KubernetesApiVersion.NETWORKING_K8S_IO_V1
  }

  void "correctly handles a crd with custom cases"() {
    when:
    def sourceJson = KubernetesManifest.class.getResource("crd-manifest-spec.json").getText("utf-8")
    def testPayload =  gsonObj.fromJson(sourceJson, Object)
    KubernetesManifest manifest = objectToManifest(testPayload)

    then:
    manifest.getName() == CRD_NAME
    manifest.getKindName() == CRD_KIND
    manifest.getApiVersion().toString() == CRD_API_VERSION
    manifest.getSpecTemplateAnnotations() == Optional.empty()
  }
}
