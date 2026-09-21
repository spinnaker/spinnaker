/*
 * Copyright 2023 Grab Holdings, Inc.
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

package com.netflix.spinnaker.rosco.manifests.helmfile;

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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.netflix.spinnaker.kork.artifacts.artifactstore.ArtifactStoreConfigurationProperties;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import com.netflix.spinnaker.kork.exceptions.SpinnakerException;
import com.netflix.spinnaker.kork.retrofit.exceptions.SpinnakerHttpException;
import com.netflix.spinnaker.kork.yaml.YamlHelper;
import com.netflix.spinnaker.kork.yaml.YamlParserProperties;
import com.netflix.spinnaker.rosco.jobs.BakeRecipe;
import com.netflix.spinnaker.rosco.manifests.ArtifactDownloader;
import com.netflix.spinnaker.rosco.manifests.BakeManifestEnvironment;
import com.netflix.spinnaker.rosco.manifests.BakeManifestRequest;
import com.netflix.spinnaker.rosco.manifests.config.RoscoHelmConfigurationProperties;
import com.netflix.spinnaker.rosco.manifests.config.RoscoHelmfileConfigurationProperties;
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
import org.springframework.http.HttpStatus;

final class HelmfileTemplateUtilsTest {

  private ArtifactDownloader artifactDownloader;

  private HelmfileTemplateUtils helmfileTemplateUtils;

  private HelmfileBakeManifestRequest bakeManifestRequest;

  private ArtifactStoreConfigurationProperties artifactStoreConfig;

  @BeforeEach
  void init(TestInfo testInfo) {
    System.out.println("--------------- Test " + testInfo.getDisplayName());

    artifactDownloader = mock(ArtifactDownloader.class);
    RoscoHelmfileConfigurationProperties helmfileConfigurationProperties =
        new RoscoHelmfileConfigurationProperties();
    artifactStoreConfig = new ArtifactStoreConfigurationProperties();
    ArtifactStoreConfigurationProperties.HelmConfig helmConfig =
        new ArtifactStoreConfigurationProperties.HelmConfig();
    artifactStoreConfig.setHelm(helmConfig);
    helmConfig.setExpandOverrides(false);
    helmfileTemplateUtils =
        new HelmfileTemplateUtils(
            artifactDownloader,
            Optional.empty(),
            artifactStoreConfig,
            helmfileConfigurationProperties,
            new YamlHelper(new YamlParserProperties()));
    Artifact chartArtifact = Artifact.builder().name("test-artifact").version("3").build();

    bakeManifestRequest = new HelmfileBakeManifestRequest();
    bakeManifestRequest.setInputArtifacts(ImmutableList.of(chartArtifact));
  }

  @Test
  public void nullReferenceTest() throws IOException {
    bakeManifestRequest.setOverrides(ImmutableMap.of());

    try (BakeManifestEnvironment env = BakeManifestEnvironment.create()) {
      BakeRecipe recipe = helmfileTemplateUtils.buildBakeRecipe(env, bakeManifestRequest);
    }
  }

  @Test
  public void exceptionDownloading() throws IOException {
    // When artifactDownloader throws an exception, make sure we wrap it and get
    // a chance to include our own message, so the exception that goes up the
    // chain includes something about helmfile.
    SpinnakerException spinnakerException = new SpinnakerException("error from ArtifactDownloader");
    doThrow(spinnakerException)
        .when(artifactDownloader)
        .downloadArtifactToFile(any(Artifact.class), any(Path.class));

    try (BakeManifestEnvironment env = BakeManifestEnvironment.create()) {
      IllegalStateException thrown =
          assertThrows(
              IllegalStateException.class,
              () -> helmfileTemplateUtils.buildBakeRecipe(env, bakeManifestRequest));

      assertThat(thrown.getMessage()).contains("Failed to fetch helmfile template");
      assertThat(thrown.getCause()).isEqualTo(spinnakerException);
    }
  }

  @Test
  public void httpExceptionDownloading() throws IOException {
    // When artifactDownloader throws a SpinnakerHttpException, make sure we
    // wrap it and get a chance to include our own message, so the exception
    // that goes up the chain includes something about helm charts. It's
    // important that HelmTemplateUtils also throws a SpinnakerHttpException so
    // it's eventually handled properly...meaning the status code in the http
    // response and the logging correspond to what happened. For example, if
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
              () -> helmfileTemplateUtils.buildBakeRecipe(env, bakeManifestRequest));

      assertThat(thrown.getMessage()).contains("Failed to fetch helmfile template");
      assertThat(thrown.getResponseCode()).isEqualTo(HttpStatus.NOT_FOUND.value());
      assertThat(thrown.getCause()).isEqualTo(spinnakerHttpException);
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
    RoscoHelmfileConfigurationProperties helmfileConfigurationProperties =
        new RoscoHelmfileConfigurationProperties();
    HelmfileTemplateUtils helmfileTemplateUtils =
        new HelmfileTemplateUtils(
            artifactDownloader,
            Optional.empty(),
            artifactStoreConfig,
            helmfileConfigurationProperties,
            new YamlHelper(new YamlParserProperties()));

    String output = helmfileTemplateUtils.removeTestsDirectoryTemplates(inputManifests);

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
    RoscoHelmfileConfigurationProperties helmfileConfigurationProperties =
        new RoscoHelmfileConfigurationProperties();
    HelmfileTemplateUtils helmfileTemplateUtils =
        new HelmfileTemplateUtils(
            artifactDownloader,
            Optional.empty(),
            artifactStoreConfig,
            helmfileConfigurationProperties,
            new YamlHelper(new YamlParserProperties()));

    String output = helmfileTemplateUtils.removeTestsDirectoryTemplates(inputManifests);

    assertEquals(inputManifests.trim(), output.trim());
  }

  @ParameterizedTest
  @MethodSource("helmfileRendererArgs")
  public void buildBakeRecipeSelectsHelm3ExecutableWhenNoneSet(
      String command, BakeManifestRequest.TemplateRenderer templateRenderer) throws IOException {
    ArtifactDownloader artifactDownloader = mock(ArtifactDownloader.class);
    RoscoHelmfileConfigurationProperties helmfileConfigurationProperties =
        new RoscoHelmfileConfigurationProperties();
    HelmfileTemplateUtils helmfileTemplateUtils =
        new HelmfileTemplateUtils(
            artifactDownloader,
            Optional.empty(),
            artifactStoreConfig,
            helmfileConfigurationProperties,
            new YamlHelper(new YamlParserProperties()));
    RoscoHelmConfigurationProperties helmConfigurationProperties =
        new RoscoHelmConfigurationProperties();

    HelmfileBakeManifestRequest request = new HelmfileBakeManifestRequest();
    Artifact artifact = Artifact.builder().build();
    request.setInputArtifacts(Collections.singletonList(artifact));
    request.setOverrides(Collections.emptyMap());

    request.setTemplateRenderer(templateRenderer);
    try (BakeManifestEnvironment env = BakeManifestEnvironment.create()) {
      BakeRecipe recipe = helmfileTemplateUtils.buildBakeRecipe(env, request);

      assertEquals(helmConfigurationProperties.getV3ExecutablePath(), recipe.getCommand().get(5));
    }
  }

  private static Stream<Arguments> helmfileRendererArgs() {
    // The command here (e.g. helmfile) must match the defaults in
    // RoscoHelmfileConfigurationProperties
    return Stream.of(Arguments.of("helmfile", BakeManifestRequest.TemplateRenderer.HELMFILE));
  }

  @Test
  public void buildBakeRecipeWithGitRepoArtifact(@TempDir Path tempDir) throws IOException {
    // git/repo artifacts appear as a tarball, so create one that contains a
    // helmfile file
    // and helm chart.
    addTestHelmfile(tempDir);

    ArtifactDownloader artifactDownloader = mock(ArtifactDownloader.class);
    RoscoHelmfileConfigurationProperties helmfileConfigurationProperties =
        new RoscoHelmfileConfigurationProperties();
    HelmfileTemplateUtils helmfileTemplateUtils =
        new HelmfileTemplateUtils(
            artifactDownloader,
            Optional.empty(),
            artifactStoreConfig,
            helmfileConfigurationProperties,
            new YamlHelper(new YamlParserProperties()));

    HelmfileBakeManifestRequest request = new HelmfileBakeManifestRequest();

    Artifact artifact =
        Artifact.builder().type("git/repo").reference("https://github.com/some/repo.git").build();

    // Set up the mock artifactDownloader to supply the tarball that represents
    // the git/repo artifact
    when(artifactDownloader.downloadArtifact(artifact)).thenReturn(makeTarball(tempDir));

    request.setInputArtifacts(Collections.singletonList(artifact));
    request.setOverrides(Collections.emptyMap());

    try (BakeManifestEnvironment env = BakeManifestEnvironment.create()) {
      BakeRecipe recipe = helmfileTemplateUtils.buildBakeRecipe(env, request);

      // Make sure we're really testing the git/repo logic
      verify(artifactDownloader).downloadArtifact(artifact);

      // Make sure the BakeManifestEnvironment has the files in our git/repo artifact.
      assertTrue(env.resolvePath("helmfile.yaml").toFile().exists());
      assertTrue(env.resolvePath("Chart.yaml").toFile().exists());
      assertTrue(env.resolvePath("values.yaml").toFile().exists());
      assertTrue(env.resolvePath("templates/foo.yaml").toFile().exists());
    }
  }

  @Test
  public void buildBakeRecipeWithGitRepoArtifactUsingHelmfileFilePath(@TempDir Path tempDir)
      throws IOException {
    // Create a tarball with a helmfile in a sub directory
    String subDirName = "subdir";
    Path subDir = tempDir.resolve(subDirName);
    addTestHelmfile(subDir);

    ArtifactDownloader artifactDownloader = mock(ArtifactDownloader.class);
    RoscoHelmfileConfigurationProperties helmfileConfigurationProperties =
        new RoscoHelmfileConfigurationProperties();
    HelmfileTemplateUtils helmfileTemplateUtils =
        new HelmfileTemplateUtils(
            artifactDownloader,
            Optional.empty(),
            artifactStoreConfig,
            helmfileConfigurationProperties,
            new YamlHelper(new YamlParserProperties()));

    HelmfileBakeManifestRequest request = new HelmfileBakeManifestRequest();

    // Note that supplying a location for a git/repo artifact doesn't change the
    // path in the resulting tarball. It's here because it's likely that it's
    // used together with helmChartFilePath. Removing it wouldn't change the
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
    request.setHelmfileFilePath(subDirName);

    try (BakeManifestEnvironment env = BakeManifestEnvironment.create()) {
      BakeRecipe recipe = helmfileTemplateUtils.buildBakeRecipe(env, request);

      // Make sure we're really testing the git/repo logic
      verify(artifactDownloader).downloadArtifact(artifact);

      // Make sure the BakeManifestEnvironment has the files in our git/repo
      // artifact in the expected location.
      assertTrue(env.resolvePath(Path.of(subDirName, "helmfile.yaml")).toFile().exists());
      assertTrue(env.resolvePath(Path.of(subDirName, "Chart.yaml")).toFile().exists());
      assertTrue(env.resolvePath(Path.of(subDirName, "values.yaml")).toFile().exists());
      assertTrue(env.resolvePath(Path.of(subDirName, "templates/foo.yaml")).toFile().exists());

      // And that the helm template command includes the path to the subdirectory
      //
      // The expected elements in the
      // command list are:
      //
      // 0 - the helm executable
      // 1 - template
      // 2 - --file flag
      // 3 - the path to helmfile.yaml
      assertEquals(env.resolvePath(subDirName).toString(), recipe.getCommand().get(3));
    }
  }

  /**
   * Add a helmfile and helm chart for testing
   *
   * @param path the location of the helmfile file (e.g. helmfile.yaml)
   */
  void addTestHelmfile(Path path) throws IOException {
    addFile(
        path,
        "helmfile.yaml",
        "releases:\n"
            + "  - name: test\n"
            + "    namespace: namespace\n"
            + "    chart: Chart.yaml\n"
            + "    values:\n"
            + "      - values.yaml\n");

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
        tarArchive.setBigNumberMode(tarArchive.BIGNUMBER_POSIX);
        tarArchive.setLongFileMode(tarArchive.LONGFILE_POSIX);
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

  @Test
  public void buildBakeRecipeIncludesEnvironmentWhenSet() throws IOException {
    ArtifactDownloader artifactDownloader = mock(ArtifactDownloader.class);
    RoscoHelmfileConfigurationProperties helmfileConfigurationProperties =
        new RoscoHelmfileConfigurationProperties();
    HelmfileTemplateUtils helmfileTemplateUtils =
        new HelmfileTemplateUtils(
            artifactDownloader,
            Optional.empty(),
            artifactStoreConfig,
            helmfileConfigurationProperties,
            new YamlHelper(new YamlParserProperties()));

    HelmfileBakeManifestRequest request = new HelmfileBakeManifestRequest();
    Artifact artifact = Artifact.builder().build();
    String envName = "testEnvironment";
    request.setEnvironment(envName);
    request.setInputArtifacts(Collections.singletonList(artifact));
    request.setOverrides(Collections.emptyMap());

    try (BakeManifestEnvironment env = BakeManifestEnvironment.create()) {
      BakeRecipe recipe = helmfileTemplateUtils.buildBakeRecipe(env, request);
      assertTrue(recipe.getCommand().contains("--environment"));
      assertTrue(recipe.getCommand().contains(envName));
      // Assert that the flag position goes after 'helmfile template' subcommand
      assertTrue(recipe.getCommand().indexOf("--environment") > 1);
    }
  }

  @Test
  public void buildBakeRecipeDoesNotIncludeEnvironmentWhenNotSet() throws IOException {
    ArtifactDownloader artifactDownloader = mock(ArtifactDownloader.class);
    RoscoHelmfileConfigurationProperties helmfileConfigurationProperties =
        new RoscoHelmfileConfigurationProperties();
    HelmfileTemplateUtils helmfileTemplateUtils =
        new HelmfileTemplateUtils(
            artifactDownloader,
            Optional.empty(),
            artifactStoreConfig,
            helmfileConfigurationProperties,
            new YamlHelper(new YamlParserProperties()));

    HelmfileBakeManifestRequest request = new HelmfileBakeManifestRequest();
    Artifact artifact = Artifact.builder().build();
    request.setInputArtifacts(Collections.singletonList(artifact));
    request.setOverrides(Collections.emptyMap());

    try (BakeManifestEnvironment env = BakeManifestEnvironment.create()) {
      BakeRecipe recipe = helmfileTemplateUtils.buildBakeRecipe(env, request);
      assertFalse(recipe.getCommand().contains("--environment"));
    }
  }

  @Test
  public void buildBakeRecipeIncludesNamespaceWhenSet() throws IOException {
    ArtifactDownloader artifactDownloader = mock(ArtifactDownloader.class);
    RoscoHelmfileConfigurationProperties helmfileConfigurationProperties =
        new RoscoHelmfileConfigurationProperties();
    HelmfileTemplateUtils helmfileTemplateUtils =
        new HelmfileTemplateUtils(
            artifactDownloader,
            Optional.empty(),
            artifactStoreConfig,
            helmfileConfigurationProperties,
            new YamlHelper(new YamlParserProperties()));

    HelmfileBakeManifestRequest request = new HelmfileBakeManifestRequest();
    Artifact artifact = Artifact.builder().build();
    String namespaceName = "testNamespace";
    request.setNamespace(namespaceName);
    request.setInputArtifacts(Collections.singletonList(artifact));
    request.setOverrides(Collections.emptyMap());

    try (BakeManifestEnvironment env = BakeManifestEnvironment.create()) {
      BakeRecipe recipe = helmfileTemplateUtils.buildBakeRecipe(env, request);
      assertTrue(recipe.getCommand().contains("--namespace"));
      assertTrue(recipe.getCommand().contains(namespaceName));
      // Assert that the flag position goes after 'helmfile template' subcommand
      assertTrue(recipe.getCommand().indexOf("--namespace") > 1);
    }
  }

  @Test
  public void buildBakeRecipeDoesNotIncludeNamespaceWhenNotSet() throws IOException {
    ArtifactDownloader artifactDownloader = mock(ArtifactDownloader.class);
    RoscoHelmfileConfigurationProperties helmfileConfigurationProperties =
        new RoscoHelmfileConfigurationProperties();
    HelmfileTemplateUtils helmfileTemplateUtils =
        new HelmfileTemplateUtils(
            artifactDownloader,
            Optional.empty(),
            artifactStoreConfig,
            helmfileConfigurationProperties,
            new YamlHelper(new YamlParserProperties()));

    HelmfileBakeManifestRequest request = new HelmfileBakeManifestRequest();
    Artifact artifact = Artifact.builder().build();
    request.setInputArtifacts(Collections.singletonList(artifact));
    request.setOverrides(Collections.emptyMap());

    try (BakeManifestEnvironment env = BakeManifestEnvironment.create()) {
      BakeRecipe recipe = helmfileTemplateUtils.buildBakeRecipe(env, request);
      assertFalse(recipe.getCommand().contains("--namespace"));
    }
  }

  @Test
  public void buildBakeRecipeIncludingCRDsWithHelm3() throws IOException {
    ArtifactDownloader artifactDownloader = mock(ArtifactDownloader.class);
    RoscoHelmfileConfigurationProperties helmfileConfigurationProperties =
        new RoscoHelmfileConfigurationProperties();
    HelmfileTemplateUtils helmfileTemplateUtils =
        new HelmfileTemplateUtils(
            artifactDownloader,
            Optional.empty(),
            artifactStoreConfig,
            helmfileConfigurationProperties,
            new YamlHelper(new YamlParserProperties()));

    HelmfileBakeManifestRequest request = new HelmfileBakeManifestRequest();
    Artifact artifact = Artifact.builder().build();
    request.setIncludeCRDs(true);
    request.setInputArtifacts(Collections.singletonList(artifact));
    request.setOverrides(Collections.emptyMap());

    try (BakeManifestEnvironment env = BakeManifestEnvironment.create()) {
      BakeRecipe recipe = helmfileTemplateUtils.buildBakeRecipe(env, request);
      assertTrue(recipe.getCommand().contains("--include-crds"));
      // Assert that the flag position goes after 'helmfile template' subcommand
      assertTrue(recipe.getCommand().indexOf("--include-crds") > 1);
    }
  }

  @Test
  public void buildBakeRecipeNotIncludingCRDsWithHelm3() throws IOException {
    ArtifactDownloader artifactDownloader = mock(ArtifactDownloader.class);
    RoscoHelmfileConfigurationProperties helmfileConfigurationProperties =
        new RoscoHelmfileConfigurationProperties();
    HelmfileTemplateUtils helmfileTemplateUtils =
        new HelmfileTemplateUtils(
            artifactDownloader,
            Optional.empty(),
            artifactStoreConfig,
            helmfileConfigurationProperties,
            new YamlHelper(new YamlParserProperties()));

    HelmfileBakeManifestRequest request = new HelmfileBakeManifestRequest();
    Artifact artifact = Artifact.builder().build();
    request.setInputArtifacts(Collections.singletonList(artifact));
    request.setOverrides(Collections.emptyMap());

    try (BakeManifestEnvironment env = BakeManifestEnvironment.create()) {
      BakeRecipe recipe = helmfileTemplateUtils.buildBakeRecipe(env, request);
      assertFalse(recipe.getCommand().contains("--include-crds"));
    }
  }

  @Test
  public void buildBakeRecipeIncludesOverridesWhenSet() throws IOException {
    ArtifactDownloader artifactDownloader = mock(ArtifactDownloader.class);
    RoscoHelmfileConfigurationProperties helmfileConfigurationProperties =
        new RoscoHelmfileConfigurationProperties();
    HelmfileTemplateUtils helmfileTemplateUtils =
        new HelmfileTemplateUtils(
            artifactDownloader,
            Optional.empty(),
            artifactStoreConfig,
            helmfileConfigurationProperties,
            new YamlHelper(new YamlParserProperties()));

    HelmfileBakeManifestRequest request = new HelmfileBakeManifestRequest();
    Artifact artifact = Artifact.builder().build();
    String overrideKey = "testOverrideKey";
    String overrideValue = "testOverrideValue";
    request.setInputArtifacts(Collections.singletonList(artifact));
    request.setOverrides(Collections.singletonMap(overrideKey, overrideValue));

    try (BakeManifestEnvironment env = BakeManifestEnvironment.create()) {
      BakeRecipe recipe = helmfileTemplateUtils.buildBakeRecipe(env, request);
      assertTrue(recipe.getCommand().contains("--set"));
      assertTrue(recipe.getCommand().contains(overrideKey + "=" + overrideValue));
      // Assert that the flag position goes after 'helmfile template' subcommand
      assertTrue(recipe.getCommand().indexOf("--set") > 1);
    }
  }

  @Test
  public void buildBakeRecipeDoesNotIncludeOverridesWhenNotSet() throws IOException {
    ArtifactDownloader artifactDownloader = mock(ArtifactDownloader.class);
    RoscoHelmfileConfigurationProperties helmfileConfigurationProperties =
        new RoscoHelmfileConfigurationProperties();
    HelmfileTemplateUtils helmfileTemplateUtils =
        new HelmfileTemplateUtils(
            artifactDownloader,
            Optional.empty(),
            artifactStoreConfig,
            helmfileConfigurationProperties,
            new YamlHelper(new YamlParserProperties()));

    HelmfileBakeManifestRequest request = new HelmfileBakeManifestRequest();
    Artifact artifact = Artifact.builder().build();
    request.setInputArtifacts(Collections.singletonList(artifact));
    request.setOverrides(Collections.emptyMap());

    try (BakeManifestEnvironment env = BakeManifestEnvironment.create()) {
      BakeRecipe recipe = helmfileTemplateUtils.buildBakeRecipe(env, request);
      assertFalse(recipe.getCommand().contains("--set"));
    }
  }

  @Test
  public void buildBakeRecipeIncludesValuesWhenSet() throws IOException {
    ArtifactDownloader artifactDownloader = mock(ArtifactDownloader.class);
    RoscoHelmfileConfigurationProperties helmfileConfigurationProperties =
        new RoscoHelmfileConfigurationProperties();
    HelmfileTemplateUtils helmfileTemplateUtils =
        new HelmfileTemplateUtils(
            artifactDownloader,
            Optional.empty(),
            artifactStoreConfig,
            helmfileConfigurationProperties,
            new YamlHelper(new YamlParserProperties()));

    HelmfileBakeManifestRequest request = new HelmfileBakeManifestRequest();

    List<Artifact> artifacts = new ArrayList<>();
    Artifact artifact = Artifact.builder().build();
    Artifact testValue = Artifact.builder().build();
    Artifact testValue2 = Artifact.builder().build();
    artifacts.add(artifact);
    artifacts.add(testValue);
    artifacts.add(testValue2);

    request.setInputArtifacts(artifacts);
    request.setOverrides(Collections.emptyMap());

    try (BakeManifestEnvironment env = BakeManifestEnvironment.create()) {
      BakeRecipe recipe = helmfileTemplateUtils.buildBakeRecipe(env, request);
      assertTrue(recipe.getCommand().contains("--values"));
      // Assert that the flag position goes after 'helmfile template' subcommand
      assertTrue(recipe.getCommand().indexOf("--values") > 1);
      // Assert that the number of --values match the number of extra values artifacts
      assertEquals(
          artifacts.size() - 1,
          (int) recipe.getCommand().stream().filter(arg -> arg.equals("--values")).count());

      // Verify each '--values' flag is followed by a path to a file
      for (int i = 0; i < recipe.getCommand().size(); i++) {
        if ("--values".equals(recipe.getCommand().get(i))) {
          int nextIdx = i + 1;
          assertTrue(nextIdx < recipe.getCommand().size(), "Missing value path after --values");
          String path = recipe.getCommand().get(nextIdx);
          // Assert that next arg is not empty
          assertFalse(path.isEmpty(), "Value path should not be empty");
          // Assert that next arg is not --values or other --option
          assertFalse(path.startsWith("-"), "Path to values file must be provided");
        }
      }
    }
  }

  @Test
  public void buildBakeRecipeDoesNotIncludeValuesWhenNotSet() throws IOException {
    ArtifactDownloader artifactDownloader = mock(ArtifactDownloader.class);
    RoscoHelmfileConfigurationProperties helmfileConfigurationProperties =
        new RoscoHelmfileConfigurationProperties();
    HelmfileTemplateUtils helmfileTemplateUtils =
        new HelmfileTemplateUtils(
            artifactDownloader,
            Optional.empty(),
            artifactStoreConfig,
            helmfileConfigurationProperties,
            new YamlHelper(new YamlParserProperties()));

    HelmfileBakeManifestRequest request = new HelmfileBakeManifestRequest();
    Artifact artifact = Artifact.builder().build();
    request.setInputArtifacts(Collections.singletonList(artifact));
    request.setOverrides(Collections.emptyMap());

    try (BakeManifestEnvironment env = BakeManifestEnvironment.create()) {
      BakeRecipe recipe = helmfileTemplateUtils.buildBakeRecipe(env, request);
      assertFalse(recipe.getCommand().contains("--values"));
    }
  }

  @Test
  public void buildBakeRecipeRejectsHooks(@TempDir Path tempDir) throws IOException {
    addFile(
        tempDir,
        "helmfile.yaml",
        "hooks:\n"
            + "  - events: [\"prepare\"]\n"
            + "    command: \"/bin/sh\"\n"
            + "    args: [\"-c\", \"echo pwned\"]\n"
            + "releases:\n"
            + "  - name: test\n"
            + "    namespace: namespace\n"
            + "    chart: Chart.yaml\n");

    ArtifactDownloader artifactDownloader = mock(ArtifactDownloader.class);
    RoscoHelmfileConfigurationProperties helmfileConfigurationProperties =
        new RoscoHelmfileConfigurationProperties();
    HelmfileTemplateUtils helmfileTemplateUtils =
        new HelmfileTemplateUtils(
            artifactDownloader,
            Optional.empty(),
            artifactStoreConfig,
            helmfileConfigurationProperties,
            new YamlHelper(new YamlParserProperties()));

    HelmfileBakeManifestRequest request = new HelmfileBakeManifestRequest();
    Artifact artifact =
        Artifact.builder().type("git/repo").reference("https://github.com/some/repo.git").build();
    when(artifactDownloader.downloadArtifact(artifact)).thenReturn(makeTarball(tempDir));
    request.setInputArtifacts(Collections.singletonList(artifact));
    request.setOverrides(Collections.emptyMap());

    try (BakeManifestEnvironment env = BakeManifestEnvironment.create()) {
      IllegalArgumentException thrown =
          assertThrows(
              IllegalArgumentException.class,
              () -> helmfileTemplateUtils.buildBakeRecipe(env, request));
      assertThat(thrown.getMessage()).contains("hooks");
    }
  }

  @Test
  public void buildBakeRecipeRejectsHelmDefaultsPostRenderers(@TempDir Path tempDir)
      throws IOException {
    addFile(
        tempDir,
        "helmfile.yaml",
        "helmDefaults:\n"
            + "  postRenderers:\n"
            + "    - binaryPath: \"/bin/sh\"\n"
            + "      args: [\"-c\", \"echo pwned\"]\n"
            + "releases:\n"
            + "  - name: test\n"
            + "    namespace: namespace\n"
            + "    chart: Chart.yaml\n");

    ArtifactDownloader artifactDownloader = mock(ArtifactDownloader.class);
    RoscoHelmfileConfigurationProperties helmfileConfigurationProperties =
        new RoscoHelmfileConfigurationProperties();
    HelmfileTemplateUtils helmfileTemplateUtils =
        new HelmfileTemplateUtils(
            artifactDownloader,
            Optional.empty(),
            artifactStoreConfig,
            helmfileConfigurationProperties,
            new YamlHelper(new YamlParserProperties()));

    HelmfileBakeManifestRequest request = new HelmfileBakeManifestRequest();
    Artifact artifact =
        Artifact.builder().type("git/repo").reference("https://github.com/some/repo.git").build();
    when(artifactDownloader.downloadArtifact(artifact)).thenReturn(makeTarball(tempDir));
    request.setInputArtifacts(Collections.singletonList(artifact));
    request.setOverrides(Collections.emptyMap());

    try (BakeManifestEnvironment env = BakeManifestEnvironment.create()) {
      IllegalArgumentException thrown =
          assertThrows(
              IllegalArgumentException.class,
              () -> helmfileTemplateUtils.buildBakeRecipe(env, request));
      assertThat(thrown.getMessage()).contains("postRenderers");
    }
  }

  @Test
  public void buildBakeRecipeRejectsPostRendererArgFlag(@TempDir Path tempDir) throws IOException {
    addFile(
        tempDir,
        "helmfile.yaml",
        "releases:\n"
            + "  - name: test\n"
            + "    namespace: namespace\n"
            + "    chart: Chart.yaml\n"
            + "    args: [\"--post-renderer=/tmp/evil.sh\"]\n");

    ArtifactDownloader artifactDownloader = mock(ArtifactDownloader.class);
    RoscoHelmfileConfigurationProperties helmfileConfigurationProperties =
        new RoscoHelmfileConfigurationProperties();
    HelmfileTemplateUtils helmfileTemplateUtils =
        new HelmfileTemplateUtils(
            artifactDownloader,
            Optional.empty(),
            artifactStoreConfig,
            helmfileConfigurationProperties,
            new YamlHelper(new YamlParserProperties()));

    HelmfileBakeManifestRequest request = new HelmfileBakeManifestRequest();
    Artifact artifact =
        Artifact.builder().type("git/repo").reference("https://github.com/some/repo.git").build();
    when(artifactDownloader.downloadArtifact(artifact)).thenReturn(makeTarball(tempDir));
    request.setInputArtifacts(Collections.singletonList(artifact));
    request.setOverrides(Collections.emptyMap());

    try (BakeManifestEnvironment env = BakeManifestEnvironment.create()) {
      IllegalArgumentException thrown =
          assertThrows(
              IllegalArgumentException.class,
              () -> helmfileTemplateUtils.buildBakeRecipe(env, request));
      assertThat(thrown.getMessage()).contains("--post-renderer");
    }
  }

  @Test
  public void buildBakeRecipeRejectsHooksInLocalBase(@TempDir Path tempDir) throws IOException {
    addFile(
        tempDir,
        "helmfile.yaml",
        "bases:\n"
            + "  - base.yaml\n"
            + "releases:\n"
            + "  - name: test\n"
            + "    chart: Chart.yaml\n");
    addFile(
        tempDir,
        "base.yaml",
        "hooks:\n"
            + "  - events: [\"prepare\"]\n"
            + "    command: \"/bin/sh\"\n"
            + "    args: [\"-c\", \"echo pwned\"]\n");

    ArtifactDownloader artifactDownloader = mock(ArtifactDownloader.class);
    RoscoHelmfileConfigurationProperties helmfileConfigurationProperties =
        new RoscoHelmfileConfigurationProperties();
    HelmfileTemplateUtils helmfileTemplateUtils =
        new HelmfileTemplateUtils(
            artifactDownloader,
            Optional.empty(),
            artifactStoreConfig,
            helmfileConfigurationProperties,
            new YamlHelper(new YamlParserProperties()));

    HelmfileBakeManifestRequest request = new HelmfileBakeManifestRequest();
    Artifact artifact =
        Artifact.builder().type("git/repo").reference("https://github.com/some/repo.git").build();
    when(artifactDownloader.downloadArtifact(artifact)).thenReturn(makeTarball(tempDir));
    request.setInputArtifacts(Collections.singletonList(artifact));
    request.setOverrides(Collections.emptyMap());

    try (BakeManifestEnvironment env = BakeManifestEnvironment.create()) {
      IllegalArgumentException thrown =
          assertThrows(
              IllegalArgumentException.class,
              () -> helmfileTemplateUtils.buildBakeRecipe(env, request));
      assertThat(thrown.getMessage()).contains("hooks");
    }
  }

  @Test
  public void buildBakeRecipeAllowsHooksWhenConfiguredToAllowThem(@TempDir Path tempDir)
      throws IOException {
    addFile(
        tempDir,
        "helmfile.yaml",
        "hooks:\n"
            + "  - events: [\"prepare\"]\n"
            + "    command: \"/bin/sh\"\n"
            + "    args: [\"-c\", \"echo ok\"]\n"
            + "releases:\n"
            + "  - name: test\n"
            + "    namespace: namespace\n"
            + "    chart: Chart.yaml\n");

    ArtifactDownloader artifactDownloader = mock(ArtifactDownloader.class);
    RoscoHelmfileConfigurationProperties helmfileConfigurationProperties =
        new RoscoHelmfileConfigurationProperties();
    helmfileConfigurationProperties.setAllowHooksAndPostRenderers(true);
    HelmfileTemplateUtils helmfileTemplateUtils =
        new HelmfileTemplateUtils(
            artifactDownloader,
            Optional.empty(),
            artifactStoreConfig,
            helmfileConfigurationProperties,
            new YamlHelper(new YamlParserProperties()));

    HelmfileBakeManifestRequest request = new HelmfileBakeManifestRequest();
    Artifact artifact =
        Artifact.builder().type("git/repo").reference("https://github.com/some/repo.git").build();
    when(artifactDownloader.downloadArtifact(artifact)).thenReturn(makeTarball(tempDir));
    request.setInputArtifacts(Collections.singletonList(artifact));
    request.setOverrides(Collections.emptyMap());

    try (BakeManifestEnvironment env = BakeManifestEnvironment.create()) {
      BakeRecipe recipe = helmfileTemplateUtils.buildBakeRecipe(env, request);
      assertThat(recipe.getCommand()).isNotEmpty();
    }
  }

  /**
   * Builds a hostile helmfile.yaml whose {@code hooks:} declaration is buried past SnakeYAML's
   * default 50-level nesting limit, by wrapping it in nested single-key maps. The content is still
   * valid, parseable YAML overall - only the specific mapping containing {@code hooks:} is deeply
   * nested - so helmfile's own (unlimited-depth) parser will read it and run the hook, even though
   * rosco's guard fails to descend far enough to see it.
   */
  private String hooksBuriedPastNestingLimit(int depth) {
    StringBuilder sb = new StringBuilder();
    sb.append("releases:\n  - name: test\n    chart: Chart.yaml\n");
    for (int i = 0; i < depth; i++) {
      sb.append("  ".repeat(i)).append("nested").append(i).append(":\n");
    }
    sb.append("  ".repeat(depth)).append("hooks:\n");
    sb.append("  ".repeat(depth)).append("  - events: [\"prepare\"]\n");
    sb.append("  ".repeat(depth)).append("    command: \"/bin/sh\"\n");
    sb.append("  ".repeat(depth)).append("    args: [\"-c\", \"echo pwned\"]\n");
    return sb.toString();
  }

  private HelmfileTemplateUtils newHelmfileTemplateUtils(
      ArtifactDownloader artifactDownloader,
      RoscoHelmfileConfigurationProperties helmfileConfigurationProperties) {
    return new HelmfileTemplateUtils(
        artifactDownloader,
        Optional.empty(),
        artifactStoreConfig,
        helmfileConfigurationProperties,
        new YamlHelper(new YamlParserProperties()));
  }

  private HelmfileBakeManifestRequest requestForTarball(
      ArtifactDownloader artifactDownloader, Path tempDir) throws IOException {
    HelmfileBakeManifestRequest request = new HelmfileBakeManifestRequest();
    Artifact artifact =
        Artifact.builder().type("git/repo").reference("https://github.com/some/repo.git").build();
    when(artifactDownloader.downloadArtifact(artifact)).thenReturn(makeTarball(tempDir));
    request.setInputArtifacts(Collections.singletonList(artifact));
    request.setOverrides(Collections.emptyMap());
    return request;
  }

  @Test
  public void buildBakeRecipeDoesNotCatchHooksBuriedPastNestingLimit(@TempDir Path tempDir)
      throws IOException {
    // Demonstrates the gap: SnakeYAML's default nestingDepthLimit is 50, but helmfile's own Go
    // YAML parser has no such limit, so content nested past 50 levels is silently skipped by
    // rosco's guard (caught as a RuntimeException and logged at debug) while helmfile itself
    // still parses and executes the buried hooks.
    addFile(tempDir, "helmfile.yaml", hooksBuriedPastNestingLimit(60));

    ArtifactDownloader artifactDownloader = mock(ArtifactDownloader.class);
    HelmfileTemplateUtils helmfileTemplateUtils =
        newHelmfileTemplateUtils(artifactDownloader, new RoscoHelmfileConfigurationProperties());
    HelmfileBakeManifestRequest request = requestForTarball(artifactDownloader, tempDir);

    try (BakeManifestEnvironment env = BakeManifestEnvironment.create()) {
      // Today this does NOT throw, because the guard's YAML parse fails on content past the
      // nesting limit and the failure is swallowed rather than rejected. Once the guard is fixed
      // to fail closed, this must throw instead.
      IllegalArgumentException thrown =
          assertThrows(
              IllegalArgumentException.class,
              () -> helmfileTemplateUtils.buildBakeRecipe(env, request));
      assertThat(thrown.getMessage()).contains("hooks");
    }
  }

  @Test
  public void buildBakeRecipeRejectsUnparseableHelmfileContent(@TempDir Path tempDir)
      throws IOException {
    // Go template control-flow blocks (e.g. "{{- if }}") are not valid YAML on their own, even
    // though helmfile's own templating pass resolves them before parsing. A hostile helmfile.yaml
    // can hide hooks/postRenderers inside such a block so that SnakeYAML throws while parsing it,
    // which today causes the guard to skip validation entirely (fail open) instead of refusing to
    // bake content it could not fully inspect.
    addFile(
        tempDir,
        "helmfile.yaml",
        "{{- if eq .Environment.Name \"prod\" }}\n"
            + "hooks:\n"
            + "  - events: [\"prepare\"]\n"
            + "    command: \"/bin/sh\"\n"
            + "    args: [\"-c\", \"echo pwned\"]\n"
            + "{{- end }}\n"
            + "releases:\n"
            + "  - name: test\n"
            + "    chart: Chart.yaml\n");

    ArtifactDownloader artifactDownloader = mock(ArtifactDownloader.class);
    HelmfileTemplateUtils helmfileTemplateUtils =
        newHelmfileTemplateUtils(artifactDownloader, new RoscoHelmfileConfigurationProperties());
    HelmfileBakeManifestRequest request = requestForTarball(artifactDownloader, tempDir);

    try (BakeManifestEnvironment env = BakeManifestEnvironment.create()) {
      // Today this does NOT throw either: SnakeYAML rejects the "{{- if }}" as invalid YAML, and
      // the guard swallows that parse failure instead of refusing to bake unparseable content.
      assertThrows(
          IllegalArgumentException.class,
          () -> helmfileTemplateUtils.buildBakeRecipe(env, request));
    }
  }

  @Test
  public void buildBakeRecipeRejectsHooksInLocalHelmfilesEntry(@TempDir Path tempDir)
      throws IOException {
    // helmfile's "helmfiles:" key nests other complete helmfile.yaml state files (distinct from
    // "bases:", which merges YAML fragments). The guard never recurses into "helmfiles:" today.
    addFile(
        tempDir,
        "helmfile.yaml",
        "helmfiles:\n" + "  - sub-helmfile.yaml\n" + "releases:\n" + "  - name: test\n");
    addFile(
        tempDir,
        "sub-helmfile.yaml",
        "hooks:\n"
            + "  - events: [\"prepare\"]\n"
            + "    command: \"/bin/sh\"\n"
            + "    args: [\"-c\", \"echo pwned\"]\n"
            + "releases:\n"
            + "  - name: test-sub\n"
            + "    chart: Chart.yaml\n");

    ArtifactDownloader artifactDownloader = mock(ArtifactDownloader.class);
    HelmfileTemplateUtils helmfileTemplateUtils =
        newHelmfileTemplateUtils(artifactDownloader, new RoscoHelmfileConfigurationProperties());
    HelmfileBakeManifestRequest request = requestForTarball(artifactDownloader, tempDir);

    try (BakeManifestEnvironment env = BakeManifestEnvironment.create()) {
      // Today this does NOT throw, since "helmfiles:" entries are never inspected.
      IllegalArgumentException thrown =
          assertThrows(
              IllegalArgumentException.class,
              () -> helmfileTemplateUtils.buildBakeRecipe(env, request));
      assertThat(thrown.getMessage()).contains("hooks");
    }
  }

  @Test
  public void buildBakeRecipeRejectsRemoteBaseByDefault(@TempDir Path tempDir) throws IOException {
    // Remote/templated "bases:" are currently only log.warn'd and skipped, never rejected, so
    // rosco bakes them without ever inspecting their content for hooks/postRenderers.
    addFile(
        tempDir,
        "helmfile.yaml",
        "bases:\n"
            + "  - https://evil.example.com/base.yaml\n"
            + "releases:\n"
            + "  - name: test\n"
            + "    chart: Chart.yaml\n");

    ArtifactDownloader artifactDownloader = mock(ArtifactDownloader.class);
    HelmfileTemplateUtils helmfileTemplateUtils =
        newHelmfileTemplateUtils(artifactDownloader, new RoscoHelmfileConfigurationProperties());
    HelmfileBakeManifestRequest request = requestForTarball(artifactDownloader, tempDir);

    try (BakeManifestEnvironment env = BakeManifestEnvironment.create()) {
      // Today this does NOT throw: unresolvable references are skipped with a warning rather than
      // rejected, so remote content of unknown trustworthiness/content is baked unchecked.
      IllegalArgumentException thrown =
          assertThrows(
              IllegalArgumentException.class,
              () -> helmfileTemplateUtils.buildBakeRecipe(env, request));
      assertThat(thrown.getMessage()).contains("remote");
    }
  }

  @Test
  public void buildBakeRecipeDisablesInsecureHelmfileFeaturesByDefault(@TempDir Path tempDir)
      throws IOException {
    // hooks:/postRenderers: are guarded above by static YAML inspection, but helmfile's
    // exec/envExec/readFile/readDir/readDirEntries template functions, and its fetching of remote
    // bases:/helmfiles:/values:/chart repos, are neither expressible as a YAML key rosco can look
    // for nor blocked by that guard. Assert the subprocess env vars that gate those features off
    // are set by default.
    addFile(tempDir, "helmfile.yaml", "releases:\n  - name: test\n    chart: Chart.yaml\n");

    ArtifactDownloader artifactDownloader = mock(ArtifactDownloader.class);
    HelmfileTemplateUtils helmfileTemplateUtils =
        newHelmfileTemplateUtils(artifactDownloader, new RoscoHelmfileConfigurationProperties());
    HelmfileBakeManifestRequest request = requestForTarball(artifactDownloader, tempDir);

    try (BakeManifestEnvironment env = BakeManifestEnvironment.create()) {
      BakeRecipe recipe = helmfileTemplateUtils.buildBakeRecipe(env, request);
      assertThat(recipe.getEnv())
          .containsEntry("HELMFILE_DISABLE_HOOKS", "true")
          .containsEntry("HELMFILE_DISABLE_INSECURE_FEATURES", "true");
    }
  }

  @Test
  public void buildBakeRecipeLeavesInsecureHelmfileFeaturesEnabledWhenOptedIn(@TempDir Path tempDir)
      throws IOException {
    // Operators who trust a helmfile source can opt back into hooks/postRenderers/exec/remote
    // fetching wholesale via the same allowHooksAndPostRenderers flag.
    addFile(tempDir, "helmfile.yaml", "releases:\n  - name: test\n    chart: Chart.yaml\n");

    RoscoHelmfileConfigurationProperties helmfileConfigurationProperties =
        new RoscoHelmfileConfigurationProperties();
    helmfileConfigurationProperties.setAllowHooksAndPostRenderers(true);
    ArtifactDownloader artifactDownloader = mock(ArtifactDownloader.class);
    HelmfileTemplateUtils helmfileTemplateUtils =
        newHelmfileTemplateUtils(artifactDownloader, helmfileConfigurationProperties);
    HelmfileBakeManifestRequest request = requestForTarball(artifactDownloader, tempDir);

    try (BakeManifestEnvironment env = BakeManifestEnvironment.create()) {
      BakeRecipe recipe = helmfileTemplateUtils.buildBakeRecipe(env, request);
      assertThat(recipe.getEnv()).isEmpty();
    }
  }
}
