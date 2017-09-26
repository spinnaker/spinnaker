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
import com.netflix.spinnaker.clouddriver.kubernetes.security.KubernetesNamedAccountCredentials
import com.netflix.spinnaker.clouddriver.kubernetes.v2.artifact.ManifestToArtifact
import com.netflix.spinnaker.clouddriver.kubernetes.v2.artifact.ManifestToVersionedArtifact
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.KubernetesApiVersion
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.KubernetesAugmentedManifest
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.KubernetesKind
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.KubernetesManifest
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.KubernetesManifestOperationDescription
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.KubernetesManifestSpinnakerRelationships
import com.netflix.spinnaker.clouddriver.kubernetes.v2.op.deployer.KubernetesReplicaSetDeployer
import com.netflix.spinnaker.clouddriver.kubernetes.v2.security.KubernetesV2Credentials
import com.netflix.spinnaker.kork.artifacts.model.Artifact
import com.netflix.spinnaker.moniker.Moniker
import org.yaml.snakeyaml.Yaml
import spock.lang.Specification

class KubernetesManifestDeployerSpec extends Specification {
  def objectMapper = new ObjectMapper()
  def yaml = new Yaml()

  def NAME = "my-name"
  def NAMESPACE = "my-namespace"
  def BACKUP_NAMESPACE = "my-backup-namespace"
  def KIND = KubernetesKind.REPLICA_SET
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

  def setupSpec() {
    TaskRepository.threadLocalTask.set(Mock(Task))
  }

  KubernetesManifest stringToManifest(String input) {
    return objectMapper.convertValue(yaml.load(input), KubernetesManifest)
  }

  KubernetesManifestDeployer createMockDeployer(KubernetesV2Credentials credentials, String manifest) {
    def metadata = new KubernetesAugmentedManifest.Metadata()
    metadata.setRelationships(new KubernetesManifestSpinnakerRelationships())
        .setMoniker(new Moniker())
    def manifestPair = new KubernetesAugmentedManifest()
        .setManifest(stringToManifest(manifest))
        .setMetadata(metadata)

    def deployDescription = new KubernetesManifestOperationDescription()
        .setManifests(Collections.singletonList(manifestPair))

    def namedCredentialsMock = Mock(KubernetesNamedAccountCredentials)
    namedCredentialsMock.getCredentials() >> credentials
    deployDescription.setCredentials(namedCredentialsMock)

    def replicaSetDeployer = new KubernetesReplicaSetDeployer()
    replicaSetDeployer.objectMapper = new ObjectMapper()

    def deployOp = new KubernetesManifestDeployer(deployDescription)
    deployOp.replicaSetDeployer = replicaSetDeployer
    deployOp.manifestToVersionedArtifact = new ManifestToVersionedArtifact()

    return deployOp
  }

  void "replica set deployer is correctly invoked"() {
    setup:
    def credentialsMock = Mock(KubernetesV2Credentials)
    credentialsMock.getDefaultNamespace() >> NAMESPACE
    def deployOp = createMockDeployer(credentialsMock, BASIC_REPLICA_SET)

    when:
    def result = deployOp.operate([])

    then:
    1 * credentialsMock.createReplicaSet(_) >> null
    result.serverGroupNames.size == 1
    result.serverGroupNames[0].startsWith("$NAMESPACE:$API_VERSION|$KIND|$NAME")
  }

  void "replica set deployer uses backup namespace"() {
    setup:
    def credentialsMock = Mock(KubernetesV2Credentials)
    credentialsMock.getDefaultNamespace() >> BACKUP_NAMESPACE
    def deployOp = createMockDeployer(credentialsMock, BASIC_REPLICA_SET_NO_NAMESPACE)

    when:
    def result = deployOp.operate([])

    then:
    1 * credentialsMock.createReplicaSet(_) >> null
    result.serverGroupNames.size == 1
    result.serverGroupNames[0].startsWith("$BACKUP_NAMESPACE:$API_VERSION|$KIND|$NAME")
  }
}
