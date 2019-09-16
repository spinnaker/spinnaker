/*
 * Copyright 2018 Bol.com
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

package com.netflix.spinnaker.clouddriver.kubernetes.v1.security

import spock.lang.Specification

import java.nio.file.Files

class KubernetesConfigParserSpec extends Specification {

  void "master url should be set irregardless of trailing slash"() {
    setup:
    def f = Files.createTempFile("kubeconfig", "tmp")
    f.setText("""
apiVersion: v1
clusters:
- cluster:
    certificate-authority: /opt/spinnaker/config/ca.crt
    server: https://1.2.3.4/
  name: tst
contexts:
- context:
    cluster: tst
    user: tst
  name: tst
current-context: tst
kind: Config
preferences: {}
users:
- name: tst
  user:
    client-certificate: /opt/spinnaker/config/client.crt
    client-key: /opt/spinnaker/config/client.key
""")

    when:
    def result = KubernetesConfigParser.withKubeConfig(f.toFile().getAbsolutePath(), "tst", "tst", "tst", ["default"])

    then:
    result.getMasterUrl() == "https://1.2.3.4/"

    cleanup:
    Files.delete(f)
  }
}
