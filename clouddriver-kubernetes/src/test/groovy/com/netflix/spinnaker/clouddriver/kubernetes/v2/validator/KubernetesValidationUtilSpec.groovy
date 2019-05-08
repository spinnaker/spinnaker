/*
 * Copyright 2018 Schibsted ASA.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 *
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.kubernetes.v2.validator

import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesKind
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesManifest
import com.netflix.spinnaker.clouddriver.kubernetes.v2.security.KubernetesV2Credentials
import com.netflix.spinnaker.clouddriver.security.AccountCredentials
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider
import org.springframework.validation.Errors
import spock.lang.Specification
import spock.lang.Unroll

class KubernetesValidationUtilSpec extends Specification {
  def namespaces = ["test-namespace"]

  @Unroll
  void "wiring of kind/namespace validation"() {
    given:
    Errors errors = Mock(Errors)
    String kubernetesAccount = "testAccount"
    def namespaces = ["test-namespace"]
    def kind = KubernetesKind.DEPLOYMENT
    AccountCredentials accountCredentials = Mock(AccountCredentials)
    KubernetesV2Credentials credentials = Mock(KubernetesV2Credentials)
    KubernetesValidationUtil kubernetesValidationUtil = new KubernetesValidationUtil("currentContext", errors);
    AccountCredentialsProvider accountCredentialsProvider = Mock(AccountCredentialsProvider)
    KubernetesManifest manifest = Mock(KubernetesManifest)

    when:
    def judgement = kubernetesValidationUtil.validateV2Credentials(accountCredentialsProvider, kubernetesAccount, manifest)

    then:
    accountCredentialsProvider.getCredentials(kubernetesAccount) >> accountCredentials
    accountCredentials.getCredentials() >> credentials
    credentials.getDeclaredNamespaces() >> namespaces
    manifest.getNamespace() >> testNamespace
    manifest.getKind() >> kind
    credentials.isValidKind(kind) >> true
    judgement == expectedResult

    where:
    testNamespace       || expectedResult
    null                || true
    ""                  || true
    "test-namespace"    || true
    "omit-namespace"    || false
    "unknown-namespace" || false
  }

  @Unroll
  void "validation of namespaces"() {
    given:
    Errors errors = Mock(Errors)
    KubernetesV2Credentials credentials = Mock(KubernetesV2Credentials)
    KubernetesValidationUtil kubernetesValidationUtil = new KubernetesValidationUtil("currentContext", errors);

    when:
    def judgement = kubernetesValidationUtil.validateNamespace(testNamespace, credentials)

    then:
    credentials.getDeclaredNamespaces() >> namespaces
    judgement == allowedNamespace

    where:
    namespaces         | omitNamespaces     | testNamespace       || allowedNamespace
    ["test-namespace"] | ["omit-namespace"] | "test-namespace"    || true
    ["test-namespace"] | null               | "test-namespace"    || true
    ["test-namespace"] | ["omit-namespace"] | "omit-namespace"    || false
    ["test-namespace"] | ["omit-namespace"] | "unknown-namespace" || false
    []                 | []                 | "unknown-namespace" || false
  }
}
