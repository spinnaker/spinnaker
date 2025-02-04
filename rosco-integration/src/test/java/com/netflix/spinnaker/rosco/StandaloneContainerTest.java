/*
 * Copyright 2024 Salesforce, Inc.
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
 */
package com.netflix.spinnaker.rosco;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.exactly;
import static com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import com.netflix.spinnaker.rosco.manifests.BakeManifestRequest;
import com.netflix.spinnaker.rosco.manifests.helm.HelmBakeManifestRequest;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.Getter;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
class StandaloneContainerTest {

  private static final String REDIS_NETWORK_ALIAS = "redisHost";

  private static final int REDIS_PORT = 6379;

  private static final Logger logger = LoggerFactory.getLogger(StandaloneContainerTest.class);

  private static final Network network = Network.newNetwork();

  @RegisterExtension
  static final WireMockExtension wmClouddriver =
      WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

  static int clouddriverPort;

  private static final ObjectMapper mapper = new ObjectMapper();

  private static GenericContainer redis =
      new GenericContainer(DockerImageName.parse("library/redis:5-alpine"))
          .withNetwork(network)
          .withNetworkAliases(REDIS_NETWORK_ALIAS)
          .withExposedPorts(REDIS_PORT);

  private static GenericContainer roscoContainer;

  private static GenericContainer roscoContainerUsingValuesFileForOverrides;

  @BeforeAll
  static void setupOnce() throws Exception {
    clouddriverPort = wmClouddriver.getRuntimeInfo().getHttpPort();
    logger.info("wiremock clouddriver http port: {} ", clouddriverPort);

    String fullDockerImageName = System.getenv("FULL_DOCKER_IMAGE_NAME");

    // Skip the tests if there's no docker image.  This allows gradlew build to work.
    assumeTrue(fullDockerImageName != null);

    // expose clouddriver to rosco
    org.testcontainers.Testcontainers.exposeHostPorts(clouddriverPort);

    redis.start();

    DockerImageName dockerImageName = DockerImageName.parse(fullDockerImageName);

    roscoContainer =
        new GenericContainer(dockerImageName)
            .withNetwork(network)
            .withExposedPorts(8087)
            .dependsOn(redis)
            .waitingFor(Wait.forHealthcheck())
            .withEnv(
                "SPRING_APPLICATION_JSON",
                getSpringApplicationJson(
                    0)); /* 0 means always use --set/--set-string for overrides */

    Slf4jLogConsumer logConsumer = new Slf4jLogConsumer(logger);
    roscoContainer.start();
    roscoContainer.followOutput(logConsumer);

    roscoContainerUsingValuesFileForOverrides =
        new GenericContainer(dockerImageName)
            .withNetwork(network)
            .withExposedPorts(8087)
            .dependsOn(redis)
            .waitingFor(Wait.forHealthcheck())
            .withEnv(
                "SPRING_APPLICATION_JSON",
                getSpringApplicationJson(1)); /* 1 means always use --values files for overrides */

    roscoContainerUsingValuesFileForOverrides.start();
    roscoContainerUsingValuesFileForOverrides.followOutput(logConsumer);
  }

  private static String getSpringApplicationJson(int overridesFileThreshold)
      throws JsonProcessingException {
    String redisUrl = "redis://" + REDIS_NETWORK_ALIAS + ":" + REDIS_PORT;
    logger.info("redisUrl: '{}'", redisUrl);
    Map<String, String> properties =
        Map.of(
            "redis.connection",
            redisUrl,
            "services.clouddriver.baseUrl",
            "http://" + GenericContainer.INTERNAL_HOST_HOSTNAME + ":" + clouddriverPort,
            "helm.overridesFileThreshold",
            String.valueOf(overridesFileThreshold));
    return mapper.writeValueAsString(properties);
  }

  @AfterAll
  static void cleanupOnce() {
    if (roscoContainer != null) {
      roscoContainer.stop();
    }

    if (redis != null) {
      redis.stop();
    }
  }

  @BeforeEach
  void init(TestInfo testInfo) {
    System.out.println("--------------- Test " + testInfo.getDisplayName());
  }

