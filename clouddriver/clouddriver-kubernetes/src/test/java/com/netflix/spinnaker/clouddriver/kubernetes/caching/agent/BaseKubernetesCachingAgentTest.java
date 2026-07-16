/*
 * Copyright 2025 Wise, PLC.
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
 */

package com.netflix.spinnaker.clouddriver.kubernetes.caching.agent;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.netflix.spinnaker.cats.agent.AgentDataType.Authority.AUTHORITATIVE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.io.Resources;
import com.netflix.spinnaker.cats.agent.AgentDataType;
import com.netflix.spinnaker.cats.cache.CacheData;
import com.netflix.spinnaker.clouddriver.kubernetes.caching.Keys.LogicalKind;
import com.netflix.spinnaker.clouddriver.kubernetes.config.KubernetesAccountProperties.ManagedAccount;
import com.netflix.spinnaker.clouddriver.kubernetes.description.GlobalResourcePropertyRegistry;
import com.netflix.spinnaker.clouddriver.kubernetes.description.KubernetesCoordinates;
import com.netflix.spinnaker.clouddriver.kubernetes.description.KubernetesSpinnakerKindMap;
import com.netflix.spinnaker.clouddriver.kubernetes.description.ResourcePropertyRegistry;
import com.netflix.spinnaker.clouddriver.kubernetes.description.manifest.KubernetesApiVersion;
import com.netflix.spinnaker.clouddriver.kubernetes.description.manifest.KubernetesKind;
import com.netflix.spinnaker.clouddriver.kubernetes.description.manifest.KubernetesKindProperties;
import com.netflix.spinnaker.clouddriver.kubernetes.description.manifest.KubernetesManifest;
import com.netflix.spinnaker.clouddriver.kubernetes.names.KubernetesManifestNamer;
import com.netflix.spinnaker.clouddriver.kubernetes.op.handler.KubernetesConfigMapHandler;
import com.netflix.spinnaker.clouddriver.kubernetes.op.handler.KubernetesDeploymentHandler;
import com.netflix.spinnaker.clouddriver.kubernetes.op.handler.KubernetesHandler;
import com.netflix.spinnaker.clouddriver.kubernetes.op.handler.KubernetesPodHandler;
import com.netflix.spinnaker.clouddriver.kubernetes.op.handler.KubernetesReplicaSetHandler;
import com.netflix.spinnaker.clouddriver.kubernetes.op.handler.KubernetesServiceHandler;
import com.netflix.spinnaker.clouddriver.kubernetes.op.handler.KubernetesStorageClassHandler;
import com.netflix.spinnaker.clouddriver.kubernetes.op.handler.KubernetesUnregisteredCustomResourceHandler;
import com.netflix.spinnaker.clouddriver.kubernetes.security.KubernetesCredentials;
import com.netflix.spinnaker.clouddriver.kubernetes.security.KubernetesNamedAccountCredentials;
import com.netflix.spinnaker.clouddriver.model.Front50Application;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;
import org.mockito.stubbing.Answer;

public class BaseKubernetesCachingAgentTest {
  protected static final String ACCOUNT = "my-account";
  protected static final String NAMESPACE1 = "test-namespace";
  protected static final String NAMESPACE2 = "test-namespace2";
  protected static final String DEPLOYMENT_NAME = "my-deployment";
  protected static final String REPLICA_SET_NAME = "my-replicaset";
  protected static final String POD_NAME = "my-pod";
  protected static final String STORAGE_CLASS_NAME = "my-storage-class";
  protected static final String MONIKER_CLUSTER = "my-cluster";
  protected static final String MONIKER_APPLICATION = "my-application";

  protected static final String DEPLOYMENT_KIND = KubernetesKind.DEPLOYMENT.toString();
  protected static final String REPLICA_SET_KIND = KubernetesKind.REPLICA_SET.toString();
  protected static final String POD_KIND = KubernetesKind.POD.toString();
  protected static final String STORAGE_CLASS_KIND = KubernetesKind.STORAGE_CLASS.toString();
  protected static final String APPLICATION_KIND = LogicalKind.APPLICATIONS.toString();
  protected static final String CLUSTER_KIND = LogicalKind.CLUSTERS.toString();

