/*
 * Copyright 2016 Google, Inc.
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

package com.netflix.spinnaker.clouddriver.kubernetes.v1.deploy.ops.securitygroup

import com.netflix.spectator.api.DefaultRegistry
import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.kubernetes.config.LinkedDockerRegistryConfiguration
import com.netflix.spinnaker.clouddriver.kubernetes.security.KubernetesNamedAccountCredentials
import com.netflix.spinnaker.clouddriver.kubernetes.v1.api.KubernetesApiAdaptor
import com.netflix.spinnaker.clouddriver.kubernetes.v1.deploy.description.securitygroup.KubernetesIngressTlS
import com.netflix.spinnaker.clouddriver.kubernetes.v1.deploy.description.securitygroup.KubernetesSecurityGroupDescription
import com.netflix.spinnaker.clouddriver.kubernetes.v1.security.KubernetesV1Credentials
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsRepository
import io.fabric8.kubernetes.api.model.extensions.Ingress
import io.fabric8.kubernetes.api.model.extensions.IngressTLS
import spock.lang.Specification
import spock.lang.Subject

class UpsertKubernetesV1SecurityGroupAtomicOperationSpec extends Specification {
  final static List<String> NAMESPACES = ['default', 'prod']
  final static String NAMESPACE = 'prod'
  final static String INGRESS_NAME = "fooingress"
  final static String TLS_HOST = "supersecure.com"
  final static String TLS_SECRET = "mumstheword"
  final static Map ANNOTATIONS = ["foo": "bar", "bar": "baz"]
  final static Map LABELS = ["can_you": "kick_it", "yes": "you_can"]

  def setupSpec() {
    TaskRepository.threadLocalTask.set(Mock(Task))
  }

  KubernetesApiAdaptor apiMock
  def accountCredentialsRepositoryMock
  def credentials
  def namedAccountCredentials
  def dockerRegistry
  def dockerRegistries
  def spectatorRegistry
  def testTLS, resultTLS

  def setup() {
    apiMock = Mock(KubernetesApiAdaptor)

    spectatorRegistry = new DefaultRegistry()
    dockerRegistry = Mock(LinkedDockerRegistryConfiguration)
    dockerRegistries = [dockerRegistry]
    accountCredentialsRepositoryMock = Mock(AccountCredentialsRepository)
    credentials = new KubernetesV1Credentials(apiMock, NAMESPACES, [], [], accountCredentialsRepositoryMock)
    namedAccountCredentials = Mock(KubernetesNamedAccountCredentials) {
      getCredentials() >> credentials
    }

    testTLS = [new KubernetesIngressTlS([TLS_HOST], TLS_SECRET)].asList()
    resultTLS = [new IngressTLS([TLS_HOST], TLS_SECRET)].asList()

  }

  void "should upsert a new SecurityGroup with labels and annotations"() {
    setup:
      def description = new KubernetesSecurityGroupDescription(
          securityGroupName: INGRESS_NAME,
          namespace: NAMESPACE,
          annotations: ANNOTATIONS,
          labels: LABELS,
          credentials: namedAccountCredentials,
          tls: testTLS,
      )
      def resultIngressMock = Mock(Ingress)

      @Subject def operation = new UpsertKubernetesSecurityGroupAtomicOperation(description)

    when:
      operation.operate([])

    then:
      1 * apiMock.getIngress(NAMESPACE, INGRESS_NAME) >> null
      1 * apiMock.createIngress(NAMESPACE, { ingress ->
        ingress.metadata.name == description.securityGroupName
        ingress.metadata.annotations == description.annotations
        ingress.metadata.labels == description.labels
        ingress.spec.tls == resultTLS
      }) >> resultIngressMock
      resultIngressMock.getMetadata() >> [name: '', namespace: '']
  }
}
