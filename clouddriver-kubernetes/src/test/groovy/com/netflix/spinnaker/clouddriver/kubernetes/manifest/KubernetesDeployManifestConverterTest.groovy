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
package com.netflix.spinnaker.clouddriver.kubernetes.manifest

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.clouddriver.kubernetes.converter.manifest.KubernetesDeployManifestConverter
import com.netflix.spinnaker.clouddriver.kubernetes.security.KubernetesNamedAccountCredentials
import com.netflix.spinnaker.clouddriver.kubernetes.description.manifest.KubernetesManifest
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider
import groovy.text.SimpleTemplateEngine
import spock.lang.Specification


class KubernetesDeployManifestConverterTest extends Specification {

  def converter = new KubernetesDeployManifestConverter(
    Mock(AccountCredentialsProvider), new ObjectMapper(), null)
  def inputMap = new HashMap()
  def manifestList = new ArrayList()

  def setup() {
    converter.accountCredentialsProvider.getCredentials("kubernetes") >> Mock(KubernetesNamedAccountCredentials)
    converter.objectMapper = new ObjectMapper()

    inputMap.put("account", "kubernetes")
    inputMap.put("manifests", manifestList)
  }

  void "list of manifests is deserialized"() {
    given:
    def deploymentJson = KubernetesManifest.class.getResource("deployment-manifest.json").getText("utf-8")
    def serviceJson = KubernetesManifest.class.getResource("service-manifest.json").getText("utf-8")
    def deploymentMap = converter.objectMapper.readValue(deploymentJson, Map.class)
    def serviceMap = converter.objectMapper.readValue(serviceJson, Map.class)
    manifestList.add(deploymentMap)
    manifestList.add(serviceMap)

    when:
    def description = converter.convertDescription(inputMap)

    then:
    description.getManifests().size() == 2
    description.getManifests().get(0).getKindName() == "Deployment"
    description.getManifests().get(1).getKindName() == "Service"
  }

  void "manifest of kind List is split into multiple manifests"() {
    given:
    def templateEngine = new SimpleTemplateEngine()
    def binding = [
      "manifest1": KubernetesManifest.class.getResource("deployment-manifest.json").getText("utf-8"),
      "manifest2": KubernetesManifest.class.getResource("service-manifest.json").getText("utf-8")
    ]
    def listJson = KubernetesManifest.class.getResource("list-manifest.json").getText("utf-8")
    def template = templateEngine.createTemplate(listJson).make(binding)
    def listMap = converter.objectMapper.readValue(template.toString(), Map.class)
    manifestList.add(listMap)

    when:
    def description = converter.convertDescription(inputMap)

    then:
    description.getManifests().size() == 2
    description.getManifests().get(0).getKindName() == "Deployment"
    description.getManifests().get(1).getKindName() == "Service"
  }

  void "input with no manifests is deserialized"() {
    given:
    inputMap.remove("manifests")

    when:
    def description = converter.convertDescription(inputMap)

    then:
    description.getManifests() == null
  }

  void "input with custom resource definition is deserialized"() {
    given:
    def crdJson = KubernetesManifest.class.getResource("crd-manifest.json").getText("utf-8")
    def crdMap = converter.objectMapper.readValue(crdJson, Map.class)
    manifestList.add(crdMap)

    when:
    def description = converter.convertDescription(inputMap)

    then:
    description.getManifests().size() == 1
    description.getManifests().get(0).getKindName() == "Custom1"
  }

  void "manifest of kind List with custom resource definitions is deserialized"() {
    given:
    def templateEngine = new SimpleTemplateEngine()
    def binding = [
      "manifest1": KubernetesManifest.class.getResource("deployment-manifest.json").getText("utf-8"),
      "manifest2": KubernetesManifest.class.getResource("crd-manifest.json").getText("utf-8")
    ]
    def listJson = KubernetesManifest.class.getResource("list-manifest.json").getText("utf-8")
    def template = templateEngine.createTemplate(listJson).make(binding)
    def listMap = converter.objectMapper.readValue(template.toString(), Map.class)
    manifestList.add(listMap)

    when:
    def description = converter.convertDescription(inputMap)

    then:
    description.getManifests().size() == 2
    description.getManifests().get(0).getKindName() == "Deployment"
    description.getManifests().get(1).getKindName() == "Custom1"
  }
}