  protected static final ImmutableMap<KubernetesKind, KubernetesKindProperties> kindProperties =
      ImmutableMap.<KubernetesKind, KubernetesKindProperties>builder()
          .put(
              KubernetesKind.DEPLOYMENT,
              KubernetesKindProperties.create(KubernetesKind.DEPLOYMENT, true))
          .put(
              KubernetesKind.STORAGE_CLASS,
              KubernetesKindProperties.create(KubernetesKind.STORAGE_CLASS, false))
          .put(
              KubernetesKind.NAMESPACE,
              KubernetesKindProperties.create(KubernetesKind.NAMESPACE, false))
          .put(KubernetesKind.POD, KubernetesKindProperties.create(KubernetesKind.POD, true))
          .put(
              KubernetesKind.REPLICA_SET,
              KubernetesKindProperties.create(KubernetesKind.REPLICA_SET, true))
          .build();

  protected static final ObjectMapper objectMapper = new ObjectMapper();
  protected static final ResourcePropertyRegistry resourcePropertyRegistry =
      new GlobalResourcePropertyRegistry(
          ImmutableList.of(), new KubernetesUnregisteredCustomResourceHandler());
  protected static final ImmutableList<KubernetesHandler> handlers =
      ImmutableList.of(
          new KubernetesDeploymentHandler(),
          new KubernetesReplicaSetHandler(),
          new KubernetesServiceHandler(),
          new KubernetesPodHandler(),
          new KubernetesConfigMapHandler());
  protected static final KubernetesSpinnakerKindMap kubernetesSpinnakerKindMap =
      new KubernetesSpinnakerKindMap(handlers);

  protected static String kubeconfigFile;

  /** A test Deployment manifest */
  protected static KubernetesManifest deploymentManifest(String deploymentName) {
    KubernetesManifest deployment = new KubernetesManifest();
    deployment.put("metadata", new HashMap<>());
    deployment.getAnnotations().put("moniker.spinnaker.io/cluster", MONIKER_CLUSTER);
    deployment.getAnnotations().put("moniker.spinnaker.io/application", MONIKER_APPLICATION);
    deployment.setNamespace(NAMESPACE1);
    deployment.setKind(KubernetesKind.DEPLOYMENT);
    deployment.setApiVersion(KubernetesApiVersion.APPS_V1);
    deployment.setName(deploymentName);
    return deployment;
  }

  /** A test ReplicaSet manifest */
  protected static KubernetesManifest replicaSetManifest(
      String replicaSetName, @Nullable String deploymentName) {
    KubernetesManifest replicaSet = new KubernetesManifest();
    HashMap<Object, Object> metadata = new HashMap<>();
    if (deploymentName != null) {
      metadata.put(
          "ownerReferences", List.of(Map.of("name", deploymentName, "kind", "Deployment")));
    }
    replicaSet.put("metadata", metadata);
    replicaSet.setNamespace(NAMESPACE1);
    replicaSet.setKind(KubernetesKind.REPLICA_SET);
    replicaSet.setApiVersion(KubernetesApiVersion.APPS_V1);
    replicaSet.setName(replicaSetName);
    return replicaSet;
  }

  /** A test Pod manifest */
  protected static KubernetesManifest podManifest(String podName, @Nullable String replicasetName) {
    KubernetesManifest pod = new KubernetesManifest();
    HashMap<Object, Object> metadata = new HashMap<>();
    if (replicasetName != null) {
      metadata.put(
          "ownerReferences", List.of(Map.of("name", replicasetName, "kind", "ReplicaSet")));
    }
    pod.put("metadata", metadata);
    pod.setNamespace(NAMESPACE1);
    pod.setKind(KubernetesKind.POD);
    pod.setApiVersion(KubernetesApiVersion.V1);
    pod.setName(podName);
    return pod;
  }

  /** A test StorageClass manifest object */
  protected static KubernetesManifest storageClassManifest() {
    KubernetesManifest storageClass = new KubernetesManifest();
    storageClass.put("metadata", new HashMap<>());
    storageClass.setKind(KubernetesKind.STORAGE_CLASS);
    storageClass.setApiVersion(KubernetesApiVersion.fromString("storage.k8s.io/v1"));
    storageClass.setName(STORAGE_CLASS_NAME);
    return storageClass;
  }