  @Test
  void testHealthCheck() throws Exception {
    // hit an arbitrary endpoint
    HttpRequest request =
        HttpRequest.newBuilder()
            .uri(
                new URI(
                    "http://"
                        + roscoContainer.getHost()
                        + ":"
                        + roscoContainer.getFirstMappedPort()
                        + "/health"))
            .GET()
            .build();

    HttpClient client = HttpClient.newHttpClient();

    HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
    assertThat(response).isNotNull();
    logger.info("response: {}, {}", response.statusCode(), response.body());
    assertThat(response.statusCode()).isEqualTo(200);
  }

  public static final String SIMPLE_TEMPLATE_VARIABLE_KEY = "foo";
  public static final String NESTED_TEMPLATE_VARIABLE_KEY = "foo1.test";
  public static final String INDEXED_TEMPLATE_VARIABLE_KEY = "foo2[0]";
  public static final String DOTTED_TEMPLATE_VARIABLE_KEY_NON_NESTED = "foo3\\.foo4";
  public static final String ARRAY_TEMPLATE_VARIABLE = "foo5";

  /**
   * Enumerates predefined sets of Helm chart values used in testing to verify the precedence and
   * application of various value sources during the Helm chart rendering process. Each enum value
   * represents a different scenario, including default values, overrides, and external value files.
   */
  @Getter
  public enum HelmTemplateValues {
    /**
     * Represents default values defined within a Helm chart's values.yaml file. These are the
     * fallback values used when no overrides are provided.
     */
    DEFAULT(
        Map.of(
            SIMPLE_TEMPLATE_VARIABLE_KEY,
            "bar_default",
            NESTED_TEMPLATE_VARIABLE_KEY,
            "bar1_default",
            INDEXED_TEMPLATE_VARIABLE_KEY,
            "bar2_default",
            DOTTED_TEMPLATE_VARIABLE_KEY_NON_NESTED,
            "bar3_default",
            ARRAY_TEMPLATE_VARIABLE,
            "bar5_default")),
    /**
     * Represents user-provided overrides that can be passed to Helm via the '--values' flag or the
     * '--set' command. These values are meant to override the default values specified in the
     * chart's values.yaml.
     */
    OVERRIDES(
        Map.of(
            SIMPLE_TEMPLATE_VARIABLE_KEY,
            1000000,
            NESTED_TEMPLATE_VARIABLE_KEY,
            999999,
            INDEXED_TEMPLATE_VARIABLE_KEY,
            "bar2_overrides",
            DOTTED_TEMPLATE_VARIABLE_KEY_NON_NESTED,
            "bar3_overrides",
            ARRAY_TEMPLATE_VARIABLE,
            "{bar5_overrides}")),

    OVERRIDES_STRING_LARGE_NUMBER(
        Map.of(
            SIMPLE_TEMPLATE_VARIABLE_KEY,
            "1000000",
            NESTED_TEMPLATE_VARIABLE_KEY,
            999999,
            INDEXED_TEMPLATE_VARIABLE_KEY,
            "bar2_overrides",
            DOTTED_TEMPLATE_VARIABLE_KEY_NON_NESTED,
            "bar3_overrides",
            ARRAY_TEMPLATE_VARIABLE,
            "{bar5_overrides}")),

    OVERRIDES_NO_LARGE_NUMBERS(
        Map.of(
            SIMPLE_TEMPLATE_VARIABLE_KEY,
            999999,
            NESTED_TEMPLATE_VARIABLE_KEY,
            999999,
            INDEXED_TEMPLATE_VARIABLE_KEY,
            "bar2_overrides",
            DOTTED_TEMPLATE_VARIABLE_KEY_NON_NESTED,
            "bar3_overrides",
            ARRAY_TEMPLATE_VARIABLE,
            "{bar5_overrides}")),
    OVERRIDES_NEGATIVE_NUMBERS(
        Map.of(
            SIMPLE_TEMPLATE_VARIABLE_KEY,
            -1000000,
            NESTED_TEMPLATE_VARIABLE_KEY,
            -999999,
            INDEXED_TEMPLATE_VARIABLE_KEY,
            "bar2_overrides",
            DOTTED_TEMPLATE_VARIABLE_KEY_NON_NESTED,
            "bar3_overrides",
            ARRAY_TEMPLATE_VARIABLE,
            "{bar5_overrides}")),

