package com.netflix.spinnaker.clouddriver.artifacts.docker;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import com.netflix.spinnaker.kork.client.ServiceClientProvider;
import com.netflix.spinnaker.kork.docker.model.DockerBearerToken;
import com.netflix.spinnaker.kork.docker.model.DockerRegistryTags;
import com.netflix.spinnaker.kork.docker.service.DockerBearerTokenService;
import com.netflix.spinnaker.kork.docker.service.DockerRegistryClient;
import com.netflix.spinnaker.kork.docker.service.RegistryService;
import com.netflix.spinnaker.kork.retrofit.ErrorHandlingExecutorCallAdapterFactory;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import okhttp3.OkHttpClient;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junitpioneer.jupiter.TempDirectory;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import retrofit2.Retrofit;
import retrofit2.converter.jackson.JacksonConverterFactory;

@ExtendWith(TempDirectory.class)
public class HelmOciDockerArtifactCredentialsTest {

  @RegisterExtension
  static WireMockExtension wmDockerRegistry =
      WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

  static RegistryService dockerRegistryService;

  @Mock private DockerBearerTokenService dockerBearerTokenService;

  @Mock private DockerRegistryClient dockerRegistryClient;

  @Mock private HelmOciFileSystem helmOciFileSystem;

  @Mock private ServiceClientProvider serviceClientProvider;

  private HelmOciDockerArtifactCredentials credentials;
  private Path tempDir;

  @BeforeEach
  public void init(@TempDirectory.TempDir Path tempDir) throws JsonProcessingException {
    MockitoAnnotations.openMocks(this);
    this.tempDir = tempDir;

    // Setup bearer token
    DockerBearerToken bearerToken = new DockerBearerToken();
    bearerToken.setToken("someToken");
    bearerToken.setAccessToken("someToken");
    when(dockerBearerTokenService.getToken(anyString())).thenReturn(bearerToken);

    // Setup registry service
    dockerRegistryService = buildService(RegistryService.class, wmDockerRegistry.baseUrl());
    dockerRegistryClient =
        new DockerRegistryClient(
            wmDockerRegistry.baseUrl(), 5, "", "", dockerRegistryService, dockerBearerTokenService);

    // Create account
    HelmOciDockerArtifactAccount account =
        HelmOciDockerArtifactAccount.builder()
            .name("test-helm-oci-account")
            .address(wmDockerRegistry.baseUrl())
            .helmOciRepositories(List.of("test-repo", "another-repo/chart"))
            .build();

    // Create credentials with mocked dependencies
    credentials =
        new HelmOciDockerArtifactCredentials(
            account, new OkHttpClient(), helmOciFileSystem, serviceClientProvider);
  }

  private static <T> T buildService(Class<T> type, String baseUrl) {
    return new Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(new OkHttpClient())
        .addCallAdapterFactory(ErrorHandlingExecutorCallAdapterFactory.getInstance())
        .addConverterFactory(JacksonConverterFactory.create())
        .build()
        .create(type);
  }

  @Test
  void testGetType() {
    assertEquals("artifacts-helm-oci", credentials.getType());
  }

  @Test
  void testGetArtifactNames() {
    List<String> names = credentials.getArtifactNames();
    assertEquals(List.of("test-repo", "another-repo/chart"), names);
  }

  @Test
  void testGetArtifactVersions() {
    // Setup
    String artifactName = "test-repo";
    DockerRegistryTags tags = mock(DockerRegistryTags.class);
    when(tags.getTags()).thenReturn(List.of("1.0.0", "1.1.0", "2.0.0"));

    // Mock the WireMock server to return tags
    wmDockerRegistry.stubFor(
        get(urlPathMatching("/v2/" + artifactName + "/tags/list"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        "{\"name\":\""
                            + artifactName
                            + "\",\"tags\":[\"1.0.0\",\"1.1.0\",\"2.0.0\"]}")));

    // Execute
    List<String> versions = credentials.getArtifactVersions(artifactName);

    // Verify
    assertNotNull(versions);
    assertEquals(3, versions.size());
    assertTrue(versions.contains("1.0.0"));
    assertTrue(versions.contains("1.1.0"));
    assertTrue(versions.contains("2.0.0"));
  }

