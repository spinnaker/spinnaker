/*
 * Copyright 2019 Google, Inc.
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

package com.netflix.spinnaker.clouddriver.kubernetes.security

import com.google.common.collect.ImmutableList
import com.netflix.spectator.api.NoopRegistry
import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.clouddriver.kubernetes.config.KubernetesAccountProperties.ManagedAccount
import com.netflix.spinnaker.clouddriver.kubernetes.description.AccountResourcePropertyRegistry
import com.netflix.spinnaker.clouddriver.kubernetes.description.GlobalResourcePropertyRegistry
import com.netflix.spinnaker.clouddriver.kubernetes.description.KubernetesSpinnakerKindMap
import com.netflix.spinnaker.clouddriver.kubernetes.description.manifest.KubernetesApiGroup
import com.netflix.spinnaker.clouddriver.kubernetes.description.manifest.KubernetesKind
import com.netflix.spinnaker.clouddriver.kubernetes.description.manifest.KubernetesKindProperties
import com.netflix.spinnaker.clouddriver.kubernetes.names.KubernetesManifestNamer
import com.netflix.spinnaker.clouddriver.kubernetes.names.KubernetesNamerRegistry
import com.netflix.spinnaker.clouddriver.kubernetes.op.handler.KubernetesUnregisteredCustomResourceHandler
import com.netflix.spinnaker.clouddriver.kubernetes.op.job.DefaultKubectlJobExecutor
import com.netflix.spinnaker.clouddriver.kubernetes.security.KubernetesCredentials.KubernetesKindStatus
import com.netflix.spinnaker.kork.configserver.ConfigFileService
import spock.lang.Specification

class KubernetesCredentialsSpec extends Specification {
  Registry registry = Stub(Registry)
  DefaultKubectlJobExecutor kubectlJobExecutor = Stub(DefaultKubectlJobExecutor)
  String NAMESPACE = "my-namespace"
  AccountResourcePropertyRegistry.Factory resourcePropertyRegistryFactory = Mock(AccountResourcePropertyRegistry.Factory)
  KubernetesKindRegistry.Factory kindRegistryFactory = new KubernetesKindRegistry.Factory(
    new GlobalKubernetesKindRegistry(KubernetesKindProperties.getGlobalKindProperties())
  )
  KubernetesNamerRegistry namerRegistry = new KubernetesNamerRegistry([new KubernetesManifestNamer()])
  ConfigFileService configFileService = new ConfigFileService()
  KubernetesSpinnakerKindMap kubernetesSpinnakerKindMap = new KubernetesSpinnakerKindMap(ImmutableList.of())
  GlobalResourcePropertyRegistry globalResourcePropertyRegistry = new GlobalResourcePropertyRegistry(ImmutableList.of(), new KubernetesUnregisteredCustomResourceHandler())

  KubernetesCredentials.Factory credentialFactory = new KubernetesCredentials.Factory(
    new NoopRegistry(),
    namerRegistry,
    kubectlJobExecutor,
    configFileService,
    resourcePropertyRegistryFactory,
    kindRegistryFactory,
    kubernetesSpinnakerKindMap,
    globalResourcePropertyRegistry
  )



  void "Built-in Kubernetes kinds are considered valid by default"() {
    when:
    KubernetesCredentials credentials = credentialFactory.build(new ManagedAccount(
        name: "k8s",
        namespaces: [NAMESPACE],
        checkPermissionsOnStartup: false,
      ))

    then:
    credentials.getKindStatus(KubernetesKind.DEPLOYMENT) == KubernetesKindStatus.VALID
    credentials.getKindStatus(KubernetesKind.REPLICA_SET) == KubernetesKindStatus.VALID
  }

  void "Built-in Kubernetes kinds are considered valid by default when kinds is empty"() {
    when:
    KubernetesCredentials credentials = credentialFactory.build(new ManagedAccount(
        name: "k8s",
        namespaces: [NAMESPACE],
        checkPermissionsOnStartup: false,
        kinds: []
      ))

    then:
    credentials.getKindStatus(KubernetesKind.DEPLOYMENT) == KubernetesKindStatus.VALID
    credentials.getKindStatus(KubernetesKind.REPLICA_SET) == KubernetesKindStatus.VALID
  }

  void "Only explicitly listed kinds are valid when kinds is not empty"() {
    when:
    KubernetesCredentials credentials = credentialFactory.build(new ManagedAccount(
        name: "k8s",
        namespaces: [NAMESPACE],
        checkPermissionsOnStartup: false,
        kinds: ["deployment"]
      ))

    then:
    credentials.getKindStatus(KubernetesKind.DEPLOYMENT) == KubernetesKindStatus.VALID
    credentials.getKindStatus(KubernetesKind.REPLICA_SET) == KubernetesKindStatus.MISSING_FROM_ALLOWED_KINDS
  }

  void "Explicitly omitted kinds are not valid"() {
    when:
    KubernetesCredentials credentials = credentialFactory.build(new ManagedAccount(
        name: "k8s",
        namespaces: [NAMESPACE],
        checkPermissionsOnStartup: false,
        omitKinds: ["deployment"]
      ))

    then:
    credentials.getKindStatus(KubernetesKind.DEPLOYMENT) == KubernetesKindStatus.EXPLICITLY_OMITTED_BY_CONFIGURATION
    credentials.getKindStatus(KubernetesKind.REPLICA_SET) == KubernetesKindStatus.VALID
  }

  void "CRDs that are not installed return unknown"() {
    given:
    KubernetesApiGroup customGroup = KubernetesApiGroup.fromString("deployment.stable.example.com")
    KubernetesCredentials credentials = credentialFactory.build(new ManagedAccount(
      name: "k8s",
      namespaces: [NAMESPACE],
      checkPermissionsOnStartup: true,
    ))

    expect:
    credentials.getKindStatus(KubernetesKind.from("my-kind", customGroup)) == KubernetesKindStatus.UNKNOWN
  }

  void "Kinds that are not readable are considered invalid"() {
    given:
    KubernetesCredentials credentials = credentialFactory.build(new ManagedAccount(
        name: "k8s",
        namespaces: [NAMESPACE],
        checkPermissionsOnStartup: true,
      ))
    kubectlJobExecutor.list(_ as KubernetesCredentials, ImmutableList.of(KubernetesKind.DEPLOYMENT), NAMESPACE, _ as KubernetesSelectorList) >> {
      throw new DefaultKubectlJobExecutor.KubectlException("Error", new Exception())
    }
    kubectlJobExecutor.list(_ as KubernetesCredentials, ImmutableList.of(KubernetesKind.REPLICA_SET), NAMESPACE, _ as KubernetesSelectorList) >> {
      return ImmutableList.of()
    }

    expect:
    credentials.getKindStatus(KubernetesKind.DEPLOYMENT) == KubernetesKindStatus.READ_ERROR
    credentials.getKindStatus(KubernetesKind.REPLICA_SET) == KubernetesKindStatus.VALID
  }

  void "Metrics are properly set on the account when not checking permissions"() {
    given:
    KubernetesCredentials credentials = credentialFactory.build(new ManagedAccount(
        name: "k8s",
        namespaces: [NAMESPACE],
        checkPermissionsOnStartup: false,
        metrics: metrics
      ))

    expect:
    credentials.isMetricsEnabled() == metrics

    where:
    metrics << [true, false]
  }

  void "Metrics are properly enabled when readable"() {
    given:
    KubernetesCredentials credentials = credentialFactory.build(new ManagedAccount(
        name: "k8s",
        namespaces: [NAMESPACE],
        checkPermissionsOnStartup: true,
        metrics: true
      ))
    kubectlJobExecutor.topPod(_ as KubernetesCredentials, NAMESPACE, _) >> ImmutableList.of()

    expect:
    credentials.isMetricsEnabled() == true
  }

  void "Metrics are properly disabled when not readable"() {
    given:
    KubernetesCredentials credentials = credentialFactory.build(new ManagedAccount(
        name: "k8s",
        namespaces: [NAMESPACE],
        checkPermissionsOnStartup: true,
        metrics: true
      ))
    kubectlJobExecutor.topPod(_ as KubernetesCredentials, NAMESPACE, _) >> {
      throw new DefaultKubectlJobExecutor.KubectlException("Error", new Exception())
    }

    expect:
    credentials.isMetricsEnabled() == false
  }
}
