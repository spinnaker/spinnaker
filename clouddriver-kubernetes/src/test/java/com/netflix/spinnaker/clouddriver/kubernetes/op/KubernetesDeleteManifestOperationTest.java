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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.MapType;
import com.google.common.collect.ImmutableList;
import com.netflix.spinnaker.clouddriver.data.task.DefaultTask;
import com.netflix.spinnaker.clouddriver.data.task.Task;
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository;
import com.netflix.spinnaker.clouddriver.kubernetes.config.KubernetesAccountProperties.ManagedAccount;
import com.netflix.spinnaker.clouddriver.kubernetes.converter.manifest.KubernetesDeleteManifestConverter;
import com.netflix.spinnaker.clouddriver.kubernetes.description.GlobalResourcePropertyRegistry;
import com.netflix.spinnaker.clouddriver.kubernetes.description.manifest.KubernetesApiGroup;
import com.netflix.spinnaker.clouddriver.kubernetes.description.manifest.KubernetesDeleteManifestDescription;
import com.netflix.spinnaker.clouddriver.kubernetes.description.manifest.KubernetesKind;
import com.netflix.spinnaker.clouddriver.kubernetes.op.handler.KubernetesCustomResourceDefinitionHandler;
import com.netflix.spinnaker.clouddriver.kubernetes.op.handler.KubernetesCustomResourceHandler;
import com.netflix.spinnaker.clouddriver.kubernetes.op.handler.KubernetesHandler;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

/** Test the deleteManifest stage. */
public class KubernetesDeleteManifestOperationTest {
  private static final GlobalResourcePropertyRegistry resourcePropertyRegistry =
      new GlobalResourcePropertyRegistry(
          ImmutableList.of(
              new KubernetesReplicaSetHandler(),
              new KubernetesServiceHandler(),
              new KubernetesCustomResourceDefinitionHandler()),
          new KubernetesUnregisteredCustomResourceHandler());
  private static final KubernetesKind customResource =
      KubernetesKind.from(
          "MyCRD", KubernetesApiGroup.fromString("foo.com")); // arbitrary custom/non-native kind
  private static final KubernetesHandler customResourceHandler =
      new KubernetesCustomResourceHandler(customResource);
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

