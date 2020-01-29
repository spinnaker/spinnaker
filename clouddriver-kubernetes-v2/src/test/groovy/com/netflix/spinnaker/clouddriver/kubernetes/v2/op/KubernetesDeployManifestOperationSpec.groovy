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

package com.netflix.spinnaker.clouddriver.kubernetes.v2.op

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.kubernetes.KubernetesCloudProvider
import com.netflix.spinnaker.clouddriver.kubernetes.security.KubernetesNamedAccountCredentials
import com.netflix.spinnaker.clouddriver.kubernetes.v2.artifact.KubernetesUnversionedArtifactConverter
import com.netflix.spinnaker.clouddriver.kubernetes.v2.artifact.KubernetesVersionedArtifactConverter
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.KubernetesResourceProperties
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.ResourcePropertyRegistry
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.*
import com.netflix.spinnaker.clouddriver.kubernetes.v2.names.KubernetesManifestNamer
import com.netflix.spinnaker.clouddriver.kubernetes.v2.op.handler.KubernetesReplicaSetHandler
import com.netflix.spinnaker.clouddriver.kubernetes.v2.op.handler.KubernetesServiceHandler
import com.netflix.spinnaker.clouddriver.kubernetes.v2.op.manifest.KubernetesDeployManifestOperation
import com.netflix.spinnaker.clouddriver.kubernetes.v2.security.GlobalKubernetesKindRegistry
import com.netflix.spinnaker.clouddriver.kubernetes.v2.security.KubernetesKindRegistry
import com.netflix.spinnaker.clouddriver.kubernetes.v2.security.KubernetesV2Credentials
import com.netflix.spinnaker.clouddriver.names.NamerRegistry
import com.netflix.spinnaker.kork.artifacts.model.Artifact
import com.netflix.spinnaker.moniker.Moniker
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.SafeConstructor
import spock.lang.Specification

class KubernetesDeployManifestOperationSpec extends Specification {
  def objectMapper = new ObjectMapper()
  def yaml = new Yaml(new SafeConstructor())

  def ACCOUNT = "account"
  def NAME = "my-name"
  def VERSION = "version"
  def NAMESPACE = "my-namespace"
  def DEFAULT_NAMESPACE = "default"
  def IMAGE = "gcr.io/project/image"
  def KIND = KubernetesKind.REPLICA_SET
  def SERVICE = KubernetesKind.SERVICE
  def API_VERSION = KubernetesApiVersion.EXTENSIONS_V1BETA1

  def BASIC_REPLICA_SET = """
apiVersion: $API_VERSION
kind: $KIND
metadata:
  name: $NAME
  namespace: $NAMESPACE
"""

  def BASIC_REPLICA_SET_NO_NAMESPACE = """
apiVersion: $API_VERSION
kind: $KIND
metadata:
  name: $NAME
"""

  def MY_SERVICE = """
apiVersion: v1
kind: $SERVICE
metadata:
  name: $NAME
  namespace: $NAMESPACE
spec:
  selector:
    selector-key: selector-value
"""

  def setupSpec() {
    TaskRepository.threadLocalTask.set(Mock(Task))
  }

  KubernetesManifest stringToManifest(String input) {
    return objectMapper.convertValue(yaml.load(input), KubernetesManifest)
  }

  KubernetesDeployManifestDescription getBaseDeployDescription(String manifest) {
    return new KubernetesDeployManifestDescription()
      .setManifests([stringToManifest(manifest)])
      .setMoniker(new Moniker())
      .setSource(KubernetesDeployManifestDescription.Source.text)
  }

  ResourcePropertyRegistry getResourcePropertyRegistry() {
    def registry = Mock(ResourcePropertyRegistry) {
      get(KubernetesKind.REPLICA_SET) >> Mock(KubernetesResourceProperties) {
        getHandler() >> new KubernetesReplicaSetHandler()
        isVersioned() >> true
        getVersionedConverter() >> Mock(KubernetesVersionedArtifactConverter) {
          getDeployedName(_) >> "$NAME-$VERSION"
          toArtifact(_, _, _) >> Artifact.builder().build()
        }
      }
      get(KubernetesKind.SERVICE) >> Mock(KubernetesResourceProperties) {
        getHandler() >> new KubernetesServiceHandler()
        isVersioned() >> false
        getUnversionedConverter() >> new KubernetesUnversionedArtifactConverter()
      }
    }
    return registry
  }

  KubernetesDeployManifestOperation createMockDeployer(
    KubernetesV2Credentials credentials,
    KubernetesDeployManifestDescription deployDescription
  ) {
    def namedCredentialsMock = Mock(KubernetesNamedAccountCredentials)
    namedCredentialsMock.getCredentials() >> credentials
    namedCredentialsMock.getName() >> ACCOUNT
    deployDescription.setCredentials(namedCredentialsMock)

    credentials.deploy(_) >> null
    credentials.getResourcePropertyRegistry() >> getResourcePropertyRegistry()

    NamerRegistry.lookup().withProvider(KubernetesCloudProvider.ID)
      .withAccount(ACCOUNT)
      .setNamer(KubernetesManifest.class, new KubernetesManifestNamer())

    def deployOp = new KubernetesDeployManifestOperation(deployDescription, null)

    return deployOp
  }