    /** Represents expected helm template output with scientific notion. */
    TEMPLATE_OUTPUT_WITH_SCIENTIFIC_NOTION(
        Map.of(
            SIMPLE_TEMPLATE_VARIABLE_KEY,
            // In helm2, any numeric value greater than or equal to 1,000,000 will be treated as a
            // float
            // for both --set and --values. Consequently, the Helm template output value
            // will be in scientific notation.
            "1e+06",
            NESTED_TEMPLATE_VARIABLE_KEY,
            999999,
            INDEXED_TEMPLATE_VARIABLE_KEY,
            "bar2_overrides",
            DOTTED_TEMPLATE_VARIABLE_KEY_NON_NESTED,
            "bar3_overrides",
            ARRAY_TEMPLATE_VARIABLE,
            "{bar5_overrides}")),

    TEMPLATE_OUTPUT_WITH_NEGATIVE_SCIENTIFIC_NOTION(
        Map.of(
            SIMPLE_TEMPLATE_VARIABLE_KEY,
            // In helm2, any numeric value greater than or equal to 1,000,000 will be treated as a
            // float
            // for both --set and --values. Consequently, the Helm template output value
            // will be in scientific notation.
            "-1e+06",
            NESTED_TEMPLATE_VARIABLE_KEY,
            -999999,
            INDEXED_TEMPLATE_VARIABLE_KEY,
            "bar2_overrides",
            DOTTED_TEMPLATE_VARIABLE_KEY_NON_NESTED,
            "bar3_overrides",
            ARRAY_TEMPLATE_VARIABLE,
            "{bar5_overrides}")),

    /**
     * Represents values from an external source, such as a separate values file not included within
     * the Helm chart itself. These values are meant to simulate the scenario where values are
     * provided from an external file during the Helm chart rendering process.
     */
    EXTERNAL(
        Map.of(
            SIMPLE_TEMPLATE_VARIABLE_KEY,
            "bar_external",
            NESTED_TEMPLATE_VARIABLE_KEY,
            "bar1_external",
            INDEXED_TEMPLATE_VARIABLE_KEY,
            "bar2_external",
            DOTTED_TEMPLATE_VARIABLE_KEY_NON_NESTED,
            "bar3_external",
            ARRAY_TEMPLATE_VARIABLE,
            "bar5_external")),
    /**
     * Represents an empty map of values, used to test the scenario where no overrides are provided,
     * and the default values within the chart's values.yaml should prevail.
     */
    EMPTY(Collections.emptyMap());

    private final Map<String, Object> values;

    HelmTemplateValues(Map<String, Object> values) {
      this.values = values;
    }
  }

