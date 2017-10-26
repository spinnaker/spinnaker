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
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.KubernetesResourcePropertyRegistry
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.KubernetesSpinnakerKindMap
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesApiVersion
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesDeployManifestDescription
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesKind
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesManifest
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesManifestSpinnakerRelationships
import com.netflix.spinnaker.clouddriver.kubernetes.v2.names.KubernetesManifestNamer
import com.netflix.spinnaker.clouddriver.kubernetes.v2.op.deployer.KubernetesReplicaSetDeployer
import com.netflix.spinnaker.clouddriver.kubernetes.v2.op.job.KubectlJobExecutor
import com.netflix.spinnaker.clouddriver.kubernetes.v2.op.manifest.KubernetesDeployManifestOperation
import com.netflix.spinnaker.clouddriver.kubernetes.v2.security.KubernetesV2Credentials
import com.netflix.spinnaker.clouddriver.names.NamerRegistry
import com.netflix.spinnaker.kork.artifacts.model.Artifact
import com.netflix.spinnaker.moniker.Moniker
import org.yaml.snakeyaml.Yaml
import spock.lang.Specification

class KubernetesDeployManifestOperationSpec extends Specification {
  def objectMapper = new ObjectMapper()
  def yaml = new Yaml()

  def ACCOUNT = "account"
  def NAME = "my-name"
  def VERSION = "version"
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

  KubernetesDeployManifestOperation createMockDeployer(KubernetesV2Credentials credentials, String manifest) {
    def deployDescription = new KubernetesDeployManifestDescription()
      .setManifest(stringToManifest(manifest))
      .setMoniker(new Moniker())
      .setRelationships(new KubernetesManifestSpinnakerRelationships())

    def namedCredentialsMock = Mock(KubernetesNamedAccountCredentials)
    namedCredentialsMock.getCredentials() >> credentials
    namedCredentialsMock.getName() >> ACCOUNT
    deployDescription.setCredentials(namedCredentialsMock)

    def jobExecutorMock = Mock(KubectlJobExecutor)
    jobExecutorMock.deployManifest(_, _) >> null

    def replicaSetDeployer = new KubernetesReplicaSetDeployer()
    replicaSetDeployer.objectMapper = new ObjectMapper()
    replicaSetDeployer.versioned() >> true
    replicaSetDeployer.apiVersion() >> API_VERSION
    replicaSetDeployer.kind() >> KIND
    replicaSetDeployer.jobExecutor = jobExecutorMock
    def versionedArtifactConverterMock = Mock(KubernetesVersionedArtifactConverter)
    versionedArtifactConverterMock.getDeployedName(_) >> "$NAME-$VERSION"
    versionedArtifactConverterMock.toArtifact(_) >> new Artifact()
    def registry = new KubernetesResourcePropertyRegistry(Collections.singletonList(replicaSetDeployer),
        new KubernetesSpinnakerKindMap(),
        versionedArtifactConverterMock,
        new KubernetesUnversionedArtifactConverter())

    NamerRegistry.lookup().withProvider(KubernetesCloudProvider.ID)
      .withAccount(ACCOUNT)
      .setNamer(KubernetesManifest.class, new KubernetesManifestNamer())
    
    def deployOp = new KubernetesDeployManifestOperation(deployDescription, registry)

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
    result.deployedNames.size == 1
    result.deployedNames[0] == "$NAMESPACE:$API_VERSION|$KIND|$NAME-$VERSION"
  }

  void "replica set deployer uses backup namespace"() {
    setup:
    def credentialsMock = Mock(KubernetesV2Credentials)
    credentialsMock.getDefaultNamespace() >> BACKUP_NAMESPACE
    def deployOp = createMockDeployer(credentialsMock, BASIC_REPLICA_SET_NO_NAMESPACE)

    when:
    def result = deployOp.operate([])

    then:
    result.deployedNames.size == 1
    result.deployedNames[0] == "$BACKUP_NAMESPACE:$API_VERSION|$KIND|$NAME-$VERSION"
  }
}