  /** Returns a mock KubernetesCredentials object */
  protected static KubernetesCredentials mockKubernetesCredentials(String deploymentName) {
    KubernetesCredentials credentials = mock(KubernetesCredentials.class);
    when(credentials.getAccountName()).thenReturn(ACCOUNT);
    when(credentials.getKubeconfigFile()).thenReturn(kubeconfigFile);
    when(credentials.getGlobalKinds()).thenReturn(kindProperties.keySet().asList());
    when(credentials.getKindProperties(any(KubernetesKind.class)))
        .thenAnswer(invocation -> kindProperties.get(invocation.getArgument(0)));
    when(credentials.getDeclaredNamespaces()).thenReturn(ImmutableList.of(NAMESPACE1, NAMESPACE2));
    when(credentials.getResourcePropertyRegistry()).thenReturn(resourcePropertyRegistry);
    when(credentials.get(
            KubernetesCoordinates.builder()
                .kind(KubernetesKind.DEPLOYMENT)
                .namespace(NAMESPACE1)
                .name(deploymentName)
                .build()))
        .thenReturn(deploymentManifest(deploymentName));
    when(credentials.get(
            KubernetesCoordinates.builder()
                .kind(KubernetesKind.STORAGE_CLASS)
                .name(STORAGE_CLASS_NAME)
                .build()))
        .thenReturn(storageClassManifest());
    when(credentials.list(any(List.class), any()))
        .thenAnswer(
            (Answer<ImmutableList<KubernetesManifest>>)
                invocation -> {
                  Object[] args = invocation.getArguments();
                  ImmutableSet<KubernetesKind> kinds =
                      ImmutableSet.copyOf((List<KubernetesKind>) args[0]);
                  String namespace = (String) args[1];
                  ImmutableList.Builder<KubernetesManifest> result = new ImmutableList.Builder<>();
                  if (kinds.contains(KubernetesKind.DEPLOYMENT) && NAMESPACE1.equals(namespace)) {
                    result.add(deploymentManifest(deploymentName));
                  }
                  if (kinds.contains(KubernetesKind.STORAGE_CLASS)) {
                    result.add(storageClassManifest());
                  }
                  return result.build();
                });
    when(credentials.getNamer()).thenReturn(new KubernetesManifestNamer());
    when(credentials.isValidKind(any(KubernetesKind.class))).thenReturn(true);
    when(credentials.getKubernetesSpinnakerKindMap())
        .thenReturn(
            new KubernetesSpinnakerKindMap(
                List.of(new KubernetesDeploymentHandler(), new KubernetesStorageClassHandler())));
    return credentials;
  }

  /**
   * Returns a KubernetesNamedAccountCredentials that contains a mock KubernetesCredentials object
   */
  protected static KubernetesNamedAccountCredentials getNamedAccountCredentials() {
    return getNamedAccountCredentials(DEPLOYMENT_NAME);
  }

  /**
   * Returns a KubernetesNamedAccountCredentials with a custom deployment name that contains a mock
   * KubernetesCredentials object
   */
  protected static KubernetesNamedAccountCredentials getNamedAccountCredentials(
      String deploymentName) {
    ManagedAccount managedAccount = new ManagedAccount();
    managedAccount.setName(ACCOUNT);

    KubernetesCredentials mockCredentials = mockKubernetesCredentials(deploymentName);
    KubernetesCredentials.Factory credentialFactory = mock(KubernetesCredentials.Factory.class);
    when(credentialFactory.build(managedAccount)).thenReturn(mockCredentials);
    return new KubernetesNamedAccountCredentials(managedAccount, credentialFactory);
  }

  protected static ImmutableList<String> getAuthoritativeTypes(
      Collection<AgentDataType> agentDataTypes) {
    return agentDataTypes.stream()
        .filter(dataType -> dataType.getAuthority() == AUTHORITATIVE)
        .map(AgentDataType::getTypeName)
        .collect(toImmutableList());
  }

  protected void validateStorageClassInCacheResult(
      String storageClassKey, Map<String, Collection<CacheData>> cacheResults) {
    assertThat(cacheResults).containsKey(STORAGE_CLASS_KIND);
    Collection<CacheData> storageClasses = cacheResults.get(STORAGE_CLASS_KIND);
    assertThat(storageClasses).extracting(CacheData::getId).contains(storageClassKey);
    assertThat(storageClasses)
        .extracting(storageClass -> storageClass.getAttributes().get("name"))
        .containsExactly(STORAGE_CLASS_NAME);
  }

  protected Set<Front50Application> getApplicationsFromFront50(String fileName)
      throws JsonProcessingException {
    return objectMapper.readValue(
        getResource(fileName), new TypeReference<Set<Front50Application>>() {});
  }

  protected String getResource(String name) {
    try {
      return Resources.toString(
          BaseKubernetesCachingAgentTest.class.getResource(name), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
