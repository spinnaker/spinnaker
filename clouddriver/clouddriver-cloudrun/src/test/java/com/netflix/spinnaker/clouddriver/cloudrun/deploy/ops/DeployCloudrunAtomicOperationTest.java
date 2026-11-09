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
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.google.api.services.run.v1.CloudRun;
import com.google.api.services.run.v1.model.ListRevisionsResponse;
import com.netflix.spinnaker.clouddriver.cloudrun.CloudrunJobExecutor;
import com.netflix.spinnaker.clouddriver.cloudrun.deploy.description.DeployCloudrunDescription;
import com.netflix.spinnaker.clouddriver.cloudrun.deploy.exception.CloudrunOperationException;
import com.netflix.spinnaker.clouddriver.cloudrun.provider.view.CloudrunLoadBalancerProvider;
import com.netflix.spinnaker.clouddriver.cloudrun.security.CloudrunCredentials;
import com.netflix.spinnaker.clouddriver.cloudrun.security.CloudrunNamedAccountCredentials;
import com.netflix.spinnaker.clouddriver.data.task.Task;
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Inspects the YAML that the legacy server group deploy hands to {@code gcloud}. */
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

  @TempDir Path localRepository;

  private DeployCloudrunDescription description;
  private DeployCloudrunAtomicOperation operation;
  private final List<String> deployedYaml = new ArrayList<>();
  private final List<List<String>> commands = new ArrayList<>();

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
            .setLocalRepositoryDirectory(localRepository.toString())
            .setJsonPath("/jsonpath")
            .setProject("my-project")
            .build(mock(CloudrunJobExecutor.class));

    description = new DeployCloudrunDescription();
    description.setAccountName("cloudrunaccount");
    description.setAccount("cloudrunaccount");
    description.setApplication("myapp");
    description.setRegion("us-central1");
    description.setCredentials(credentials);

    CloudrunJobExecutor jobExecutor = mock(CloudrunJobExecutor.class);
    doAnswer(
            invocation -> {
              List<String> command = invocation.getArgument(0);
              commands.add(command);
              deployedYaml.add(Files.readString(Path.of(command.get(4)), StandardCharsets.UTF_8));
              return null;
            })
        .when(jobExecutor)
        .runCommand(anyList());

    operation = new DeployCloudrunAtomicOperation(description);
    operation.jobExecutor = jobExecutor;
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

    assertThat(commands.get(0)).contains("--region=us-central1");
    assertThat(path(singleDeployedManifest(), "metadata", "annotations", "spinnaker/application"))
        .isEqualTo("myapp");
  }

  @Test
  void rejectsMoreThanOneConfigFileInsteadOfBuildingAnInvalidGcloudCommand() {
    description.setConfigFiles(List.of(CONFIG_FILE, CONFIG_FILE));

    assertThatThrownBy(() -> operation.operate(new ArrayList<>()))
        .isInstanceOf(CloudrunOperationException.class)
        .hasMessageContaining("exactly one");
    assertThat(commands).isEmpty();
  }

  private Map<String, Object> singleDeployedManifest() throws IOException {
    assertThat(deployedYaml).hasSize(1);
    return YAML.readValue(deployedYaml.get(0), Map.class);
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
