/*
 * Copyright 2020 Armory
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

package com.netflix.spinnaker.clouddriver.kubernetes.manifest;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.MapType;
import com.google.common.io.CharStreams;
import com.netflix.spinnaker.clouddriver.kubernetes.converter.manifest.KubernetesDeployManifestConverter;
import com.netflix.spinnaker.clouddriver.kubernetes.description.manifest.KubernetesDeployManifestDescription;
import com.netflix.spinnaker.clouddriver.kubernetes.description.manifest.KubernetesKindProperties;
import com.netflix.spinnaker.clouddriver.kubernetes.description.manifest.KubernetesManifest;
import com.netflix.spinnaker.clouddriver.kubernetes.security.KubernetesCredentials;
import com.netflix.spinnaker.clouddriver.kubernetes.security.KubernetesNamedAccountCredentials;
import com.netflix.spinnaker.credentials.CredentialsRepository;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

public class KubernetesDeployManifestConverterTest {
  private static KubernetesDeployManifestConverter converter;
  private static ObjectMapper mapper;
  private static MapType mapType;

  @BeforeAll
  static void setup() {
    CredentialsRepository<KubernetesNamedAccountCredentials> credentialsRepository =
        Mockito.mock(CredentialsRepository.class);
    Mockito.when(credentialsRepository.getOne("kubernetes"))
        .thenReturn(Mockito.mock(KubernetesNamedAccountCredentials.class));
    converter = new KubernetesDeployManifestConverter(credentialsRepository, null);
    mapper = converter.getObjectMapper();
    mapType = mapper.getTypeFactory().constructMapType(Map.class, String.class, Object.class);
  }

  @Test
  public void manifestListDeserialized() throws IOException {
    String deploymentJson = getResourceAsString("deployment-manifest.json");
    String serviceJson = getResourceAsString("service-manifest.json");

    Map<String, Object> deploymentMap = mapper.readValue(deploymentJson, mapType);
    Map<String, Object> serviceMap = mapper.readValue(serviceJson, mapType);
    List<Map<String, Object>> manifestList = Arrays.asList(deploymentMap, serviceMap);
    Map<String, Object> inputMap =
        new HashMap<>(Map.of("account", "kubernetes", "manifests", manifestList));
    KubernetesDeployManifestDescription description = converter.convertDescription(inputMap);

    assertThat(description.getManifests()).hasSize(2);
    assertThat(description.getManifests().get(0).getKindName()).isEqualTo("Deployment");
    assertThat(description.getManifests().get(1).getKindName()).isEqualTo("Service");
  }

  @Test
  public void splitManifestList() throws IOException {
    String listTemplate = getResourceAsString("list-manifest.json");
    String deploymentJson = getResourceAsString("deployment-manifest.json");
    String serviceJson = getResourceAsString("service-manifest.json");

    String listJson = String.format(listTemplate, deploymentJson, serviceJson);
    Map<String, Object> listMap = mapper.readValue(listJson, mapType);

    Map<String, Object> inputMap =
        new HashMap<>(
            Map.of("account", "kubernetes", "manifests", Collections.singletonList(listMap)));
    KubernetesDeployManifestDescription description = converter.convertDescription(inputMap);

    assertThat(description.getManifests()).hasSize(2);
    assertThat(description.getManifests().get(0).getKindName()).isEqualTo("Deployment");
    assertThat(description.getManifests().get(1).getKindName()).isEqualTo("Service");
  }

  @Test
  public void noInput() {
    Map<String, Object> inputMap = new HashMap<>(Map.of("account", "kubernetes"));
    KubernetesDeployManifestDescription description = converter.convertDescription(inputMap);
    assertThat(description.getManifests()).isNull();
  }

  @Test
  public void inputWithCustomResource() throws IOException {
    String crdJson = getResourceAsString("crd-manifest.json");
    Map<String, Object> crdMap = mapper.readValue(crdJson, mapType);

    Map<String, Object> inputMap =
        new HashMap<>(
            Map.of("account", "kubernetes", "manifests", Collections.singletonList(crdMap)));
    KubernetesDeployManifestDescription description = converter.convertDescription(inputMap);

    assertThat(description.getManifests()).hasSize(1);
    assertThat(description.getManifests().get(0).getKindName()).isEqualTo("Custom1");
  }

  @Test
  public void splitListManifestWithCustomResource() throws IOException {
    String listTemplate = getResourceAsString("list-manifest.json");
    String deploymentJson = getResourceAsString("deployment-manifest.json");
    String crdJson = getResourceAsString("crd-manifest.json");

    String listJson = String.format(listTemplate, deploymentJson, crdJson);
    Map<String, Object> listMap = mapper.readValue(listJson, mapType);

    Map<String, Object> inputMap =
        new HashMap<>(
            Map.of("account", "kubernetes", "manifests", Collections.singletonList(listMap)));
    KubernetesDeployManifestDescription description = converter.convertDescription(inputMap);

    assertThat(description.getManifests()).hasSize(2);
    assertThat(description.getManifests().get(0).getKindName()).isEqualTo("Deployment");
    assertThat(description.getManifests().get(1).getKindName()).isEqualTo("Custom1");
  }

  @Test
  public void splitListManifestWithNamespaceOverride() throws IOException {
    KubernetesKindProperties prop1 = Mockito.mock(KubernetesKindProperties.class);
    Mockito.when(prop1.isNamespaced()).thenReturn(true, false, true);
    KubernetesCredentials credentials = Mockito.mock(KubernetesCredentials.class);
    Mockito.when(credentials.getKindProperties(Mockito.any())).thenReturn(prop1);
    KubernetesNamedAccountCredentials accountCredentials =
        Mockito.mock(KubernetesNamedAccountCredentials.class);
    Mockito.when(accountCredentials.getCredentials()).thenReturn(credentials);

    CredentialsRepository<KubernetesNamedAccountCredentials> credentialsRepository =
        Mockito.mock(CredentialsRepository.class);
    Mockito.when(credentialsRepository.getOne("kubernetes")).thenReturn(accountCredentials);
    converter = new KubernetesDeployManifestConverter(credentialsRepository, null);

    String listTemplate = getResourceAsString("list-manifest.json");
    String deploymentJson = getResourceAsString("deployment-manifest.json");
    String crdJson = getResourceAsString("crd-manifest.json");

    String listJson = String.format(listTemplate, deploymentJson, crdJson);
    Map<String, Object> listMap = mapper.readValue(listJson, mapType);
    Map<String, Object> deploymentMap = mapper.readValue(deploymentJson, mapType);

    Map<String, Object> inputMap =
        new HashMap<>(
            Map.of(
                "account",
                "kubernetes",
                "manifests",
                Arrays.asList(listMap, deploymentMap),
                "namespaceOverride",
                "testNamespace"));
    KubernetesDeployManifestDescription description = converter.convertDescription(inputMap);
    assertThat(description.getManifests()).hasSize(3);
    assertThat(description.getManifests().get(0).getNamespace()).isEqualTo("testNamespace");
    assertThat(description.getManifests().get(1).getNamespace()).isEqualTo("");
    assertThat(description.getManifests().get(2).getNamespace()).isEqualTo("testNamespace");
  }

  protected String getResourceAsString(String name) throws IOException {
    try (InputStreamReader reader =
        new InputStreamReader(
            KubernetesManifest.class.getResourceAsStream(name), Charset.defaultCharset())) {
      return CharStreams.toString(reader);
    }
  }
}
