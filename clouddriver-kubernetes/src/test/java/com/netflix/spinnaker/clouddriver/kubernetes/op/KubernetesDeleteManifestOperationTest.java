/*
 * Copyright 2021 Salesforce, Inc.
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

package com.netflix.spinnaker.clouddriver.kubernetes.op;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.MapType;
import com.google.common.collect.ImmutableList;
import com.netflix.spinnaker.clouddriver.data.task.DefaultTask;
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository;
import com.netflix.spinnaker.clouddriver.kubernetes.config.KubernetesAccountProperties.ManagedAccount;
import com.netflix.spinnaker.clouddriver.kubernetes.converter.manifest.KubernetesDeleteManifestConverter;
import com.netflix.spinnaker.clouddriver.kubernetes.description.GlobalResourcePropertyRegistry;
import com.netflix.spinnaker.clouddriver.kubernetes.description.ResourcePropertyRegistry;
import com.netflix.spinnaker.clouddriver.kubernetes.description.manifest.KubernetesDeleteManifestDescription;
import com.netflix.spinnaker.clouddriver.kubernetes.description.manifest.KubernetesKind;
import com.netflix.spinnaker.clouddriver.kubernetes.op.handler.KubernetesReplicaSetHandler;
import com.netflix.spinnaker.clouddriver.kubernetes.op.handler.KubernetesServiceHandler;
import com.netflix.spinnaker.clouddriver.kubernetes.op.handler.KubernetesUnregisteredCustomResourceHandler;
import com.netflix.spinnaker.clouddriver.kubernetes.op.manifest.KubernetesDeleteManifestOperation;
import com.netflix.spinnaker.clouddriver.kubernetes.security.KubernetesCredentials;
import com.netflix.spinnaker.clouddriver.kubernetes.security.KubernetesNamedAccountCredentials;
import com.netflix.spinnaker.clouddriver.kubernetes.security.KubernetesSelectorList;
import com.netflix.spinnaker.credentials.CredentialsRepository;
import io.kubernetes.client.openapi.models.V1DeleteOptions;
import java.io.IOException;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

/**
 * Testing the orphanDependent and cascading Pipeline Options
 *
 * <p>Based on DeleteOptions KubectlJobExecutor.delete will determine whether the --cascade flag is
 * set. If deleteOptions.OrphanDependents is false or null -> --cascade is true. If
 * deleteOptions.OrphanDependents is true -> --cascade is false. (Ex. kubectl delete replicaset
 * my-repset --cascade=false)
 */
@RunWith(JUnitPlatform.class)
public class KubernetesDeleteManifestOperationTest {
  private static final ResourcePropertyRegistry resourcePropertyRegistry =
      new GlobalResourcePropertyRegistry(
          ImmutableList.of(new KubernetesReplicaSetHandler(), new KubernetesServiceHandler()),
          new KubernetesUnregisteredCustomResourceHandler());
  private static KubernetesDeleteManifestConverter converter;
  private static ObjectMapper mapper;
  private static MapType mapType;

  @BeforeAll
  static void setup() {
    CredentialsRepository<KubernetesNamedAccountCredentials> credentialsRepository =
        Mockito.mock(CredentialsRepository.class);
    when(credentialsRepository.getOne(any(String.class)))
        .thenReturn(Mockito.mock(KubernetesNamedAccountCredentials.class));

    converter = new KubernetesDeleteManifestConverter();
    converter.setCredentialsRepository(credentialsRepository);

    mapper = converter.getObjectMapper();
    mapType = mapper.getTypeFactory().constructMapType(Map.class, String.class, Object.class);
  }

  @BeforeEach
  void setupTest() {
    // Store a Mock Task in a thread before each test
    // so TaskRepository will be able to get it during delete to update the Task
    TaskRepository.threadLocalTask.set(new DefaultTask("task-id"));
  }

  @Test
  public void orphanDependentsTrueTest() throws IOException {
    String pipelineJSON =
        "{ "
            + " \"account\": \"kubernetes-account\","
            + "  \"kinds\": ["
            + "    \"deployment\""
            + "  ],"
            + "  \"options\": {"
            + "    \"orphanDependents\": \"true\""
            + "  }"
            + "}";
    Map<String, Object> pipeline = mapper.readValue(pipelineJSON, mapType);

    KubernetesDeleteManifestDescription description = buildDeleteManifestDescription(pipeline);

    V1DeleteOptions deleteOptions = deleteAndCaptureDeleteOptions(description);
    assertTrue(deleteOptions.getOrphanDependents());
  }

  @Test
  public void orphanDependentsFalseTest() throws IOException {
    String pipelineJSON =
        "{ "
            + " \"account\": \"kubernetes-account\","
            + "  \"kinds\": [ \"deployment\" ],"
            + "  \"options\": {"
            + "    \"orphanDependents\": \"false\""
            + "  }"
            + "}";
    Map<String, Object> pipeline = mapper.readValue(pipelineJSON, mapType);

    KubernetesDeleteManifestDescription description = buildDeleteManifestDescription(pipeline);

    V1DeleteOptions deleteOptions = deleteAndCaptureDeleteOptions(description);
    assertFalse(deleteOptions.getOrphanDependents());
  }