  void "replica set deployer is correctly invoked"() {
    setup:
    def credentialsMock = Mock(KubernetesV2Credentials) {
      getKindProperties(_ as KubernetesKind) >> { args -> KubernetesKindProperties.withDefaultProperties(args[0]) }
      getDefaultNamespace() >> NAMESPACE
    }
    def deployOp = createMockDeployer(credentialsMock, getBaseDeployDescription(BASIC_REPLICA_SET))

    when:
    def result = deployOp.operate([])
    then:
    result.manifestNamesByNamespace[NAMESPACE].size() == 1
    result.manifestNamesByNamespace[NAMESPACE][0] == "$KIND $NAME-$VERSION"
  }

  void "replica set deployer uses backup namespace"() {
    setup:
    def credentialsMock = Mock(KubernetesV2Credentials) {
      getKindProperties(_ as KubernetesKind) >> { args -> KubernetesKindProperties.withDefaultProperties(args[0]) }
      getDefaultNamespace() >> DEFAULT_NAMESPACE
    }
    def deployOp = createMockDeployer(credentialsMock, getBaseDeployDescription(BASIC_REPLICA_SET_NO_NAMESPACE))

    when:
    def result = deployOp.operate([])

    then:
    result.manifestNamesByNamespace[DEFAULT_NAMESPACE].size() == 1
    result.manifestNamesByNamespace[DEFAULT_NAMESPACE][0] == "$KIND $NAME-$VERSION"
  }

  void "replica set deployer uses namespace override when set"() {
    setup:
    def namespaceOverride = "overridden"
    def credentialsMock = Mock(KubernetesV2Credentials) {
      getKindProperties(_ as KubernetesKind) >> { args -> KubernetesKindProperties.withDefaultProperties(args[0]) }
      getDefaultNamespace() >> DEFAULT_NAMESPACE
    }
    def deployOp = getBaseDeployDescription(BASIC_REPLICA_SET_NO_NAMESPACE)
      .setNamespaceOverride(namespaceOverride)
    def mockDeployer= createMockDeployer(credentialsMock, deployOp)

    when:
    def result = mockDeployer.operate([])

    then:
    result.manifestNamesByNamespace[namespaceOverride].size() == 1
    result.manifestNamesByNamespace[namespaceOverride][0] == "$KIND $NAME-$VERSION"
    !result.manifestNamesByNamespace.containsKey(DEFAULT_NAMESPACE)
  }

  void "sends traffic to the specified service when enableTraffic is true"() {
    setup:
    def credentialsMock = Mock(KubernetesV2Credentials) {
      getKindProperties(_ as KubernetesKind) >> { args -> KubernetesKindProperties.withDefaultProperties(args[0]) }
      getDefaultNamespace() >> NAMESPACE
      get(KubernetesKind.SERVICE, NAMESPACE, "my-service") >> stringToManifest(MY_SERVICE)
    }
    def deployDescription = getBaseDeployDescription(BASIC_REPLICA_SET)
      .setServices(["service my-service"])
      .setEnableTraffic(true)
    def deployOp = createMockDeployer(credentialsMock, deployDescription)

    when:
    def result = deployOp.operate([])
    def manifest = result.getManifests().getAt(0)
    def traffic = KubernetesManifestAnnotater.getTraffic(manifest)

    then:
    traffic.getLoadBalancers() == ["service my-service"]
    manifest.getLabels().get("selector-key") == "selector-value"
  }

  void "does not send traffic to the specified service when enableTraffic is false"() {
    setup:
    def credentialsMock = Mock(KubernetesV2Credentials) {
      getKindProperties(_ as KubernetesKind) >> { args -> KubernetesKindProperties.withDefaultProperties(args[0]) }
      getDefaultNamespace() >> NAMESPACE
      get(KubernetesKind.SERVICE, NAMESPACE, "my-service") >> stringToManifest(MY_SERVICE)
    }
    def deployDescription = getBaseDeployDescription(BASIC_REPLICA_SET)
      .setServices(["service my-service"])
      .setEnableTraffic(false)
    def deployOp = createMockDeployer(credentialsMock, deployDescription)

    when:
    def result = deployOp.operate([])
    def manifest = result.getManifests().getAt(0)
    def traffic = KubernetesManifestAnnotater.getTraffic(manifest)

    then:
    traffic.getLoadBalancers() == ["service my-service"]
    !manifest.getLabels().containsKey("selector-key")
  }
}
