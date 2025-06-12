/*
 * Copyright 2019 Google, LLC
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

package com.netflix.spinnaker.rosco.manifests.helm;

import static com.netflix.spinnaker.rosco.manifests.ManifestTestUtils.makeSpinnakerHttpException;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.netflix.spinnaker.kork.artifacts.artifactstore.ArtifactStore;
import com.netflix.spinnaker.kork.artifacts.artifactstore.ArtifactStoreConfigurationProperties;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import com.netflix.spinnaker.kork.exceptions.SpinnakerException;
import com.netflix.spinnaker.kork.retrofit.exceptions.SpinnakerHttpException;
import com.netflix.spinnaker.rosco.jobs.BakeRecipe;
import com.netflix.spinnaker.rosco.manifests.ArtifactDownloader;
import com.netflix.spinnaker.rosco.manifests.BakeManifestEnvironment;
import com.netflix.spinnaker.rosco.manifests.BakeManifestRequest;
import com.netflix.spinnaker.rosco.manifests.config.RoscoHelmConfigurationProperties;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;
import org.apache.commons.compress.utils.IOUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;

final class HelmTemplateUtilsTest {

  public static final String OVERRIDES_FILE_PATH_PATTERN =
      ".*/overrides_[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}\\.yml$";
  private RoscoHelmConfigurationProperties helmConfigurationProperties;
  private ArtifactDownloader artifactDownloader;

  private HelmTemplateUtils helmTemplateUtils;

  private HelmBakeManifestRequest bakeManifestRequest;

  /** Configuration for the default values in the event artifact store had been turned off. */
  private ArtifactStoreConfigurationProperties artifactStoreConfig;

  private ArtifactStoreConfigurationProperties.HelmConfig helmConfig;

  @BeforeEach
  void init(TestInfo testInfo) {
    System.out.println("--------------- Test " + testInfo.getDisplayName());

    artifactDownloader = mock(ArtifactDownloader.class);
    helmConfigurationProperties = new RoscoHelmConfigurationProperties();
    artifactStoreConfig = new ArtifactStoreConfigurationProperties();
    helmConfig = new ArtifactStoreConfigurationProperties.HelmConfig();
    artifactStoreConfig.setHelm(helmConfig);
    helmConfig.setExpandOverrides(false);
    helmTemplateUtils =
        new HelmTemplateUtils(
            artifactDownloader, Optional.empty(), artifactStoreConfig, helmConfigurationProperties);
    Artifact chartArtifact = Artifact.builder().name("test-artifact").version("3").build();

    bakeManifestRequest = new HelmBakeManifestRequest();
    bakeManifestRequest.setInputArtifacts(ImmutableList.of(chartArtifact));
  }

  @Test
  public void nullReferenceTest() throws IOException {
    bakeManifestRequest.setOverrides(ImmutableMap.of());

    try (BakeManifestEnvironment env = BakeManifestEnvironment.create()) {
      BakeRecipe recipe = helmTemplateUtils.buildBakeRecipe(env, bakeManifestRequest);
    }
  }

  @Test
  public void realNullReferenceOfOverrides() throws IOException {
    bakeManifestRequest.setOverrides(null);

    try (BakeManifestEnvironment env = BakeManifestEnvironment.create()) {
      BakeRecipe recipe = helmTemplateUtils.buildBakeRecipe(env, bakeManifestRequest);
    }
  }

  @Test
  public void exceptionDownloading() throws IOException {
    // When artifactDownloader throws an exception, make sure we wrap it and get
    // a chance to include our own message, so the exception that goes up the
    // chain includes something about helm charts.
    SpinnakerException spinnakerException = new SpinnakerException("error from ArtifactDownloader");
    doThrow(spinnakerException)
        .when(artifactDownloader)
        .downloadArtifactToFile(any(Artifact.class), any(Path.class));

    try (BakeManifestEnvironment env = BakeManifestEnvironment.create()) {
      IllegalStateException thrown =
          assertThrows(
              IllegalStateException.class,
              () -> helmTemplateUtils.buildBakeRecipe(env, bakeManifestRequest));

      assertThat(thrown.getMessage()).contains("Failed to fetch helm template");
      assertThat(thrown.getCause()).isEqualTo(spinnakerException);
    }
  }

  @Test
  public void removeTestsDirectoryTemplatesWithTests() throws IOException {
    String inputManifests =
        "---\n"
            + "# Source: mysql/templates/pvc.yaml\n"
            + "\n"
            + "kind: PersistentVolumeClaim\n"
            + "apiVersion: v1\n"
            + "metadata:\n"
            + "  name: release-name-mysql\n"
            + "  namespace: default\n"
            + "spec:\n"
            + "  accessModes:\n"
            + "    - \"ReadWriteOnce\"\n"
            + "  resources:\n"
            + "    requests:\n"
            + "      storage: \"8Gi\"\n"
            + "---\n"
            + "# Source: mysql/templates/tests/test-configmap.yaml\n"
            + "apiVersion: v1\n"
            + "kind: ConfigMap\n"
            + "metadata:\n"
            + "  name: release-name-mysql-test\n"
            + "  namespace: default\n"
            + "data:\n"
            + "  run.sh: |-\n";

    ArtifactDownloader artifactDownloader = mock(ArtifactDownloader.class);
    RoscoHelmConfigurationProperties helmConfigurationProperties =
        new RoscoHelmConfigurationProperties();
    HelmTemplateUtils helmTemplateUtils =
        new HelmTemplateUtils(
            artifactDownloader, Optional.empty(), artifactStoreConfig, helmConfigurationProperties);

    String output = helmTemplateUtils.removeTestsDirectoryTemplates(inputManifests);

    String expected =
        "---\n"
            + "# Source: mysql/templates/pvc.yaml\n"
            + "\n"
            + "kind: PersistentVolumeClaim\n"
            + "apiVersion: v1\n"
            + "metadata:\n"
            + "  name: release-name-mysql\n"
            + "  namespace: default\n"
            + "spec:\n"
            + "  accessModes:\n"
            + "    - \"ReadWriteOnce\"\n"
            + "  resources:\n"
            + "    requests:\n"
            + "      storage: \"8Gi\"\n";

    assertEquals(expected.trim(), output.trim());
  }

  @Test
  public void removeTestsDirectoryTemplatesWithoutTests() throws IOException {
    String inputManifests =
        "---\n"
            + "# Source: mysql/templates/pvc.yaml\n"
            + "\n"
            + "kind: PersistentVolumeClaim\n"
            + "apiVersion: v1\n"
            + "metadata:\n"
            + "  name: release-name-mysql\n"
            + "  namespace: default\n"
            + "spec:\n"
            + "  accessModes:\n"
            + "    - \"ReadWriteOnce\"\n"
            + "  resources:\n"
            + "    requests:\n"
            + "      storage: \"8Gi\"\n"
            + "---\n"
            + "# Source: mysql/templates/configmap.yaml\n"
            + "apiVersion: v1\n"
            + "kind: ConfigMap\n"
            + "metadata:\n"
            + "  name: release-name-mysql-test\n"
            + "  namespace: default\n"
            + "data:\n"
            + "  run.sh: |-\n";

    ArtifactDownloader artifactDownloader = mock(ArtifactDownloader.class);
    RoscoHelmConfigurationProperties helmConfigurationProperties =
        new RoscoHelmConfigurationProperties();
    HelmTemplateUtils helmTemplateUtils =
        new HelmTemplateUtils(
            artifactDownloader, Optional.empty(), artifactStoreConfig, helmConfigurationProperties);

    String output = helmTemplateUtils.removeTestsDirectoryTemplates(inputManifests);

    assertEquals(inputManifests.trim(), output.trim());
  }

  @ParameterizedTest
  @MethodSource("helmRendererArgs")
  public void buildBakeRecipeSelectsHelmExecutableByVersion(
      String command, BakeManifestRequest.TemplateRenderer templateRenderer) throws IOException {
    ArtifactDownloader artifactDownloader = mock(ArtifactDownloader.class);
    RoscoHelmConfigurationProperties helmConfigurationProperties =
        new RoscoHelmConfigurationProperties();
    HelmTemplateUtils helmTemplateUtils =
        new HelmTemplateUtils(
            artifactDownloader, Optional.empty(), artifactStoreConfig, helmConfigurationProperties);

    HelmBakeManifestRequest request = new HelmBakeManifestRequest();
    Artifact artifact = Artifact.builder().build();
    request.setInputArtifacts(Collections.singletonList(artifact));
    request.setNamespace("default");
    request.setOverrides(Collections.emptyMap());

    request.setTemplateRenderer(templateRenderer);
    try (BakeManifestEnvironment env = BakeManifestEnvironment.create()) {
      BakeRecipe recipe = helmTemplateUtils.buildBakeRecipe(env, request);

      assertEquals(command, recipe.getCommand().get(0));
    }
  }

  private static Stream<Arguments> helmRendererArgs() {
    // The command here (e.g. helm, helm3) must match the defaults in
    // RoscoHelmConfigurationProperties
    return Stream.of(
        Arguments.of("helm", BakeManifestRequest.TemplateRenderer.HELM2),
        Arguments.of("helm3", BakeManifestRequest.TemplateRenderer.HELM3));
  }

  @Test
  public void buildBakeRecipeWithGitRepoArtifact(@TempDir Path tempDir) throws IOException {
    // git/repo artifacts appear as a tarball, so create one that contains a helm chart.
    addTestHelmChart(tempDir);

    ArtifactDownloader artifactDownloader = mock(ArtifactDownloader.class);
    RoscoHelmConfigurationProperties helmConfigurationProperties =
        new RoscoHelmConfigurationProperties();
    HelmTemplateUtils helmTemplateUtils =
        new HelmTemplateUtils(
            artifactDownloader, Optional.empty(), artifactStoreConfig, helmConfigurationProperties);

    HelmBakeManifestRequest request = new HelmBakeManifestRequest();

    Artifact artifact =
        Artifact.builder().type("git/repo").reference("https://github.com/some/repo.git").build();

    // Set up the mock artifactDownloader to supply the tarball that represents
    // the git/repo artifact
    when(artifactDownloader.downloadArtifact(artifact)).thenReturn(makeTarball(tempDir));

    request.setInputArtifacts(Collections.singletonList(artifact));
    request.setNamespace("default");
    request.setOverrides(Collections.emptyMap());

    try (BakeManifestEnvironment env = BakeManifestEnvironment.create()) {
      BakeRecipe recipe = helmTemplateUtils.buildBakeRecipe(env, request);

      // Make sure we're really testing the git/repo logic
      verify(artifactDownloader).downloadArtifact(artifact);

      // Make sure the BakeManifestEnvironment has the files in our git/repo artifact.
      assertTrue(env.resolvePath("Chart.yaml").toFile().exists());
      assertTrue(env.resolvePath("values.yaml").toFile().exists());
      assertTrue(env.resolvePath("templates/foo.yaml").toFile().exists());
    }
  }

  @Test
  public void buildBakeRecipeWithGitRepoArtifactUsingHelmChartFilePath(@TempDir Path tempDir)
      throws IOException {
    // Create a tarball with a helm chart in a sub directory
    String subDirName = "subdir";
    Path subDir = tempDir.resolve(subDirName);
    addTestHelmChart(subDir);

    ArtifactDownloader artifactDownloader = mock(ArtifactDownloader.class);
    RoscoHelmConfigurationProperties helmConfigurationProperties =
        new RoscoHelmConfigurationProperties();
    HelmTemplateUtils helmTemplateUtils =
        new HelmTemplateUtils(
            artifactDownloader, Optional.empty(), artifactStoreConfig, helmConfigurationProperties);

    HelmBakeManifestRequest request = new HelmBakeManifestRequest();

    // So we know where to look for the path to the chart in the resulting helm
    // template command, specify the renderer.  Use HELM2 since the path appears
    // earlier in the argument list.
    request.setTemplateRenderer(BakeManifestRequest.TemplateRenderer.HELM2);

    // Note that supplying a location for a git/repo artifact doesn't change the
    // path in the resulting tarball.  It's here because it's likely that it's
    // used together with helmChartFilePath.  Removing it wouldn't change the
    // test.
    Artifact artifact =
        Artifact.builder()
            .type("git/repo")
            .reference("https://github.com/some/repo.git")
            .location(subDirName)
            .build();

    // Set up the mock artifactDownloader to supply the tarball that represents
    // the git/repo artifact
    when(artifactDownloader.downloadArtifact(artifact)).thenReturn(makeTarball(tempDir));

    request.setInputArtifacts(Collections.singletonList(artifact));
    request.setOverrides(Collections.emptyMap());

    // This is the key part of this test.
    request.setHelmChartFilePath(subDirName);

    try (BakeManifestEnvironment env = BakeManifestEnvironment.create()) {
      BakeRecipe recipe = helmTemplateUtils.buildBakeRecipe(env, request);

      // Make sure we're really testing the git/repo logic
      verify(artifactDownloader).downloadArtifact(artifact);

      // Make sure the BakeManifestEnvironment has the files in our git/repo
      // artifact in the expected location.
      assertTrue(env.resolvePath(Path.of(subDirName, "Chart.yaml")).toFile().exists());
      assertTrue(env.resolvePath(Path.of(subDirName, "values.yaml")).toFile().exists());
      assertTrue(env.resolvePath(Path.of(subDirName, "templates/foo.yaml")).toFile().exists());

      // And that the helm template command includes the path to the subdirectory
      //
      // Given that we're using the HELM2 renderer, the expected elements in the
      // command list are:
      //
      // 0 - the helm executable
      // 1 - template
      // 2 - the path to Chart.yaml
      assertEquals(env.resolvePath(subDirName).toString(), recipe.getCommand().get(2));
    }
  }

  @Test
  public void buildBakeRecipeIncludingHelmVersionsOptionsWithHelm3() throws IOException {
    ArtifactDownloader artifactDownloader = mock(ArtifactDownloader.class);
    RoscoHelmConfigurationProperties helmConfigurationProperties =
        new RoscoHelmConfigurationProperties();
    HelmTemplateUtils helmTemplateUtils =
        new HelmTemplateUtils(
            artifactDownloader, Optional.empty(), artifactStoreConfig, helmConfigurationProperties);

    HelmBakeManifestRequest request = new HelmBakeManifestRequest();
    Artifact artifact = Artifact.builder().build();
    request.setTemplateRenderer(BakeManifestRequest.TemplateRenderer.HELM3);
    request.setApiVersions("customApiVersion");
    request.setKubeVersion("customKubernetesVersion");
    request.setInputArtifacts(Collections.singletonList(artifact));
    request.setOverrides(Collections.emptyMap());

    try (BakeManifestEnvironment env = BakeManifestEnvironment.create()) {
      BakeRecipe bakeRecipe = helmTemplateUtils.buildBakeRecipe(env, request);
      assertTrue(bakeRecipe.getCommand().contains("--api-versions"));
      assertTrue(bakeRecipe.getCommand().indexOf("--api-versions") > 3);
      assertTrue(bakeRecipe.getCommand().contains("--kube-version"));
      assertTrue(bakeRecipe.getCommand().indexOf("--kube-version") > 5);
    }
  }

  @Test
  public void buildBakeRecipeIncludingHelmVersionsOptionsWithHelm2() throws IOException {
    ArtifactDownloader artifactDownloader = mock(ArtifactDownloader.class);
    RoscoHelmConfigurationProperties helmConfigurationProperties =
        new RoscoHelmConfigurationProperties();
    HelmTemplateUtils helmTemplateUtils =
        new HelmTemplateUtils(
            artifactDownloader, Optional.empty(), artifactStoreConfig, helmConfigurationProperties);

    HelmBakeManifestRequest request = new HelmBakeManifestRequest();
    Artifact artifact = Artifact.builder().build();
    request.setTemplateRenderer(BakeManifestRequest.TemplateRenderer.HELM2);
    request.setApiVersions("customApiVersion");
    request.setKubeVersion("customKubernetesVersion");
    request.setInputArtifacts(Collections.singletonList(artifact));
    request.setOverrides(Collections.emptyMap());

    try (BakeManifestEnvironment env = BakeManifestEnvironment.create()) {
      BakeRecipe bakeRecipe = helmTemplateUtils.buildBakeRecipe(env, request);
      assertTrue(bakeRecipe.getCommand().contains("--api-versions"));
      assertTrue(bakeRecipe.getCommand().indexOf("--api-versions") > 3);
      assertTrue(bakeRecipe.getCommand().contains("--kube-version"));
      assertTrue(bakeRecipe.getCommand().indexOf("--kube-version") > 5);
    }
  }

  @Test
  public void buildBakeRecipeIncludingCRDsWithHelm3() throws IOException {
    ArtifactDownloader artifactDownloader = mock(ArtifactDownloader.class);
    RoscoHelmConfigurationProperties helmConfigurationProperties =
        new RoscoHelmConfigurationProperties();
    HelmTemplateUtils helmTemplateUtils =
        new HelmTemplateUtils(
            artifactDownloader, Optional.empty(), artifactStoreConfig, helmConfigurationProperties);

    HelmBakeManifestRequest request = new HelmBakeManifestRequest();
    Artifact artifact = Artifact.builder().build();
    request.setTemplateRenderer(BakeManifestRequest.TemplateRenderer.HELM3);
    request.setIncludeCRDs(true);
    request.setInputArtifacts(Collections.singletonList(artifact));
    request.setOverrides(Collections.emptyMap());

    try (BakeManifestEnvironment env = BakeManifestEnvironment.create()) {
      BakeRecipe recipe = helmTemplateUtils.buildBakeRecipe(env, request);
      assertTrue(recipe.getCommand().contains("--include-crds"));
      // Assert that the flag position goes after 'helm template' subcommand
      assertTrue(recipe.getCommand().indexOf("--include-crds") > 1);
    }
  }

  @ParameterizedTest(
      name =
          "ensureOverrides reference: {0} overrides: {1} expected: {2} artifactStoreNull: {3}, expandOverrides: {4}, overridesInValuesFile: {5}")
  @MethodSource("ensureOverrides")
  public void ensureOverridesGetEvaluated(
      String reference,
      Map<String, Object> overrides,
      Map<String, Object> expectedMap,
      Boolean artifactStoreNull,
      Boolean expandOverrides,
      Boolean overridesInValuesFile)
      throws IOException {

    // Assume that expectedMap has one element, and turn it into an expected argument for
    // --set/--set-string
    assertThat(expectedMap).hasSize(1);
    Map.Entry<String, Object> entry = expectedMap.entrySet().iterator().next();
    String expected = entry.getKey() + "=" + entry.getValue();

    ArtifactStore artifactStore = null;
    if (!artifactStoreNull) {
      artifactStore = mock(ArtifactStore.class);
      when(artifactStore.get(any())).thenReturn(Artifact.builder().reference(reference).build());
    }
    RoscoHelmConfigurationProperties helmConfigurationProperties =
        new RoscoHelmConfigurationProperties();

    if (overridesInValuesFile) {
      // Set the threshold so that rosco always uses a values file for
      // overrides, no matter their size.
      helmConfigurationProperties.setOverridesFileThreshold(1);
    }

    // do not use the artifactStoreConfig field as we are trying to test various
    // values of expandOverrides
    ArtifactStoreConfigurationProperties artifactStoreConfiguration =
        new ArtifactStoreConfigurationProperties();
    ArtifactStoreConfigurationProperties.HelmConfig helmConfig =
        new ArtifactStoreConfigurationProperties.HelmConfig();
    helmConfig.setExpandOverrides(expandOverrides);
    artifactStoreConfiguration.setHelm(helmConfig);

    HelmTemplateUtils helmTemplateUtils =
        new HelmTemplateUtils(
            artifactDownloader,
            Optional.ofNullable(artifactStore),
            artifactStoreConfiguration,
            helmConfigurationProperties);
    HelmBakeManifestRequest request = new HelmBakeManifestRequest();
    request.setOutputName("output_name");
    request.setTemplateRenderer(BakeManifestRequest.TemplateRenderer.HELM3);
    request.setOverrides(overrides);
    try (BakeManifestEnvironment env = BakeManifestEnvironment.create()) {
      BakeRecipe recipe =
          helmTemplateUtils.buildCommand(request, List.of(), Path.of("template_path"), env);
      if (overridesInValuesFile) {
        List<String> helmTemplateCommand = recipe.getCommand();
        assertThat(Collections.frequency(helmTemplateCommand, "--values")).isEqualTo(1);
        String lastValuesArgument =
            helmTemplateCommand.get(helmTemplateCommand.lastIndexOf("--values") + 1);
        assertThat(lastValuesArgument).matches(OVERRIDES_FILE_PATH_PATTERN);
        List<String> overridesYamlContents = Files.readAllLines(Path.of(lastValuesArgument));
        assertThat(helmTemplateCommand).doesNotContain("--set");
        assertThat(helmTemplateCommand).doesNotContain("--set-string");
        assertThat(helmTemplateCommand).contains("--values");
        assertThat(
                new ObjectMapper(new YAMLFactory())
                    .readValue(
                        String.join(System.lineSeparator(), overridesYamlContents),
                        new TypeReference<Map<String, Object>>() {}))
            .isEqualTo(expectedMap);
      } else {
        assertThat(recipe.getCommand().contains(expected)).isTrue();
      }
    }
  }

  private static Stream<Arguments> ensureOverrides() {
    return Stream.of(
        Arguments.of(
            "test", Map.of("foo", "ref://bar/baz"), Map.of("foo", "test"), false, true, false),
        Arguments.of(
            "test", Map.of("foo", "ref://bar/baz"), Map.of("foo", "test"), false, true, true),
        Arguments.of(
            "test",
            Map.of("foo", "ref://bar/baz"),
            Map.of("foo", "ref://bar/baz"),
            false,
            false,
            false),
        Arguments.of(
            "test",
            Map.of("foo", "ref://bar/baz"),
            Map.of("foo", "ref://bar/baz"),
            false,
            false,
            true),
        Arguments.of(
            "test",
            Map.of("foo", "ref://bar/baz"),
            Map.of("foo", "ref://bar/baz"),
            true,
            false,
            false),
        Arguments.of(
            "test",
            Map.of("foo", "ref://bar/baz"),
            Map.of("foo", "ref://bar/baz"),
            true,
            false,
            true));
  }

  @ParameterizedTest
  @MethodSource("helmRendererArgsCRDs")
  public void buildBakeRecipeNotIncludingCRDs(
      boolean includeCRDs, BakeManifestRequest.TemplateRenderer templateRenderer)
      throws IOException {
    ArtifactDownloader artifactDownloader = mock(ArtifactDownloader.class);
    RoscoHelmConfigurationProperties helmConfigurationProperties =
        new RoscoHelmConfigurationProperties();
    HelmTemplateUtils helmTemplateUtils =
        new HelmTemplateUtils(
            artifactDownloader, Optional.empty(), artifactStoreConfig, helmConfigurationProperties);

    HelmBakeManifestRequest request = new HelmBakeManifestRequest();
    Artifact artifact = Artifact.builder().build();
    request.setInputArtifacts(Collections.singletonList(artifact));
    request.setOverrides(Collections.emptyMap());

    request.setTemplateRenderer(templateRenderer);
    request.setIncludeCRDs(includeCRDs);

    try (BakeManifestEnvironment env = BakeManifestEnvironment.create()) {
      BakeRecipe recipe = helmTemplateUtils.buildBakeRecipe(env, request);
      assertFalse(recipe.getCommand().contains("--include-crds"));
    }
  }

  private static Stream<Arguments> helmRendererArgsCRDs() {
    return Stream.of(
        Arguments.of(true, BakeManifestRequest.TemplateRenderer.HELM2),
        Arguments.of(false, BakeManifestRequest.TemplateRenderer.HELM2),
        Arguments.of(false, BakeManifestRequest.TemplateRenderer.HELM3));
  }

  @Test
  public void httpExceptionDownloading() throws IOException {
    // When artifactDownloader throws a SpinnakerHttpException, make sure we
    // wrap it and get a chance to include our own message, so the exception
    // that goes up the chain includes something about helm charts.  It's
    // important that HelmTemplateUtils also throws a SpinnakerHttpException so
    // it's eventually handled properly...meaning the status code in the http
    // response and the logging correspond to what happened.  For example, if
    // there's a 404 from clouddriver, rosco also responds with 404, and doesn't
    // log an error.

    SpinnakerHttpException spinnakerHttpException =
        makeSpinnakerHttpException(HttpStatus.NOT_FOUND.value());
    doThrow(spinnakerHttpException)
        .when(artifactDownloader)
        .downloadArtifactToFile(any(Artifact.class), any(Path.class));

    try (BakeManifestEnvironment env = BakeManifestEnvironment.create()) {
      SpinnakerHttpException thrown =
          assertThrows(
              SpinnakerHttpException.class,
              () -> helmTemplateUtils.buildBakeRecipe(env, bakeManifestRequest));

      assertThat(thrown.getMessage()).contains("Failed to fetch helm template");
      assertThat(thrown.getResponseCode()).isEqualTo(HttpStatus.NOT_FOUND.value());
      assertThat(thrown.getCause()).isEqualTo(spinnakerHttpException);
    }
  }

  @Test
  public void testOverrideThresholdShortEnough() throws IOException {
    bakeManifestRequest.setOverrides(ImmutableMap.of("key1", "value1", "key2", "value2"));
    bakeManifestRequest.setRawOverrides(false);
    helmConfigurationProperties.setOverridesFileThreshold(100);

    try (BakeManifestEnvironment env = BakeManifestEnvironment.create()) {
      BakeRecipe recipe = helmTemplateUtils.buildBakeRecipe(env, bakeManifestRequest);

      assertThat(recipe.getCommand()).contains("--set-string");
      assertThat(recipe.getCommand()).doesNotContain("--values");
    }
  }

  @Test
  public void testOverrideThresholdShortEnoughWithRawOverrides() throws IOException {
    bakeManifestRequest.setOverrides(ImmutableMap.of("key1", "value1", "key2", "value2"));
    bakeManifestRequest.setRawOverrides(true);
    helmConfigurationProperties.setOverridesFileThreshold(100);

    try (BakeManifestEnvironment env = BakeManifestEnvironment.create()) {
      BakeRecipe recipe = helmTemplateUtils.buildBakeRecipe(env, bakeManifestRequest);

      assertThat(recipe.getCommand()).contains("--set");
      assertThat(recipe.getCommand()).doesNotContain("--values");
    }
  }

  @Test
  public void testOverrideThresholdExceedsLimitWithRawOverridesAsFalse() throws IOException {
    Artifact chartArtifact = Artifact.builder().name("test-artifact").build();
    Artifact valuesArtifact = Artifact.builder().name("test-artifact_values").build();
    bakeManifestRequest = new HelmBakeManifestRequest();
    bakeManifestRequest.setInputArtifacts(ImmutableList.of(chartArtifact, valuesArtifact));
    bakeManifestRequest.setOverrides(
        ImmutableMap.of("key1", "valu&e1", "key2", "value2:", "key3", 1, "key4", true));
    bakeManifestRequest.setRawOverrides(false);
    helmConfigurationProperties.setOverridesFileThreshold(10);
    try (BakeManifestEnvironment env = BakeManifestEnvironment.create()) {
      BakeRecipe recipe = helmTemplateUtils.buildBakeRecipe(env, bakeManifestRequest);
      List<String> helmTemplateCommand = recipe.getCommand();
      assertThat(Collections.frequency(helmTemplateCommand, "--values")).isEqualTo(2);
      String lastValuesArgument =
          helmTemplateCommand.get(helmTemplateCommand.lastIndexOf("--values") + 1);
      assertThat(lastValuesArgument).matches(OVERRIDES_FILE_PATH_PATTERN);
      List<String> overridesYamlContents = Files.readAllLines(Path.of(lastValuesArgument));
      assertThat(helmTemplateCommand).doesNotContain("--set");
      assertThat(helmTemplateCommand).doesNotContain("--set-string");
      assertThat(helmTemplateCommand).contains("--values");
      assertThat(
              new ObjectMapper(new YAMLFactory())
                  .readValue(
                      String.join(System.lineSeparator(), overridesYamlContents),
                      new TypeReference<Map<String, Object>>() {}))
          .isEqualTo(
              ImmutableMap.of("key1", "valu&e1", "key2", "value2:", "key3", "1", "key4", "true"));

      assertThat(overridesYamlContents)
          .containsExactly(
              "---", "key1: \"valu&e1\"", "key2: \"value2:\"", "key3: \"1\"", "key4: \"true\"");
    }
  }

  @ParameterizedTest(
      name = "testOverrideThresholdExceedsLimitWithRawOverridesAsTrue: expandOverrides {0}")
  @ValueSource(booleans = {true, false})
  public void testOverrideThresholdExceedsLimitWithRawOverridesAsTrue(boolean expandOverrides)
      throws IOException {
    Artifact chartArtifact = Artifact.builder().name("test-artifact").build();
    Artifact valuesArtifact = Artifact.builder().name("test-artifact_values").build();

    Map<String, Object> expectedOverrides =
        ImmutableMap.of("key1", "valu&e1", "key2", "value2:", "key4", true);
    ArtifactStore artifactStore = null;
    if (expandOverrides) {
      artifactStore = mock(ArtifactStore.class);
    }
    helmConfig.setExpandOverrides(expandOverrides);

    helmTemplateUtils =
        new HelmTemplateUtils(
            artifactDownloader,
            Optional.ofNullable(artifactStore),
            artifactStoreConfig,
            helmConfigurationProperties);

    bakeManifestRequest = new HelmBakeManifestRequest();
    bakeManifestRequest.setInputArtifacts(ImmutableList.of(chartArtifact, valuesArtifact));
    bakeManifestRequest.setOverrides(
        ImmutableMap.of("key1", "valu&e1", "key2", "value2:", "key3", 100000000, "key4", true));
    bakeManifestRequest.setRawOverrides(true);
    helmConfigurationProperties.setOverridesFileThreshold(10);
    try (BakeManifestEnvironment env = BakeManifestEnvironment.create()) {
      BakeRecipe recipe = helmTemplateUtils.buildBakeRecipe(env, bakeManifestRequest);
      List<String> helmTemplateCommand = recipe.getCommand();
      String lastValuesArgument =
          helmTemplateCommand.get(helmTemplateCommand.lastIndexOf("--values") + 1);
      assertThat(Collections.frequency(helmTemplateCommand, "--values")).isEqualTo(2);
      assertThat(lastValuesArgument).matches(OVERRIDES_FILE_PATH_PATTERN);
      List<String> overridesYamlContents = Files.readAllLines(Path.of(lastValuesArgument));
      assertThat(helmTemplateCommand).contains("--set");
      assertThat(helmTemplateCommand).contains("key3=100000000");
      assertThat(helmTemplateCommand).doesNotContain("--set-string");
      assertThat(helmTemplateCommand).contains("--values");
      assertThat(
              new ObjectMapper(new YAMLFactory())
                  .readValue(
                      String.join(System.lineSeparator(), overridesYamlContents),
                      new TypeReference<Map<String, Object>>() {}))
          .isEqualTo(expectedOverrides);
      assertThat(overridesYamlContents)
          .containsExactly("---", "key1: \"valu&e1\"", "key2: \"value2:\"", "key4: true");
    }
  }

  @Test
  public void buildBakeRecipeWithHelmImageArtifact(@TempDir Path tempDir) throws IOException {
    // helm/image artifacts appear as a tarball, so create one that contains a helm chart.
    addTestHelmChart(tempDir);

    ArtifactDownloader artifactDownloader = mock(ArtifactDownloader.class);
    RoscoHelmConfigurationProperties helmConfigurationProperties =
        new RoscoHelmConfigurationProperties();
    HelmTemplateUtils helmTemplateUtils =
        new HelmTemplateUtils(
            artifactDownloader, Optional.empty(), artifactStoreConfig, helmConfigurationProperties);

    HelmBakeManifestRequest request = new HelmBakeManifestRequest();

    Artifact artifact =
        Artifact.builder()
            .type("helm/image")
            .artifactAccount("docker-helm-account")
            .version("1.0.0")
            .name("repo/chart")
            .build();

    // Set up the mock artifactDownloader to supply the tarball that represents
    // the helm/image artifact
    when(artifactDownloader.downloadArtifact(artifact)).thenReturn(makeTarball(tempDir));

    request.setInputArtifacts(Collections.singletonList(artifact));
    request.setNamespace("default");
    request.setOverrides(Collections.emptyMap());

    try (BakeManifestEnvironment env = BakeManifestEnvironment.create()) {
      BakeRecipe recipe = helmTemplateUtils.buildBakeRecipe(env, request);

      // Make sure we're really testing the helm/image logic
      verify(artifactDownloader).downloadArtifact(artifact);

      // Make sure the BakeManifestEnvironment has the files in our helm/image artifact.
      assertTrue(env.resolvePath("Chart.yaml").toFile().exists());
      assertTrue(env.resolvePath("values.yaml").toFile().exists());
      assertTrue(env.resolvePath("templates/foo.yaml").toFile().exists());
    }
  }

  @Test
  public void buildBakeRecipeWithHelmImageArtifactUsingHelmChartFilePath(@TempDir Path tempDir)
      throws IOException {
    // Create a tarball with a helm chart in a sub directory
    String subDirName = "subdir";
    Path subDir = tempDir.resolve(subDirName);
    addTestHelmChart(subDir);

    ArtifactDownloader artifactDownloader = mock(ArtifactDownloader.class);
    RoscoHelmConfigurationProperties helmConfigurationProperties =
        new RoscoHelmConfigurationProperties();
    HelmTemplateUtils helmTemplateUtils =
        new HelmTemplateUtils(
            artifactDownloader, Optional.empty(), artifactStoreConfig, helmConfigurationProperties);

    HelmBakeManifestRequest request = new HelmBakeManifestRequest();

    // So we know where to look for the path to the chart in the resulting helm
    // template command, specify the renderer.  Use HELM2 since the path appears
    // earlier in the argument list.
    request.setTemplateRenderer(BakeManifestRequest.TemplateRenderer.HELM2);

    // Note that supplying a location for a helm/image artifact doesn't change the
    // path in the resulting tarball.  It's here because it's likely that it's
    // used together with helmChartFilePath.  Removing it wouldn't change the
    // test.
    Artifact artifact =
        Artifact.builder()
            .type("helm/image")
            .artifactAccount("docker-helm-account")
            .version("1.0.0")
            .name("repo/chart")
            .location(subDirName)
            .build();

    // Set up the mock artifactDownloader to supply the tarball that represents
    // the helm/image artifact
    when(artifactDownloader.downloadArtifact(artifact)).thenReturn(makeTarball(tempDir));

    request.setInputArtifacts(Collections.singletonList(artifact));
    request.setOverrides(Collections.emptyMap());

    // This is the key part of this test.
    request.setHelmChartFilePath(subDirName);

    try (BakeManifestEnvironment env = BakeManifestEnvironment.create()) {
      BakeRecipe recipe = helmTemplateUtils.buildBakeRecipe(env, request);

      // Make sure we're really testing the helm/image logic
      verify(artifactDownloader).downloadArtifact(artifact);

      // Make sure the BakeManifestEnvironment has the files in our git/repo
      // artifact in the expected location.
      assertTrue(env.resolvePath(Path.of(subDirName, "Chart.yaml")).toFile().exists());
      assertTrue(env.resolvePath(Path.of(subDirName, "values.yaml")).toFile().exists());
      assertTrue(env.resolvePath(Path.of(subDirName, "templates/foo.yaml")).toFile().exists());

      // And that the helm template command includes the path to the subdirectory
      //
      // Given that we're using the HELM2 renderer, the expected elements in the
      // command list are:
      //
      // 0 - the helm executable
      // 1 - template
      // 2 - the path to Chart.yaml
      assertEquals(env.resolvePath(subDirName).toString(), recipe.getCommand().get(2));
    }
  }

  /**
   * Add a helm chart for testing
   *
   * @param path the location of the helm chart (e.g. Chart.yaml)
   */
  void addTestHelmChart(Path path) throws IOException {
    addFile(
        path,
        "Chart.yaml",
        "apiVersion: v1\n"
            + "name: example\n"
            + "description: chart for testing\n"
            + "version: 0.1\n"
            + "engine: gotpl\n");

    addFile(path, "values.yaml", "foo: bar\n");

    addFile(
        path,
        "templates/foo.yaml",
        "labels:\n" + "helm.sh/chart: {{ .Chart.Name }}-{{ .Chart.Version }}\n");
  }

  /**
   * Create a new file in the temp directory
   *
   * @param path the path of the file to create (relative to the temp directory's root)
   * @param content the content of the file, or null for an empty file
   */
  void addFile(Path tempDir, String path, String content) throws IOException {
    Path pathToCreate = tempDir.resolve(path);
    pathToCreate.toFile().getParentFile().mkdirs();
    Files.write(pathToCreate, content.getBytes());
  }

  /**
   * Make a gzipped tarball of all files in a path
   *
   * @param rootPath the root path of the tarball
   * @return an InputStream containing the gzipped tarball
   */
  InputStream makeTarball(Path rootPath) throws IOException {
    ArrayList<Path> filePathsToAdd =
        Files.walk(rootPath, FileVisitOption.FOLLOW_LINKS)
            .filter(path -> !path.equals(rootPath))
            .collect(Collectors.toCollection(ArrayList::new));

    // See
    // https://commons.apache.org/proper/commons-compress/examples.html#Common_Archival_Logic
    // for background
    try (ByteArrayOutputStream os = new ByteArrayOutputStream();
        GzipCompressorOutputStream gzo = new GzipCompressorOutputStream(os);
        TarArchiveOutputStream tarArchive = new TarArchiveOutputStream(gzo)) {
      for (Path path : filePathsToAdd) {
        TarArchiveEntry tarEntry =
            new TarArchiveEntry(path.toFile(), rootPath.relativize(path).toString());
        tarArchive.putArchiveEntry(tarEntry);
        if (path.toFile().isFile()) {
          IOUtils.copy(Files.newInputStream(path), tarArchive);
        }
        tarArchive.closeArchiveEntry();
      }

      tarArchive.finish();
      gzo.finish();

      return new ByteArrayInputStream(os.toByteArray());
    }
  }
}
