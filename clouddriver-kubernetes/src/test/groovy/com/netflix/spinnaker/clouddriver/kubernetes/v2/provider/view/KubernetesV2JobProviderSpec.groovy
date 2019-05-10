/*
 * Copyright 2019 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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
 */

package com.netflix.spinnaker.clouddriver.kubernetes.v2.provider.view

import com.netflix.spinnaker.clouddriver.kubernetes.v2.caching.view.model.KubernetesV2Manifest
import com.netflix.spinnaker.clouddriver.kubernetes.v2.caching.view.provider.KubernetesV2ManifestProvider
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesManifest
import com.netflix.spinnaker.clouddriver.kubernetes.v2.security.KubernetesV2Credentials
import com.netflix.spinnaker.clouddriver.security.AccountCredentials
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider
import spock.lang.Specification

class KubernetesV2JobProviderSpec extends Specification {

  def "getFileContents return a map with properties"() {
    given:
    def mockCredentials = Mock(KubernetesV2Credentials) {
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

    def mockManifestProvider = Mock(KubernetesV2ManifestProvider) {
      getManifest(*_) >> new KubernetesV2Manifest(
        account: 'a',
        name: 'a',
        manifest: testManifest,
      )
    }

    when:
    def provider = new KubernetesV2JobProvider(mockAccountCredentialsProvider, [mockManifestProvider])
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
