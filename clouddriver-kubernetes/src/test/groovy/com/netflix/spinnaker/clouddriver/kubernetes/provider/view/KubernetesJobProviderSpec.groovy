/*
 * Copyright 2019 Armory
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

package com.netflix.spinnaker.clouddriver.kubernetes.provider.view

import com.netflix.spinnaker.clouddriver.kubernetes.caching.view.model.KubernetesManifestContainer
import com.netflix.spinnaker.clouddriver.kubernetes.caching.view.provider.KubernetesManifestProvider
import com.netflix.spinnaker.clouddriver.kubernetes.description.manifest.KubernetesManifest
import com.netflix.spinnaker.clouddriver.kubernetes.security.KubernetesCredentials
import com.netflix.spinnaker.clouddriver.security.AccountCredentials
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider
import spock.lang.Specification

class KubernetesJobProviderSpec extends Specification {

  def "getFileContents return a map with properties"() {
    given:
    def mockCredentials = Mock(KubernetesCredentials) {
      jobLogs(*_) >> logs
    }

    def mockAccountCredentialsProvider = Mock(AccountCredentialsProvider) {
      getCredentials(*_) >> Mock(AccountCredentials) {
        getCredentials(*_) >> mockCredentials
      }
    }

    def testManifest = new KubernetesManifest()
    testManifest.putAll([
      apiVersion: 'batch/v1',
      kind: 'Job',
      metadata: [
        name: 'a',
        namespace: 'b',
      ]
    ])

    def mockManifestProvider = Mock(KubernetesManifestProvider) {
      getManifest(*_) >> KubernetesManifestContainer.builder()
        .account("a")
        .name("a")
        .manifest(testManifest)
        .build()
    }

    when:
    def provider = new KubernetesJobProvider(mockAccountCredentialsProvider, mockManifestProvider)
    def logResult = provider.getFileContents("a", "b", "c", "d")

    then:
    logResult == result

    where:
    logs                               | result
    "SPINNAKER_PROPERTY_a=b"           | [a: 'b']
    "Spinnaker_Property_a=b"           | [:]
    'SPINNAKER_CONFIG_JSON={"a": "b"}' | [a: 'b']
    'SPINNAKER_CONFIG_JSON={"a": "b}'  | null
  }
}
