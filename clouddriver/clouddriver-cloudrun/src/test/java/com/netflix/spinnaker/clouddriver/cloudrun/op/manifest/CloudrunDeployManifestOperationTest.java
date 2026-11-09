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

package com.netflix.spinnaker.clouddriver.cloudrun.op.manifest;

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
import com.netflix.spinnaker.clouddriver.cloudrun.deploy.exception.CloudrunOperationException;
import com.netflix.spinnaker.clouddriver.cloudrun.description.manifest.CloudrunDeployManifestDescription;
import com.netflix.spinnaker.clouddriver.cloudrun.model.CloudrunService;
import com.netflix.spinnaker.clouddriver.cloudrun.provider.view.CloudrunLoadBalancerProvider;
import com.netflix.spinnaker.clouddriver.cloudrun.security.CloudrunCredentials;
import com.netflix.spinnaker.clouddriver.cloudrun.security.CloudrunNamedAccountCredentials;
import com.netflix.spinnaker.clouddriver.data.task.Task;
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Exercises {@link CloudrunDeployManifestOperation} end to end up to the {@code gcloud} call and
 * inspects the YAML file that would be handed to {@code gcloud run services replace}.
 */
class CloudrunDeployManifestOperationTest {

  private static final ObjectMapper YAML = new ObjectMapper(new YAMLFactory());

  /** Mirrors the manifest from spinnaker/spinnaker#8361. */
  private static final String MANIFEST_WITH_ENV_AND_PROBE =
      String.join(
          "\n",
          "apiVersion: serving.knative.dev/v1",
          "kind: Service",
          "metadata:",
          "  annotations:",
          "    run.googleapis.com/ingress: internal-and-cloud-load-balancing",
          "    run.googleapis.com/maxScale: \"1\"",
          "    run.googleapis.com/minScale: \"0\"",
          "  labels:",
          "    cloud.googleapis.com/location: europe-west2",
          "  name: exampleservice",
          "spec:",
          "  template:",
          "    metadata:",
          "      annotations:",
          "        autoscaling.knative.dev/maxScale: \"1\"",
          "        autoscaling.knative.dev/minScale: \"0\"",
          "        run.googleapis.com/startup-cpu-boost: \"true\"",
          "    spec:",
          "      containers:",
          "        - env:",
          "            - name: environment",
          "              value: test-utils",
          "          image: example-image-url",
          "          name: exampleservice",
          "          ports:",
          "            - containerPort: 80",
          "              name: http1",
          "          startupProbe:",
          "            tcpSocket:",
          "              port: 8182",
          "        - image: sidecar-image-url",
          "          name: sidecar",
          "      serviceAccountName: exampleservice@example-com.iam.gserviceaccount.com",
          "      timeoutSeconds: 30",
          "");

  private CloudrunDeployManifestDescription description;
  private CloudrunDeployManifestOperation operation;
  private CloudrunLoadBalancerProvider loadBalancerProvider;

  /** Manifests handed to the Cloud Run client. */
  private final List<Map<String, Object>> deployedManifests = new ArrayList<>();

  /** Regions of every deploy call. */
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

    description = new CloudrunDeployManifestDescription();
    description.setAccountName("cloudrunaccount");
    description.setAccount("cloudrunaccount");
    description.setApplication("myapp");
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