  /**
   * test data provider for helm template precedence test value of template variable foo is as below
   * - default value in chart's value.yaml: bar_default - value in overrides: bar_overrides - value
   * in external value.yaml: bar_external
   *
   * @return test data for helm template precedence test
   */
  private static Stream<Arguments> helmOverridesPriorityTestData() {
    /*

    */
    return Stream.of(
        // default values.yml + overrides through --values + no external values yaml -> values of
        // helm variables is from overrides
        Arguments.of(
            HelmTemplateValues.OVERRIDES,
            true,
            BakeManifestRequest.TemplateRenderer.HELM3,
            HelmTemplateValues.OVERRIDES,
            false,
            false),
        Arguments.of(
            HelmTemplateValues.OVERRIDES_NO_LARGE_NUMBERS,
            true,
            BakeManifestRequest.TemplateRenderer.HELM3,
            HelmTemplateValues.OVERRIDES_NO_LARGE_NUMBERS,
            false,
            false),

        // default values.yml + overrides through --values + no external values yaml -> values of
        // helm variables is from overrides
        Arguments.of(
            HelmTemplateValues.OVERRIDES,
            true,
            BakeManifestRequest.TemplateRenderer.HELM2,
            HelmTemplateValues.OVERRIDES,
            false,
            false),
        // default values.yml + overrides through --set-string + no external values yaml -> values
        // of helm variables is from overrides
        Arguments.of(
            HelmTemplateValues.OVERRIDES,
            false,
            BakeManifestRequest.TemplateRenderer.HELM3,
            HelmTemplateValues.OVERRIDES,
            false,
            false),
        Arguments.of(
            HelmTemplateValues.OVERRIDES,
            false,
            BakeManifestRequest.TemplateRenderer.HELM2,
            HelmTemplateValues.OVERRIDES,
            false,
            false),
        // default values.yml + empty overrides + no external values yaml -> values of helm
        // variables is from default
        Arguments.of(
            HelmTemplateValues.EMPTY,
            false,
            BakeManifestRequest.TemplateRenderer.HELM3,
            HelmTemplateValues.DEFAULT,
            false,
            false),
        Arguments.of(
            HelmTemplateValues.EMPTY,
            false,
            BakeManifestRequest.TemplateRenderer.HELM2,
            HelmTemplateValues.DEFAULT,
            false,
            false),
        // default values.yml + overrides through --values + external values yaml -> values of helm
        // variables is from overrides
        Arguments.of(
            HelmTemplateValues.OVERRIDES,
            true,
            BakeManifestRequest.TemplateRenderer.HELM3,
            HelmTemplateValues.OVERRIDES,
            true,
            false),
        Arguments.of(
            HelmTemplateValues.OVERRIDES,
            true,
            BakeManifestRequest.TemplateRenderer.HELM2,
            HelmTemplateValues.OVERRIDES,
            true,
            false),
        // default values.yml + overrides through --set-string + external values yaml -> values of
        // helm variables is from overrides
        Arguments.of(
            HelmTemplateValues.OVERRIDES,
            false,
            BakeManifestRequest.TemplateRenderer.HELM3,
            HelmTemplateValues.OVERRIDES,
            true,
            false),
        Arguments.of(
            HelmTemplateValues.OVERRIDES,
            false,
            BakeManifestRequest.TemplateRenderer.HELM2,
            HelmTemplateValues.OVERRIDES,
            true,
            false),
        // default values.yml + empty overrides + external values yaml -> values of helm variables
        // is from external values yaml
        Arguments.of(
            HelmTemplateValues.EMPTY,
            false,
            BakeManifestRequest.TemplateRenderer.HELM3,
            HelmTemplateValues.EXTERNAL,
            true,
            false),
        Arguments.of(
            HelmTemplateValues.EMPTY,
            true,
            BakeManifestRequest.TemplateRenderer.HELM2,
            HelmTemplateValues.EXTERNAL,
            true,
            false),

        // default values.yml + overrides through --values + no external values yaml -> values of
        // helm variables is from overrides
        Arguments.of(
            HelmTemplateValues.OVERRIDES,
            true,
            BakeManifestRequest.TemplateRenderer.HELM3,
            HelmTemplateValues.OVERRIDES,
            false,
            true),
        Arguments.of(
            HelmTemplateValues.OVERRIDES_STRING_LARGE_NUMBER,
            true,
            BakeManifestRequest.TemplateRenderer.HELM3,
            HelmTemplateValues.OVERRIDES_STRING_LARGE_NUMBER,
            false,
            true),
        Arguments.of(
            HelmTemplateValues.OVERRIDES_NEGATIVE_NUMBERS,
            true,
            BakeManifestRequest.TemplateRenderer.HELM3,
            HelmTemplateValues.OVERRIDES_NEGATIVE_NUMBERS,
            false,
            true),
        Arguments.of(
            HelmTemplateValues.OVERRIDES_NEGATIVE_NUMBERS,
            true,
            BakeManifestRequest.TemplateRenderer.HELM2,
            HelmTemplateValues.TEMPLATE_OUTPUT_WITH_NEGATIVE_SCIENTIFIC_NOTION,
            false,
            true),
        // default values.yml + overrides through --values + no external values yaml -> values of
        // helm variables is from overrides
        Arguments.of(
            HelmTemplateValues.OVERRIDES,
            true,
            BakeManifestRequest.TemplateRenderer.HELM2,
            HelmTemplateValues.TEMPLATE_OUTPUT_WITH_SCIENTIFIC_NOTION,
            false,
            true),
        Arguments.of(
            HelmTemplateValues.OVERRIDES_STRING_LARGE_NUMBER,
            true,
            BakeManifestRequest.TemplateRenderer.HELM2,
            HelmTemplateValues.TEMPLATE_OUTPUT_WITH_SCIENTIFIC_NOTION,
            false,
            true),
        // default values.yml + overrides through --set-string + no external values yaml -> values
        // of helm variables is from overrides
        Arguments.of(
            HelmTemplateValues.OVERRIDES,
            false,
            BakeManifestRequest.TemplateRenderer.HELM3,
            HelmTemplateValues.OVERRIDES,
            false,
            true),
        Arguments.of(
            HelmTemplateValues.OVERRIDES,
            false,
            BakeManifestRequest.TemplateRenderer.HELM2,
            HelmTemplateValues.TEMPLATE_OUTPUT_WITH_SCIENTIFIC_NOTION,
            false,
            true),
        // default values.yml + empty overrides + no external values yaml -> values of helm
        // variables is from default
        Arguments.of(
            HelmTemplateValues.EMPTY,
            false,
            BakeManifestRequest.TemplateRenderer.HELM3,
            HelmTemplateValues.DEFAULT,
            false,
            true),
        Arguments.of(
            HelmTemplateValues.EMPTY,
            false,
            BakeManifestRequest.TemplateRenderer.HELM2,
            HelmTemplateValues.DEFAULT,
            false,
            true),
        // default values.yml + overrides through --values +  external values yaml -> values of helm
        // variables is from overrides
        Arguments.of(
            HelmTemplateValues.OVERRIDES,
            true,
            BakeManifestRequest.TemplateRenderer.HELM3,
            HelmTemplateValues.OVERRIDES,
            true,
            true),
        Arguments.of(
            HelmTemplateValues.OVERRIDES,
            true,
            BakeManifestRequest.TemplateRenderer.HELM2,
            HelmTemplateValues.TEMPLATE_OUTPUT_WITH_SCIENTIFIC_NOTION,
            true,
            true),
        // default values.yml + overrides through --set-string +  external values yaml -> values of
        // helm variables is from overrides
        Arguments.of(
            HelmTemplateValues.OVERRIDES,
            false,
            BakeManifestRequest.TemplateRenderer.HELM3,
            HelmTemplateValues.OVERRIDES,
            true,
            true),
        Arguments.of(
            HelmTemplateValues.OVERRIDES,
            false,
            BakeManifestRequest.TemplateRenderer.HELM2,
            HelmTemplateValues.TEMPLATE_OUTPUT_WITH_SCIENTIFIC_NOTION,
            true,
            true),
        // default values.yml + empty overrides + external values yaml -> values of helm variables
        // is from external values yaml
        Arguments.of(
            HelmTemplateValues.EMPTY,
            false,
            BakeManifestRequest.TemplateRenderer.HELM3,
            HelmTemplateValues.EXTERNAL,
            true,
            true),
        Arguments.of(
            HelmTemplateValues.EMPTY,
            false,
            BakeManifestRequest.TemplateRenderer.HELM2,
            HelmTemplateValues.EXTERNAL,
            true,
            true));
  }

