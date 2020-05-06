/*
 * Copyright 2019 Air France-KLM Group
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

package com.netflix.spinnaker.clouddriver.kubernetes.op.handler

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.clouddriver.kubernetes.description.manifest.KubernetesManifest
import com.netflix.spinnaker.clouddriver.kubernetes.description.JsonPatch.Op;
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.SafeConstructor
import spock.lang.Specification

class KubernetesServiceHandlerSpec extends Specification {
  def objectMapper = new ObjectMapper()
  def yaml = new Yaml(new SafeConstructor())
  def handler = new KubernetesServiceHandler()

  def BASIC_SERVICE = """
apiVersion: v1
kind: Service
metadata:
  name: test-service
spec:
  ports:
  - port: 80
    protocol: TCP
    targetPort: 80
  selector:
    load-balancer-test-app: 'true'
"""

  def SERVICE_WITH_SLASH = """
apiVersion: v1
kind: Service
metadata:
  name: test-service
spec:
  ports:
  - port: 80
    protocol: TCP
    targetPort: 80
  selector:
    load-balancer/test-app: 'true'
status:
  loadBalancer: {}
"""

  def SERVICE_WITH_NAME_LABEL = """
apiVersion: v1
kind: Service
metadata:
  name: test-service
spec:
  ports:
  - port: 80
    protocol: TCP
    targetPort: 80
  selector:
    app.kubernetes.io/name: test-app
status:
  loadBalancer: {}
"""

  def BASIC_REPLICASET = """
apiVersion: extensions/v1beta1
kind: ReplicaSet
metadata:
  name: app-replicaset
spec:
  replicas: 1
  selector:
    matchLabels:
      app.kubernetes.io/instance: app-instance
      app.kubernetes.io/name: test-app
  template:
    metadata:
      labels:
        app.kubernetes.io/instance: app-instance
        app.kubernetes.io/name: test-app
"""

  KubernetesManifest stringToManifest(String input) {
    return objectMapper.convertValue(yaml.load(input), KubernetesManifest)
  }

  void "check that loadbalancer label is added"() {
    when:
    def service = stringToManifest(BASIC_SERVICE)
    def target = stringToManifest(BASIC_REPLICASET)

    def result = handler.attachPatch(service, target)

    then:
    result[0].op == Op.add && result[0].path == "/spec/template/metadata/labels/load-balancer-test-app"
  }

  void "check that loadbalancer label with slash is escaped"() {
    when:
    def service = stringToManifest(SERVICE_WITH_SLASH)
    def target = stringToManifest(BASIC_REPLICASET)

    def result = handler.attachPatch(service, target)

    then:
    result[0].op == Op.add && result[0].path == "/spec/template/metadata/labels/load-balancer~1test-app"
  }

  void "check that loadbalancer label with slash is escaped when removing"() {
    when:
    def service = stringToManifest(SERVICE_WITH_NAME_LABEL)
    def target = stringToManifest(BASIC_REPLICASET)

    def result = handler.detachPatch(service, target)

    then:
    result[0].op == Op.remove && result[0].path == "/spec/template/metadata/labels/app.kubernetes.io~1name"
  }

}