  @Test
  void testDownloadArtifact() throws IOException, InterruptedException {
    // Setup
    String artifactName = "test-repo/chart";
    String version = "1.0.0";
    Artifact artifact = Artifact.builder().name(artifactName).version(version).build();

    // Create test content
    String testContent = "test helm chart content";
    File tempFile = tempDir.resolve("test-content.tgz").toFile();
    FileUtils.writeStringToFile(tempFile, testContent, "UTF-8");

    // Setup file system mocks
    Path stagingPath = tempDir.resolve("test-repo-hash");
    Path outputFile = stagingPath.resolve("1.0.0.tar.gz");
    when(helmOciFileSystem.getLocalClonePath(artifactName, version)).thenReturn(stagingPath);
    when(helmOciFileSystem.tryTimedLock(artifactName, version)).thenReturn(true);
    when(helmOciFileSystem.canRetainClone()).thenReturn(false);

    // Mock WireMock to return the getManifest response
    wmDockerRegistry.stubFor(
        get(urlPathMatching("/v2/" + artifactName + "/manifests/" + version))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        "{\n"
                            + "    \"schemaVersion\": 2,\n"
                            + "    \"config\": {\n"
                            + "        \"mediaType\": \"application/vnd.cncf.helm.config.v1+json\",\n"
                            + "        \"digest\": \"sha256:ff1a5b6b030beb6d80ed19ee6573edf7de40803ef73c8502cffb6f2a10ba39d7\",\n"
                            + "        \"size\": 138\n"
                            + "    },\n"
                            + "    \"layers\": [\n"
                            + "        {\n"
                            + "            \"mediaType\": \"application/vnd.cncf.helm.chart.content.v1.tar+gzip\",\n"
                            + "            \"digest\": \"sha256:2fb8bd150e67835452c1c866379ddde29d1788b5f93950e44a2d3dbd71f2feda\",\n"
                            + "            \"size\": 4164\n"
                            + "        }\n"
                            + "    ],\n"
                            + "    \"annotations\": {\n"
                            + "        \"org.opencontainers.image.created\": \"2025-04-24T12:40:31+03:00\",\n"
                            + "        \"org.opencontainers.image.description\": \"A Helm chart for Kubernetes\",\n"
                            + "        \"org.opencontainers.image.title\": \"demo\",\n"
                            + "        \"org.opencontainers.image.version\": \"0.1.0\"\n"
                            + "    }\n"
                            + "}")));

    // Mock WireMock to return blob content
    wmDockerRegistry.stubFor(
        get(urlPathMatching(
                "/v2/"
                    + artifactName
                    + "/blobs/sha256:2fb8bd150e67835452c1c866379ddde29d1788b5f93950e44a2d3dbd71f2feda"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/octet-stream")
                    .withBody(testContent)));

    // Execute
    InputStream result = credentials.download(artifact);

    // Verify
    assertNotNull(result);
    byte[] resultBytes = result.readAllBytes();
    assertEquals(testContent, new String(resultBytes));

    // Verify file system operations
    wmDockerRegistry.verify(
        1, getRequestedFor(urlPathMatching("/v2/" + artifactName + "/blobs/.*")));
    verify(helmOciFileSystem).tryTimedLock(artifactName, version);
    verify(helmOciFileSystem).unlock(artifactName, version);
  }

  @Test
  void testDownloadWithExistingFile() throws IOException, InterruptedException {
    // Setup
    String artifactName = "test-repo";
    String version = "1.0.0";
    Artifact artifact = Artifact.builder().name(artifactName).version(version).build();

    // Create directory and file structure
    Path stagingPath = tempDir.resolve("test-repo-hash");
    Files.createDirectories(stagingPath);
    Path outputFile = stagingPath.resolve("1.0.0.tar.gz");
    String existingContent = "existing chart content";
    FileUtils.writeStringToFile(outputFile.toFile(), existingContent, "UTF-8");

    // Setup file system mocks
    when(helmOciFileSystem.getLocalClonePath(artifactName, version)).thenReturn(stagingPath);
    when(helmOciFileSystem.tryTimedLock(artifactName, version)).thenReturn(true);
    when(helmOciFileSystem.canRetainClone()).thenReturn(true);

    // Execute
    InputStream result = credentials.download(artifact);

    // Verify
    assertNotNull(result);
    String content = new String(result.readAllBytes());
    assertEquals(existingContent, content);

    // Verify WireMock was not called (existing file was used)
    wmDockerRegistry.verify(
        0, getRequestedFor(urlPathMatching("/v2/" + artifactName + "/blobs/.*")));

    // Verify file system operations
    verify(helmOciFileSystem).tryTimedLock(artifactName, version);
    verify(helmOciFileSystem).unlock(artifactName, version);
  }

  @Test
  void testDownloadFailsToAcquireLock() throws InterruptedException {
    // Setup
    String artifactName = "test-repo";
    String version = "1.0.0";
    Artifact artifact = Artifact.builder().name(artifactName).version(version).build();

    Path stagingPath = tempDir.resolve("test-repo-hash");

    // Mock file system operations to fail lock acquisition
    when(helmOciFileSystem.getLocalClonePath(artifactName, version)).thenReturn(stagingPath);
    when(helmOciFileSystem.tryTimedLock(artifactName, version)).thenReturn(false);
    when(helmOciFileSystem.getCloneWaitLockTimeoutSec()).thenReturn(60);

    // Execute and verify exception
    IOException exception = assertThrows(IOException.class, () -> credentials.download(artifact));
    assertTrue(exception.getMessage().contains("Timeout waiting to acquire file system lock"));
    assertEquals(
        "Timeout waiting to acquire file system lock for test-repo (version 1.0.0). Waited 60 seconds.",
        exception.getMessage());
  }

  @Test
  void testDownloadFailsWithRegistryException()
      throws RuntimeException, IOException, InterruptedException {
    // Setup
    String artifactName = "test-repo";
    String version = "1.0.0";
    Artifact artifact = Artifact.builder().name(artifactName).version(version).build();

    // Create test content
    String testContent = "test helm chart content";
    File tempFile = tempDir.resolve("test-content.tgz").toFile();
    FileUtils.writeStringToFile(tempFile, testContent, "UTF-8");

    // Setup file system mocks
    Path stagingPath = tempDir.resolve("test-repo-hash");
    Path outputFile = stagingPath.resolve("1.0.0.tar.gz");
    when(helmOciFileSystem.getLocalClonePath(artifactName, version)).thenReturn(stagingPath);
    when(helmOciFileSystem.tryTimedLock(artifactName, version)).thenReturn(true);
    when(helmOciFileSystem.canRetainClone()).thenReturn(false);

    // Mock WireMock to return the getManifest response
    wmDockerRegistry.stubFor(
        get(urlPathMatching("/v2/" + artifactName + "/manifests/" + version))
            .willReturn(
                aResponse()
                    .withStatus(404)
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        "{\n"
                            + "    \"errors\": [\n"
                            + "        {\n"
                            + "            \"code\": \"MANIFEST_UNKNOWN\",\n"
                            + "            \"message\": \"manifest unknown\",\n"
                            + "            \"detail\": \"unknown tag=1.0.0\"\n"
                            + "        }\n"
                            + "    ]\n"
                            + "}")));

    // Execute and verify
    Exception exception =
        assertThrows(RuntimeException.class, () -> credentials.download(artifact));
    assertTrue(
        exception.getMessage().contains("Failed to download artifact blob from registry"),
        "Expected exception message to contain error about registry but was: "
            + exception.getMessage());
    wmDockerRegistry.verify(
        0, getRequestedFor(urlPathMatching("/v2/" + artifactName + "/blobs/.*")));
    verify(helmOciFileSystem).tryTimedLock(artifactName, version);
    verify(helmOciFileSystem).unlock(artifactName, version);
  }
}