  /**
   * Test the priority of Helm overrides based on different input scenarios, using
   * HelmTemplateValues to define both input and expectedTemplateValues configurations. This method
   * evaluates how different types of Helm value configurations (default, overrides, and external)
   * are applied and prioritized during the Helm chart rendering process.
   *
   * @param inputOverrides The HelmTemplateValues enum representing the set of values to be used as
   *     input for the Helm chart baking process. This includes any overrides or default values that
   *     should be applied to the template rendering.
   * @param useValuesFileForOverrides true to test with a rosco container configured to use a values
   *     file for overrides. false to test with a rosco container configured to use
   *     --set/--set-string.
   * @param helmVersion The version of Helm being tested (e.g. "TemplateRenderer.HELM2",
   *     TemplateRenderer.HELM3), which may affect the rendering behavior and the handling of values
   *     and overrides.
   * @param expectedTemplateValues The HelmTemplateValues enum representing the
   *     expectedTemplateValues set of values after the Helm chart rendering process. This is used
   *     to verify that the correct values are applied based on the input configuration and Helm
   *     version.
   * @param addExternalValuesFile A boolean flag indicating whether an external values YAML file
   *     should be included in the helm template command. This allows testing the effect of external
   *     value files on the rendering outcome.
   * @throws Exception if any error occurs during file handling, processing the Helm template, or if
   *     assertions fail due to unexpected rendering results.
   */
  @ParameterizedTest(name = "{displayName} - [{index}] {arguments}")
  @MethodSource("helmOverridesPriorityTestData")
  void testHelmOverridesPriority(
      HelmTemplateValues inputOverrides,
      boolean useValuesFileForOverrides,
      BakeManifestRequest.TemplateRenderer helmVersion,
      HelmTemplateValues expectedTemplateValues,
      boolean addExternalValuesFile,
      boolean rawOverrides)
      throws Exception {

    HelmBakeManifestRequest bakeManifestRequest = new HelmBakeManifestRequest();
    bakeManifestRequest.setOutputName("test");
    bakeManifestRequest.setOutputArtifactName("test_artifact");

    // The artifact is arbitrary.  This test is about verifying behavior of
    // overrides regardless of the artifact.
    Artifact artifact = Artifact.builder().type("git/repo").build();

    // Use a mutable list since we conditionally add an external values file below.
    List<Artifact> inputArtifacts = new ArrayList<>();
    inputArtifacts.add(artifact);
    bakeManifestRequest.setInputArtifacts(inputArtifacts);

    bakeManifestRequest.setOverrides(inputOverrides.values);
    bakeManifestRequest.setRawOverrides(rawOverrides);
    bakeManifestRequest.setTemplateRenderer(helmVersion);

    // set up the first clouddriver response for /artifacts/fetch to return a helm chart.
    Path tempDir = Files.createTempDirectory("tempDir");
    addTestHelmChartToPath(tempDir);

    String scenarioName = "artifacts";
    String scenarioTwo = "externalValuesFile";
    wmClouddriver.stubFor(
        WireMock.put(urlPathEqualTo("/artifacts/fetch/"))
            .inScenario(scenarioName)
            .whenScenarioStateIs(Scenario.STARTED)
            .willReturn(aResponse().withStatus(200).withBody(createGzippedTarballFromPath(tempDir)))
            .willSetStateTo(scenarioTwo));

    if (addExternalValuesFile) {
      Path externalValuesPath = getFilePathFromClassPath("values_external.yaml");
      // This doesn't have to be an artifact that actually refers to this file
      // on the file system...especially since it couldn't refer to a file on
      // clouddriver's file system.  An arbitrary artifact is sufficient.
      Artifact valuesArtifact = Artifact.builder().build();
      inputArtifacts.add(valuesArtifact);

      wmClouddriver.stubFor(
          WireMock.put(urlPathEqualTo("/artifacts/fetch/"))
              .inScenario(scenarioName)
              .whenScenarioStateIs(scenarioTwo)
              .willReturn(
                  aResponse().withStatus(200).withBody(Files.readAllBytes(externalValuesPath))));
    }

    GenericContainer containerToUse =
        useValuesFileForOverrides ? roscoContainerUsingValuesFileForOverrides : roscoContainer;

    HttpRequest request =
        HttpRequest.newBuilder()
            .uri(
                new URI(
                    "http://"
                        + containerToUse.getHost()
                        + ":"
                        + containerToUse.getFirstMappedPort()
                        + "/api/v2/manifest/bake/"
                        + helmVersion))
            .header("Content-Type", "application/json")
            .header(
                "X-SPINNAKER-USER",
                "test-user") // to silence a warning when X-SPINNAKER-USER is missing
            .header(
                "X-SPINNAKER-ACCOUNTS",
                "test-account") // to silence a warning when X-SPINNAKER-ACCOUNTS is missing
            .POST(
                HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(bakeManifestRequest)))
            .build();

