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
import com.netflix.spinnaker.clouddriver.kubernetes.v2.artifact.KubernetesVersionedArtifactConverter
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.KubernetesResourcePropertyRegistry
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.KubernetesSpinnakerKindMap
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.*
import com.netflix.spinnaker.clouddriver.kubernetes.v2.names.KubernetesManifestNamer
import com.netflix.spinnaker.clouddriver.kubernetes.v2.op.handler.KubernetesReplicaSetHandler
import com.netflix.spinnaker.clouddriver.kubernetes.v2.op.handler.KubernetesServiceHandler
import com.netflix.spinnaker.clouddriver.kubernetes.v2.op.manifest.KubernetesDeployManifestOperation
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

  KubernetesDeployManifestOperation createMockDeployer(
    KubernetesV2Credentials credentials,
    KubernetesDeployManifestDescription deployDescription
  ) {
    def namedCredentialsMock = Mock(KubernetesNamedAccountCredentials)
    namedCredentialsMock.getCredentials() >> credentials
    namedCredentialsMock.getName() >> ACCOUNT
    deployDescription.setCredentials(namedCredentialsMock)

    credentials.deploy(_, _) >> null

    def replicaSetDeployer = new KubernetesReplicaSetHandler()
    replicaSetDeployer.versioned() >> true
    replicaSetDeployer.kind() >> KIND
    def serviceDeployer = new KubernetesServiceHandler()
    def versionedArtifactConverterMock = Mock(KubernetesVersionedArtifactConverter)
    versionedArtifactConverterMock.getDeployedName(_) >> "$NAME-$VERSION"
    versionedArtifactConverterMock.toArtifact(_, _, _) >> new Artifact()
    def registry = new KubernetesResourcePropertyRegistry([replicaSetDeployer, serviceDeployer],
        new KubernetesSpinnakerKindMap())

    NamerRegistry.lookup().withProvider(KubernetesCloudProvider.ID)
      .withAccount(ACCOUNT)
      .setNamer(KubernetesManifest.class, new KubernetesManifestNamer())

    registry.get("any", KubernetesKind.REPLICA_SET).versionedConverter = versionedArtifactConverterMock
    
    def deployOp = new KubernetesDeployManifestOperation(deployDescription, registry, null)

    return deployOp
  }

  void "replica set deployer is correctly invoked"() {
    setup:
    def credentialsMock = Mock(KubernetesV2Credentials)
    credentialsMock.getDefaultNamespace() >> NAMESPACE
    def deployOp = createMockDeployer(credentialsMock, getBaseDeployDescription(BASIC_REPLICA_SET))

    when:
    def result = deployOp.operate([])
    then:
    result.manifestNamesByNamespace[NAMESPACE].size() == 1
    result.manifestNamesByNamespace[NAMESPACE][0] == "$KIND $NAME-$VERSION"
  }

  void "replica set deployer uses backup namespace"() {
    setup:
    def credentialsMock = Mock(KubernetesV2Credentials)
    credentialsMock.getDefaultNamespace() >> DEFAULT_NAMESPACE
    def deployOp = createMockDeployer(credentialsMock, getBaseDeployDescription(BASIC_REPLICA_SET_NO_NAMESPACE))

    when:
    def result = deployOp.operate([])

    then:
    result.manifestNamesByNamespace[DEFAULT_NAMESPACE].size() == 1
    result.manifestNamesByNamespace[DEFAULT_NAMESPACE][0] == "$KIND $NAME-$VERSION"
  }

  void "sends traffic to the specified service when enableTraffic is true"() {
    setup:
    def credentialsMock = Mock(KubernetesV2Credentials)
    credentialsMock.getDefaultNamespace() >> NAMESPACE
    credentialsMock.get(KubernetesKind.SERVICE, NAMESPACE, "my-service") >> stringToManifest(MY_SERVICE)
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
    def credentialsMock = Mock(KubernetesV2Credentials)
    credentialsMock.getDefaultNamespace() >> NAMESPACE
    credentialsMock.get(KubernetesKind.SERVICE, NAMESPACE, "my-service") >> stringToManifest(MY_SERVICE)
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