    // Add a handler for custom resources to demonstrate that it's possible to delete them.
    resourcePropertyRegistry.updateCrdProperties(ImmutableList.of(customResourceHandler));
  }

  @BeforeEach
  void setupTest() {
    // Store a Mock Task in a thread before each test
    // so TaskRepository will be able to get it during delete to update the Task
    TaskRepository.threadLocalTask.set(new DefaultTask("task-id"));
  }

  @Test
  public void deleteUnregisteredCRD() throws IOException {
    String pipelineJSON =
        "{ "
            + " \"account\": \"kubernetes-account\","
            + " \"manifestName\": \"customResourceDefinition mycrd.foo.com\""
            + " }";
    Map<String, Object> pipeline = mapper.readValue(pipelineJSON, mapType);

    KubernetesDeleteManifestDescription description = buildDeleteManifestDescription(pipeline);

    KubernetesCredentials mockKubernetesCreds = description.getCredentials().getCredentials();

    new KubernetesDeleteManifestOperation(description).operate(ImmutableList.of());

    ArgumentCaptor<KubernetesKind> kindCaptor = ArgumentCaptor.forClass(KubernetesKind.class);
    ArgumentCaptor<String> namespaceCaptor = ArgumentCaptor.forClass(String.class);
    verify(mockKubernetesCreds)
        .delete(
            kindCaptor.capture(),
            namespaceCaptor.capture(),
            anyString(),
            any(KubernetesSelectorList.class),
            any(V1DeleteOptions.class),
            any(Task.class),
            anyString());

    assertEquals(KubernetesKind.CUSTOM_RESOURCE_DEFINITION, kindCaptor.getValue());
  }

  @ParameterizedTest(name = "deleteUnregisteredCustomResource useNamespace = {0}")
  @ValueSource(booleans = {true, false})
  public void deleteUnregisteredCustomResource(boolean useNamespace) throws IOException {
    String namespace = "";
    String namespaceJson = "";

    if (useNamespace) {
      namespace = "test-namespace";
      namespaceJson = ", \"location\": \"" + namespace + "\"";
    }

    String pipelineJSON =
        "{ "
            + " \"account\": \"kubernetes-account\","
            + " \"manifestName\": \""
            + customResource.toString()
            + " my-custom-resource\""
            + namespaceJson
            + " }";

    Map<String, Object> pipeline = mapper.readValue(pipelineJSON, mapType);

    KubernetesDeleteManifestDescription description = buildDeleteManifestDescription(pipeline);

    KubernetesCredentials mockKubernetesCreds = description.getCredentials().getCredentials();

    new KubernetesDeleteManifestOperation(description).operate(ImmutableList.of());

    ArgumentCaptor<KubernetesKind> kindCaptor = ArgumentCaptor.forClass(KubernetesKind.class);
    ArgumentCaptor<String> namespaceCaptor = ArgumentCaptor.forClass(String.class);
    verify(mockKubernetesCreds)
        .delete(
            kindCaptor.capture(),
            namespaceCaptor.capture(),
            anyString(),
            any(KubernetesSelectorList.class),
            any(V1DeleteOptions.class),
            any(Task.class),
            anyString());

    assertEquals(customResource, kindCaptor.getValue());
    assertEquals(namespace, namespaceCaptor.getValue());
  }

  @Test
  public void deleteUnregisteredCustomResourceViaLabelSelector() throws IOException {
    String pipelineJSON =
        "{ "
            + " \"account\": \"kubernetes-account\","
            + " \"kinds\": [ \""
            + customResource.toString()
            + "\" ],"
            + " \"labelSelectors\": {"
            + "   \"selectors\": ["
            + "     {"
            + "       \"key\": \"foo\","
            + "       \"kind\": \"EQUALS\","
            + "       \"values\": ["
            + "         \"bar\""
            + "       ]"
            + "     }"
            + "   ]"
            + " }"
            + "}";
    Map<String, Object> pipeline = mapper.readValue(pipelineJSON, mapType);

    KubernetesDeleteManifestDescription description = buildDeleteManifestDescription(pipeline);

    KubernetesCredentials mockKubernetesCreds = description.getCredentials().getCredentials();

    new KubernetesDeleteManifestOperation(description).operate(ImmutableList.of());

    ArgumentCaptor<KubernetesKind> kindCaptor = ArgumentCaptor.forClass(KubernetesKind.class);
    ArgumentCaptor<KubernetesSelectorList> labelSelectorsCaptor =
        ArgumentCaptor.forClass(KubernetesSelectorList.class);
    verify(mockKubernetesCreds)
        .delete(
            kindCaptor.capture(),
            anyString(),
            anyString(),
            labelSelectorsCaptor.capture(),
            any(V1DeleteOptions.class),
            any(Task.class),
            anyString());

    assertEquals(customResource, kindCaptor.getValue());
    assertEquals(
        KubernetesSelectorList.fromMatchLabels(Map.of("foo", "bar")),
        labelSelectorsCaptor.getValue());
  }

  @Test
  public void orphanDependentsTrue() throws IOException {
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
    assertEquals("orphan", deleteOptions.getPropagationPolicy());
  }

  @Test
  public void orphanDependentsFalse() throws IOException {
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
    assertEquals("background", deleteOptions.getPropagationPolicy());
  }

  @Test
  public void noOptionsDefault() throws IOException {
    String pipelineJSON =
        "{ \"account\": \"kubernetes-account\"," + "  \"kinds\": [ \"deployment\" ]" + "}";
    Map<String, Object> pipeline = mapper.readValue(pipelineJSON, mapType);

    KubernetesDeleteManifestDescription description = buildDeleteManifestDescription(pipeline);

    V1DeleteOptions deleteOptions = deleteAndCaptureDeleteOptions(description);
    assertNull(deleteOptions.getOrphanDependents());
  }

  @Test
  public void cascadingTrue() throws IOException {
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
    assertEquals("background", deleteOptions.getPropagationPolicy());
  }

  @Test
  public void cascadingFalse() throws IOException {
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
    assertEquals("orphan", deleteOptions.getPropagationPolicy());
  }

  @ParameterizedTest(name = "cascadingLiteralValues {0}")
  @ValueSource(strings = {"foregound", "background", "orphan", "bogus"})
  public void cascadingStringValues(String cascadingValue) throws IOException {
    String pipelineJSON =
        "{ "
            + " \"account\": \"kubernetes-account\","
            + "  \"kinds\": [ \"deployment\" ],"
            + "  \"options\": {"
            + "    \"cascading\": \""
            + cascadingValue
            + "\""
            + "  }"
            + "}";
    Map<String, Object> pipeline = mapper.readValue(pipelineJSON, mapType);

    KubernetesDeleteManifestDescription description = buildDeleteManifestDescription(pipeline);

    V1DeleteOptions deleteOptions = deleteAndCaptureDeleteOptions(description);
    assertEquals(cascadingValue, deleteOptions.getPropagationPolicy());
  }

  @Test
  // Set both orphanDependents and cascading options and show that
  // the orphanDependents options has precedence
  public void cascadingTrueOrphanDependentsPrecedenceTest() throws IOException {
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
    assertEquals("orphan", deleteOptions.getPropagationPolicy());
  }

  @Test
  public void cascadingFalseOrphanDependentsPrecedenceTest() throws IOException {
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
    assertEquals("background", deleteOptions.getPropagationPolicy());
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
            deleteOptionsCaptor.capture(),
            any(Task.class),
            anyString());

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