  @Test
  public void noOptionsDefaultTest() throws IOException {
    String pipelineJSON =
        "{ \"account\": \"kubernetes-account\"," + "  \"kinds\": [ \"deployment\" ]" + "}";
    Map<String, Object> pipeline = mapper.readValue(pipelineJSON, mapType);

    KubernetesDeleteManifestDescription description = buildDeleteManifestDescription(pipeline);

    V1DeleteOptions deleteOptions = deleteAndCaptureDeleteOptions(description);
    assertNull(deleteOptions.getOrphanDependents());
  }

  @Test()
  public void cascadingTrueSetOrphanDependentsFalseTest() throws IOException {
    String pipelineJSON =
        "{ "
            + " \"account\": \"kubernetes-account\","
            + "  \"kinds\": [ \"deployment\" ],"
            + "  \"options\": {"
            + "    \"cascading\": \"true\""
            + "  }"
            + "}";
    Map<String, Object> pipeline = mapper.readValue(pipelineJSON, mapType);

    KubernetesDeleteManifestDescription description = buildDeleteManifestDescription(pipeline);

    V1DeleteOptions deleteOptions = deleteAndCaptureDeleteOptions(description);
    assertFalse(deleteOptions.getOrphanDependents());
  }

  @Test()
  public void cascadingFalseSetOrphanDependentsTrueTest() throws IOException {
    String pipelineJSON =
        "{ "
            + " \"account\": \"kubernetes-account\","
            + "  \"kinds\": [ \"deployment\" ],"
            + "  \"options\": {"
            + "    \"cascading\": \"false\""
            + "  }"
            + "}";
    Map<String, Object> pipeline = mapper.readValue(pipelineJSON, mapType);

    KubernetesDeleteManifestDescription description = buildDeleteManifestDescription(pipeline);

    V1DeleteOptions deleteOptions = deleteAndCaptureDeleteOptions(description);
    assertTrue(deleteOptions.getOrphanDependents());
  }

  @Test()
  // Set both orphanDependents and cascading options and show that
  // the orphanDependents options has precedence
  public void showTrueOrphanDependentsPrecedenceTest() throws IOException {
    String pipelineJSON =
        "{ "
            + " \"account\": \"kubernetes-account\","
            + "  \"kinds\": [ \"deployment\" ],"
            + "  \"options\": {"
            + "    \"cascading\": \"true\","
            + "    \"orphanDependents\": \"true\""
            + "  }"
            + "}";
    Map<String, Object> pipeline = mapper.readValue(pipelineJSON, mapType);

    KubernetesDeleteManifestDescription description = buildDeleteManifestDescription(pipeline);

    V1DeleteOptions deleteOptions = deleteAndCaptureDeleteOptions(description);
    assertTrue(deleteOptions.getOrphanDependents());
  }

  @Test()
  public void showFalseOrphanDependentsPrecedenceTest() throws IOException {
    String pipelineJSON =
        "{ "
            + " \"account\": \"kubernetes-account\","
            + "  \"kinds\": [ \"deployment\" ],"
            + "  \"options\": {"
            + "    \"cascading\": \"false\","
            + "    \"orphanDependents\": \"false\""
            + "  }"
            + "}";
    Map<String, Object> pipeline = mapper.readValue(pipelineJSON, mapType);

    KubernetesDeleteManifestDescription description = buildDeleteManifestDescription(pipeline);

    V1DeleteOptions deleteOptions = deleteAndCaptureDeleteOptions(description);
    assertFalse(deleteOptions.getOrphanDependents());
  }

  private static V1DeleteOptions deleteAndCaptureDeleteOptions(
      KubernetesDeleteManifestDescription description) {
    KubernetesCredentials mockKubernetesCreds = description.getCredentials().getCredentials();

    new KubernetesDeleteManifestOperation(description).operate(ImmutableList.of());
    ArgumentCaptor<V1DeleteOptions> deleteOptionsCaptor =
        ArgumentCaptor.forClass(V1DeleteOptions.class);
    verify(mockKubernetesCreds)
        .delete(
            any(KubernetesKind.class),
            anyString(),
            anyString(),
            any(KubernetesSelectorList.class),
            deleteOptionsCaptor.capture());
    return deleteOptionsCaptor.getValue();
  }

  private static KubernetesDeleteManifestDescription buildDeleteManifestDescription(
      Map<String, Object> inputMap) {
    KubernetesDeleteManifestDescription description = converter.convertDescription(inputMap);
    description.setCredentials(getNamedAccountCredentials());
    return description;
  }

  private static KubernetesNamedAccountCredentials getNamedAccountCredentials() {
    ManagedAccount managedAccount = new ManagedAccount();
    managedAccount.setName("my-account");

    KubernetesCredentials mockCredentials = mock(KubernetesCredentials.class);
    when(mockCredentials.getResourcePropertyRegistry()).thenReturn(resourcePropertyRegistry);

    KubernetesCredentials.Factory credentialFactory = mock(KubernetesCredentials.Factory.class);
    when(credentialFactory.build(managedAccount)).thenReturn(mockCredentials);

    return new KubernetesNamedAccountCredentials(managedAccount, credentialFactory);
  }
}