    loadBalancerProvider = mock(CloudrunLoadBalancerProvider.class);
    operation = new CloudrunDeployManifestOperation(description);
    operation.serviceClient = serviceClient;
    operation.provider = loadBalancerProvider;
  }

  // --- behavior that must keep working -------------------------------------------------------

  @Test
  void injectsServiceNameRevisionNameAndApplicationAnnotation() throws IOException {
    description.setManifests(List.of(manifest(MANIFEST_WITH_ENV_AND_PROBE)));

    operation.operate(new ArrayList<>());

    Map<String, Object> deployed = singleDeployedManifest();
    assertThat(path(deployed, "metadata", "name")).isEqualTo("myapp");
    assertThat((String) path(deployed, "spec", "template", "metadata", "name"))
        .startsWith("myapp-v");
    assertThat(path(deployed, "metadata", "annotations", "spinnaker/application"))
        .isEqualTo("myapp");
  }

  @Test
  void deploysToTheRegionNamedInTheManifestLabel() throws IOException {
    description.setManifests(List.of(manifest(MANIFEST_WITH_ENV_AND_PROBE)));

    operation.operate(new ArrayList<>());

    assertThat(regions).containsExactly("europe-west2");
  }

  // --- #8361: fields the typed model does not know about are silently dropped -----------------

  @Test
  void preservesContainerEnvironmentVariables() throws IOException {
    description.setManifests(List.of(manifest(MANIFEST_WITH_ENV_AND_PROBE)));

    operation.operate(new ArrayList<>());

    assertThat(firstContainer(singleDeployedManifest()))
        .containsEntry("env", List.of(Map.of("name", "environment", "value", "test-utils")));
  }

  @Test
  void preservesStartupProbe() throws IOException {
    description.setManifests(List.of(manifest(MANIFEST_WITH_ENV_AND_PROBE)));

    operation.operate(new ArrayList<>());

    assertThat(firstContainer(singleDeployedManifest()))
        .containsEntry("startupProbe", Map.of("tcpSocket", Map.of("port", 8182)));
  }

  @Test
  void preservesAdditionalContainers() throws IOException {
    description.setManifests(List.of(manifest(MANIFEST_WITH_ENV_AND_PROBE)));

    operation.operate(new ArrayList<>());

    List<Map<String, Object>> containers = containers(singleDeployedManifest());
    assertThat(containers).hasSize(2);
    assertThat(containers.get(1)).containsEntry("name", "sidecar");
  }

  @Test
  void preservesServiceAnnotationsWithoutADedicatedField() throws IOException {
    description.setManifests(List.of(manifest(MANIFEST_WITH_ENV_AND_PROBE)));

    operation.operate(new ArrayList<>());

    Map<String, Object> deployed = singleDeployedManifest();
    assertThat((Map<String, Object>) path(deployed, "metadata", "annotations"))
        .containsEntry("run.googleapis.com/maxScale", "1")
        .containsEntry("run.googleapis.com/minScale", "0");
  }

  @Test
  void preservesRevisionTemplateAnnotationsWithoutADedicatedField() throws IOException {
    description.setManifests(List.of(manifest(MANIFEST_WITH_ENV_AND_PROBE)));

    operation.operate(new ArrayList<>());

    Map<String, Object> deployed = singleDeployedManifest();
    assertThat((Map<String, Object>) path(deployed, "spec", "template", "metadata", "annotations"))
        .containsEntry("autoscaling.knative.dev/maxScale", "1")
        .containsEntry("autoscaling.knative.dev/minScale", "0")
        .containsEntry("run.googleapis.com/startup-cpu-boost", "true");
  }

  // --- related defects in the same code path --------------------------------------------------

  @Test
  void deploysWhenTheManifestHasNoLabelsOrAnnotations() throws IOException {
    description.setRegion("us-central1");
    description.setManifests(
        List.of(
            manifest(
                String.join(
                    "\n",
                    "apiVersion: serving.knative.dev/v1",
                    "kind: Service",
                    "metadata:",
                    "  name: exampleservice",
                    "spec:",
                    "  template:",
                    "    metadata:",
                    "      name: ignored",
                    "    spec:",
                    "      containers:",
                    "        - image: example-image-url",
                    ""))));

    operation.operate(new ArrayList<>());

    assertThat(regions).containsExactly("us-central1");
    assertThat(path(singleDeployedManifest(), "metadata", "annotations", "spinnaker/application"))
        .isEqualTo("myapp");
  }

  @Test
  void bindsDockerArtifactWithoutRequiringTheUserImageAnnotation() throws IOException {
    description.setRequiredArtifacts(
        List.of(
            Artifact.builder()
                .type("docker/image")
                .name("gcr.io/project/example-image")
                .reference("gcr.io/project/example-image:v2")
                .build()));
    description.setManifests(
        List.of(
            manifest(MANIFEST_WITH_ENV_AND_PROBE.replace("example-image-url", "example-image"))));

    operation.operate(new ArrayList<>());

    assertThat(firstContainer(singleDeployedManifest()))
        .containsEntry("image", "gcr.io/project/example-image:v2");
  }

  @Test
  void failsWhenARequiredArtifactMatchesNoContainer() throws IOException {
    description.setRequiredArtifacts(
        List.of(
            Artifact.builder()
                .type("docker/image")
                .name("gcr.io/project/other-image")
                .reference("gcr.io/project/other-image:v2")
                .build()));
    description.setManifests(List.of(manifest(MANIFEST_WITH_ENV_AND_PROBE)));

    assertThatThrownBy(() -> operation.operate(new ArrayList<>()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("could not be bound");
    assertThat(deployedManifests).isEmpty();
  }

  @Test
  void bindsArtifactWhenTheManifestImageHasARegistryPortAndTag() throws IOException {
    description.setRequiredArtifacts(
        List.of(
            Artifact.builder()
                .type("docker/image")
                .name("registry.example.com:5000/team/example-image")
                .reference("registry.example.com:5000/team/example-image:v2")
                .build()));
    description.setManifests(
        List.of(
            manifest(
                MANIFEST_WITH_ENV_AND_PROBE.replace(
                    "example-image-url", "registry.example.com:5000/team/example-image:v1"))));

    operation.operate(new ArrayList<>());

    assertThat(firstContainer(singleDeployedManifest()))
        .containsEntry("image", "registry.example.com:5000/team/example-image:v2");
  }

  @Test
  void rejectsMoreThanOneManifestInsteadOfBuildingAnInvalidGcloudCommand() throws IOException {
    description.setManifests(
        List.of(manifest(MANIFEST_WITH_ENV_AND_PROBE), manifest(MANIFEST_WITH_ENV_AND_PROBE)));

    assertThatThrownBy(() -> operation.operate(new ArrayList<>()))
        .isInstanceOf(CloudrunOperationException.class)
        .hasMessageContaining("exactly one");
    assertThat(deployedManifests).isEmpty();
  }

  // --- helpers --------------------------------------------------------------------------------

  private static CloudrunService manifest(String yaml) throws IOException {
    Map<String, Object> parsed = YAML.readValue(yaml, Map.class);
    return new CloudrunService()
        .setApiVersion((String) parsed.get("apiVersion"))
        .setKind((String) parsed.get("kind"))
        .setMetadata(new LinkedHashMap<>((Map<String, Object>) parsed.get("metadata")))
        .setSpec(new LinkedHashMap<>((Map<String, Object>) parsed.get("spec")));
  }

  private Map<String, Object> singleDeployedManifest() throws IOException {
    assertThat(deployedManifests).hasSize(1);
    return deployedManifests.get(0);
  }

  private static List<Map<String, Object>> containers(Map<String, Object> manifest) {
    return (List<Map<String, Object>>) path(manifest, "spec", "template", "spec", "containers");
  }

  private static Map<String, Object> firstContainer(Map<String, Object> manifest) {
    return containers(manifest).get(0);
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
