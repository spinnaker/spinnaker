/*
 * Copyright 2026 Harness, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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

package com.netflix.spinnaker.rosco.manifests.kustomize;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import com.netflix.spinnaker.rosco.manifests.kustomize.mapping.Kustomization;
import com.netflix.spinnaker.rosco.services.ClouddriverService;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;
import okhttp3.ResponseBody;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.ConstructorException;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import retrofit2.Call;
import retrofit2.Response;

/**
 * Proof tests that the safe YAML constructor used by {@link KustomizationFileReader} blocks known
 * CVE-2022-1471 / CWE-502 attack vectors.
 *
 * <p>{@link KustomizationFileReader#convert} parses untrusted kustomization YAML with
 * SnakeYAML {@link SafeConstructor}, which only produces standard types (Map, List,
 * String, etc.). The resulting map is then mapped to {@link
 * com.netflix.spinnaker.rosco.manifests.kustomize.mapping.Kustomization} via Jackson's {@link
 * com.fasterxml.jackson.databind.ObjectMapper#convertValue}. This two-step process prevents
 * arbitrary object instantiation via YAML tags.
 */
class KustomizeSafeConstructorTest {

  /**
   * Verifies that a malicious {@code !!javax.script.ScriptEngineManager} tag injected into a
   * String-typed field is rejected.
   */
  @Test
  void safeConstructorPreventsScriptEngineManagerInstantiationInStringField() {
    Yaml yaml = new Yaml(new SafeConstructor(new LoaderOptions()));

    String maliciousYaml =
        "namePrefix: !!javax.script.ScriptEngineManager []\n"
            + "resources:\n"
            + "  - deployment.yml\n";

    ConstructorException ex =
        assertThrows(ConstructorException.class, () -> yaml.load(maliciousYaml));
    assertTrue(
        ex.getMessage().contains("could not determine a constructor for the tag"),
        "Expected SafeConstructor to reject ScriptEngineManager tag, but got: " + ex.getMessage());
  }

  /**
   * Verifies that a malicious {@code !!java.net.URL} tag cannot be used for SSRF.
   *
   * <p>With the vulnerable {@code new Constructor(Kustomization.class)} configuration, the URL
   * object could be instantiated and trigger outbound connections. SafeConstructor blocks this at
   * parse time.
   */
  @Test
  void safeConstructorPreventsURLInstantiation() throws Exception {
    AtomicBoolean connectionReceived = new AtomicBoolean(false);
    HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    int port = server.getAddress().getPort();
    server.createContext(
        "/ssrf-test",
        exchange -> {
          connectionReceived.set(true);
          exchange.sendResponseHeaders(200, 0);
          exchange.close();
        });
    server.start();

    try {
      Yaml yaml = new Yaml(new SafeConstructor(new LoaderOptions()));

      String maliciousYaml =
          "namePrefix: !!java.net.URL [\"http://127.0.0.1:"
              + port
              + "/ssrf-test\"]\n"
              + "resources:\n"
              + "  - deployment.yml\n";

      ConstructorException ex =
          assertThrows(ConstructorException.class, () -> yaml.load(maliciousYaml));
      assertTrue(
          ex.getMessage().contains("could not determine a constructor for the tag"),
          "Expected SafeConstructor to reject URL tag, but got: " + ex.getMessage());

      Thread.sleep(500);
      assertTrue(
          !connectionReceived.get(),
          "No connection should have been made because SafeConstructor rejected the URL tag before instantiation");
    } finally {
      server.stop(0);
    }
  }

  /**
   * Verifies that a malicious tag nested inside a map value (analogous to {@code
   * additionalProperties}) is rejected.
   */
  @Test
  void safeConstructorPreventsArbitraryInstantiationInMapFields() {
    Yaml yaml = new Yaml(new SafeConstructor(new LoaderOptions()));

    String maliciousYaml =
        "resources:\n"
            + "  - deployment.yml\n"
            + "additionalProperties:\n"
            + "  evil: !!javax.script.ScriptEngineManager []\n";

    ConstructorException ex =
        assertThrows(ConstructorException.class, () -> yaml.load(maliciousYaml));
    assertTrue(
        ex.getMessage().contains("could not determine a constructor for the tag"),
        "Expected SafeConstructor to reject ScriptEngineManager tag in nested map, but got: "
            + ex.getMessage());
  }

