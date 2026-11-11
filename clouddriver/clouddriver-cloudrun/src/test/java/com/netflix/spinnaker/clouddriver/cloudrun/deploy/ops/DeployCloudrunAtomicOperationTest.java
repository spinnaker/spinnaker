/*
 * Copyright 2026 spinnaker.io
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

package com.netflix.spinnaker.clouddriver.cloudrun.deploy.ops;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.google.api.services.run.v1.CloudRun;
import com.google.api.services.run.v1.model.ListRevisionsResponse;
import com.netflix.spinnaker.clouddriver.cloudrun.deploy.CloudrunServiceClient;
import com.netflix.spinnaker.clouddriver.cloudrun.deploy.description.DeployCloudrunDescription;
import com.netflix.spinnaker.clouddriver.cloudrun.deploy.exception.CloudrunOperationException;
import com.netflix.spinnaker.clouddriver.cloudrun.provider.view.CloudrunLoadBalancerProvider;
import com.netflix.spinnaker.clouddriver.cloudrun.security.CloudrunCredentials;
import com.netflix.spinnaker.clouddriver.cloudrun.security.CloudrunNamedAccountCredentials;
import com.netflix.spinnaker.clouddriver.data.task.Task;
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Inspects the manifest that the legacy server group deploy hands to Cloud Run. */
class DeployCloudrunAtomicOperationTest {

  private static final ObjectMapper YAML = new ObjectMapper(new YAMLFactory());

  private static final String CONFIG_FILE =
      String.join(
          "\n",
          "apiVersion: serving.knative.dev/v1",
          "kind: Service",
          "metadata:",
          "  annotations:",
          "    run.googleapis.com/maxScale: \"1\"",
          "  labels:",
          "    cloud.googleapis.com/location: europe-west2",
          "  name: exampleservice",
          "spec:",
          "  template:",
          "    metadata:",
          "      name: ignored",
          "    spec:",
          "      containers:",
          "        - env:",
          "            - name: environment",
          "              value: test-utils",
          "          image: example-image-url",
          "          startupProbe:",
          "            tcpSocket:",
          "              port: 8182",
          "");

  private DeployCloudrunDescription description;
  private DeployCloudrunAtomicOperation operation;
  private final List<Map<String, Object>> deployedManifests = new ArrayList<>();
  private final List<String> regions = new ArrayList<>();

  @BeforeEach
  void setUp() throws IOException {
    TaskRepository.threadLocalTask.set(mock(Task.class));

    CloudRun cloudRun = mock(CloudRun.class, RETURNS_DEEP_STUBS);
    when(cloudRun.namespaces().revisions().list(anyString()).execute())
        .thenReturn(new ListRevisionsResponse());
    CloudrunCredentials cloudrunCredentials = mock(CloudrunCredentials.class);
    when(cloudrunCredentials.getCloudrun(anyString())).thenReturn(cloudRun);

    CloudrunNamedAccountCredentials credentials =
        new CloudrunNamedAccountCredentials.Builder()
            .setName("cloudrunaccount")
            .setAccountType("cloudrun")
            .setCloudProvider("cloudrun")
            .setApplicationName("spinnaker")
            .setCredentials(cloudrunCredentials)
            .setCloudRun(cloudRun)
            .setEnvironment("test")
            .setJsonKey("jsonkey")
            .setLiveLookupsEnabled(false)
            .setLocalRepositoryDirectory("/localdirectory")
            .setJsonPath("/jsonpath")
            .setProject("my-project")
            .build();

    description = new DeployCloudrunDescription();
    description.setAccountName("cloudrunaccount");
    description.setAccount("cloudrunaccount");
    description.setApplication("myapp");
    description.setRegion("us-central1");
    description.setCredentials(credentials);

    CloudrunServiceClient serviceClient = mock(CloudrunServiceClient.class);
    doAnswer(
            invocation -> {
              regions.add(invocation.getArgument(0));
              deployedManifests.add(invocation.getArgument(1));
              return null;
            })
        .when(serviceClient)
        .deployService(anyString(), anyMap());

    operation = new DeployCloudrunAtomicOperation(description);
    operation.serviceClient = serviceClient;
    operation.provider = mock(CloudrunLoadBalancerProvider.class);
  }

  @Test
  void injectsServiceNameRevisionNameAndApplicationAnnotation() throws IOException {
    description.setConfigFiles(List.of(CONFIG_FILE));

    operation.operate(new ArrayList<>());

    Map<String, Object> deployed = singleDeployedManifest();
    assertThat(path(deployed, "metadata", "name")).isEqualTo("myapp");
    assertThat((String) path(deployed, "spec", "template", "metadata", "name"))
        .startsWith("myapp-v");
    assertThat(path(deployed, "metadata", "annotations", "spinnaker/application"))
        .isEqualTo("myapp");
  }

  @Test
  void preservesFieldsOutsideTheTypedModel() throws IOException {
    description.setConfigFiles(List.of(CONFIG_FILE));

    operation.operate(new ArrayList<>());

    Map<String, Object> deployed = singleDeployedManifest();
    Map<String, Object> container =
        ((List<Map<String, Object>>) path(deployed, "spec", "template", "spec", "containers"))
            .get(0);
    assertThat(container)
        .containsEntry("env", List.of(Map.of("name", "environment", "value", "test-utils")))
        .containsEntry("startupProbe", Map.of("tcpSocket", Map.of("port", 8182)));
    assertThat((Map<String, Object>) path(deployed, "metadata", "annotations"))
        .containsEntry("run.googleapis.com/maxScale", "1");
  }

  @Test
  void deploysWhenTheConfigFileHasNoLabelsOrAnnotations() throws IOException {
    description.setConfigFiles(
        List.of(
            String.join(
                "\n",
                "apiVersion: serving.knative.dev/v1",
                "kind: Service",
                "metadata:",
                "  name: exampleservice",
                "spec:",
                "  template:",
                "    spec:",
                "      containers:",
                "        - image: example-image-url",
                "")));

    operation.operate(new ArrayList<>());

    assertThat(regions).containsExactly("us-central1");
    assertThat(path(singleDeployedManifest(), "metadata", "annotations", "spinnaker/application"))
        .isEqualTo("myapp");
  }

  @Test
  void rejectsMoreThanOneConfigFileInsteadOfBuildingAnInvalidGcloudCommand() {
    description.setConfigFiles(List.of(CONFIG_FILE, CONFIG_FILE));

    assertThatThrownBy(() -> operation.operate(new ArrayList<>()))
        .isInstanceOf(CloudrunOperationException.class)
        .hasMessageContaining("exactly one");
    assertThat(deployedManifests).isEmpty();
  }

  private Map<String, Object> singleDeployedManifest() throws IOException {
    assertThat(deployedManifests).hasSize(1);
    return deployedManifests.get(0);
  }

  private static Object path(Map<String, Object> root, String... keys) {
    Object current = root;
    for (String key : keys) {
      if (!(current instanceof Map)) {
        return null;
      }
      current = ((Map<String, Object>) current).get(key);
    }
    return current;
  }
}