    HttpClient client = HttpClient.newHttpClient();

    HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
    assertThat(response).isNotNull();
    logger.info("response: {}, {}", response.statusCode(), response.body());
    assertThat(response.statusCode()).isEqualTo(200);

    // Verify that clouddriver was called the expected number of times
    wmClouddriver.verify(
        exactly(1 + (addExternalValuesFile ? 1 : 0)),
        putRequestedFor(urlPathEqualTo("/artifacts/fetch/")));

    // Verify that the response from rosco is as expected
    TypeReference<Map<String, Object>> artifactType = new TypeReference<>() {};
    Map<String, Object> map = mapper.readValue(response.body(), artifactType);
    assertThat(new String(Base64.getDecoder().decode((String) map.get("reference"))))
        .isEqualTo(
            String.format(
                readFileFromClasspath("expected_template.yaml") + "\n",
                expectedTemplateValues.getValues().get(SIMPLE_TEMPLATE_VARIABLE_KEY),
                expectedTemplateValues.getValues().get(NESTED_TEMPLATE_VARIABLE_KEY),
                expectedTemplateValues.getValues().get(INDEXED_TEMPLATE_VARIABLE_KEY),
                expectedTemplateValues.getValues().get(DOTTED_TEMPLATE_VARIABLE_KEY_NON_NESTED),
                expectedTemplateValues
                            .getValues()
                            .get(ARRAY_TEMPLATE_VARIABLE)
                            .toString()
                            .startsWith("{")
                        && expectedTemplateValues
                            .getValues()
                            .get(ARRAY_TEMPLATE_VARIABLE)
                            .toString()
                            .endsWith("}")
                    ? "bar5_overrides"
                    : expectedTemplateValues.getValues().get(ARRAY_TEMPLATE_VARIABLE).toString()));
  }

  /** Translate a classpath resource to a file path */
  private Path getFilePathFromClassPath(String fileName) throws Exception {
    return Paths.get(
        Objects.requireNonNull(getClass().getClassLoader().getResource(fileName)).toURI());
  }

  /**
   * Make a gzipped tarball of all files in a path
   *
   * @param rootPath the root path of the tarball
   * @return a byte array containing the gzipped tarball
   */
  private byte[] createGzippedTarballFromPath(Path rootPath) throws IOException {
    ArrayList<Path> filePathsToAdd =
        Files.walk(rootPath, FileVisitOption.FOLLOW_LINKS)
            .filter(path -> !path.equals(rootPath))
            .collect(Collectors.toCollection(ArrayList::new));
    // See https://commons.apache.org/proper/commons-compress/examples.html#Common_Archival_Logic
    // for background
    try (ByteArrayOutputStream os = new ByteArrayOutputStream();
        GzipCompressorOutputStream gzo = new GzipCompressorOutputStream(os);
        TarArchiveOutputStream tarArchive = new TarArchiveOutputStream(gzo)) {
      for (Path path : filePathsToAdd) {
        TarArchiveEntry tarEntry =
            new TarArchiveEntry(path.toFile(), rootPath.relativize(path).toString());
        tarArchive.putArchiveEntry(tarEntry);
        if (path.toFile().isFile()) {
          try (InputStream fileInputStream = Files.newInputStream(path)) {
            IOUtils.copy(fileInputStream, tarArchive);
          }
        }
        tarArchive.closeArchiveEntry();
      }
      tarArchive.finish();
      gzo.finish();
      return os.toByteArray();
    }
  }

  /**
   * Adds a test Helm chart to a specified path. This method creates necessary Helm chart files such
   * as Chart.yaml, values.yaml, and a sample template. The values.yaml file sets default values for
   * the chart, in this case, it sets the value of 'foo' to 'bar'. The templates/foo.yaml file is a
   * sample Helm template that uses the 'foo' value.
   *
   * @param path The root directory path where the Helm chart files will be created.
   * @throws IOException If there is an issue creating the files i/o in the specified path.
   */
  static void addTestHelmChartToPath(Path path) throws IOException {

    addFile(path, "Chart.yaml", readFileFromClasspath("Chart.yaml"));
    addFile(path, "values.yaml", readFileFromClasspath("values.yaml"));
    addFile(path, "templates/foo.yaml", readFileFromClasspath("foo.yaml"));
  }

  /**
   * Create a new file in the temp directory
   *
   * @param path path of the file to create (relative to the temp directory's root)
   * @param content content of the file, or null for an empty file
   */
  static void addFile(Path tempDir, String path, String content) throws IOException {
    Path pathToCreate = tempDir.resolve(path);
    pathToCreate.toFile().getParentFile().mkdirs();
    Files.write(pathToCreate, content.getBytes());
  }

  private static String readFileFromClasspath(String fileName) throws IOException {
    // Obtain the URL of the file from the classpath
    URL fileUrl = Thread.currentThread().getContextClassLoader().getResource(fileName);
    if (fileUrl == null) {
      throw new IOException("File not found in classpath: " + fileName);
    }

    // Convert URL to a Path and read the file content
    try (Stream<String> lines = Files.lines(Paths.get(fileUrl.getPath()), StandardCharsets.UTF_8)) {
      return lines.collect(Collectors.joining("\n"));
    }
  }
}