  /** Verifies that a root-level malicious tag override is rejected. */
  @Test
  void safeConstructorPreventsRootTagOverride() {
    Yaml yaml = new Yaml(new SafeConstructor(new LoaderOptions()));

    String maliciousYaml = "!!javax.script.ScriptEngineManager []";

    ConstructorException ex =
        assertThrows(ConstructorException.class, () -> yaml.load(maliciousYaml));
    assertTrue(
        ex.getMessage().contains("could not determine a constructor for the tag"),
        "Expected SafeConstructor to reject root tag override, but got: " + ex.getMessage());
  }

  /**
   * Verifies that {@link KustomizationFileReader} itself rejects malicious YAML. If the fix in
   * {@code KustomizationFileReader} is reverted to the vulnerable {@code
   * Constructor(Kustomization.class)} configuration, this test will fail because the malicious
   * object would be successfully deserialized and returned instead of throwing an exception.
   */
  @Test
  @SuppressWarnings("unchecked")
  void kustomizationFileReaderRejectsMaliciousYaml() throws Exception {
    String maliciousYaml =
        "resources:\n"
            + "  - deployment.yml\n"
            + "additionalProperties:\n"
            + "  evil: !!javax.script.ScriptEngineManager []\n";

    ClouddriverService clouddriverService = mock(ClouddriverService.class);
    Call<ResponseBody> call = mock(Call.class);

    ResponseBody responseBody =
        ResponseBody.create(null, maliciousYaml.getBytes(StandardCharsets.UTF_8));

    when(clouddriverService.fetchArtifact(any())).thenReturn(call);
    when(call.execute()).thenReturn(Response.success(200, responseBody));

    KustomizationFileReader reader = new KustomizationFileReader(clouddriverService);

    // SafeConstructor throws ConstructorException during convert(), which getKustomization()
    // catches and treats as "file not found". After exhausting all filenames it throws
    // IllegalArgumentException. With the vulnerable Constructor(Kustomization.class) the
    // malicious object would be successfully deserialized and returned.
    assertThrows(
        IllegalArgumentException.class,
        () ->
            reader.getKustomization(
                Artifact.builder()
                    .reference("http://example.com/base")
                    .artifactAccount("test")
                    .type("test")
                    .build(),
                "kustomization.yaml"));
  }

  /**
   * Regression test: a legitimate kustomization YAML must still be parsed correctly through the
   * SafeConstructor + Jackson convertValue path. Ensures the security fix did not break normal
   * functionality.
   */
  @Test
  @SuppressWarnings("unchecked")
  void kustomizationFileReaderParsesLegitimateYaml() throws Exception {
    String benignYaml =
        "namePrefix: prod-\n"
            + "resources:\n"
            + "  - deployment.yml\n"
            + "  - service.yml\n"
            + "patchesStrategicMerge:\n"
            + "  - patch.yml\n";

    ClouddriverService clouddriverService = mock(ClouddriverService.class);
    Call<ResponseBody> call = mock(Call.class);

    ResponseBody responseBody =
        ResponseBody.create(null, benignYaml.getBytes(StandardCharsets.UTF_8));

    when(clouddriverService.fetchArtifact(any())).thenReturn(call);
    when(call.execute()).thenReturn(Response.success(200, responseBody));

    KustomizationFileReader reader = new KustomizationFileReader(clouddriverService);

    Kustomization k =
        reader.getKustomization(
            Artifact.builder()
                .reference("http://example.com/base")
                .artifactAccount("test")
                .type("test")
                .build(),
            "kustomization.yaml");

    assertNotNull(k);
    assertEquals(2, k.getResources().size());
    assertTrue(k.getResources().contains("deployment.yml"));
    assertTrue(k.getResources().contains("service.yml"));
    assertEquals(1, k.getPatchesStrategicMerge().size());
    assertEquals("patch.yml", k.getPatchesStrategicMerge().get(0));
  }
}
