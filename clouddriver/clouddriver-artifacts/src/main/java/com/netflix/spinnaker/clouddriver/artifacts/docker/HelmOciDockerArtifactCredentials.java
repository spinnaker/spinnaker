package com.netflix.spinnaker.clouddriver.artifacts.docker;

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.common.collect.ImmutableList;
import com.netflix.spinnaker.clouddriver.artifacts.config.ArtifactCredentials;
import com.netflix.spinnaker.clouddriver.artifacts.config.BaseHttpArtifactCredentials;
import com.netflix.spinnaker.kork.annotations.NonnullByDefault;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import com.netflix.spinnaker.kork.client.ServiceClientProvider;
import com.netflix.spinnaker.kork.docker.service.DockerRegistryClient;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.ResponseBody;
import org.apache.commons.io.FileUtils;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;

@Slf4j
@NonnullByDefault
public class HelmOciDockerArtifactCredentials
    extends BaseHttpArtifactCredentials<HelmOciDockerArtifactAccount>
    implements ArtifactCredentials {
  public static final String CREDENTIALS_TYPE = "artifacts-helm-oci";
  public static final String TYPE = "helm/image";

  @Getter private final String name;
  @Getter private final ImmutableList<String> types = ImmutableList.of(TYPE);

  private final HelmOciDockerArtifactAccount account;
  private final HelmChartsFileSystem helmChartsFileSystem;

  @Autowired private ServiceClientProvider serviceClientProvider;

  private DockerRegistryClient client;

  public HelmOciDockerArtifactCredentials(
      HelmOciDockerArtifactAccount account,
      OkHttpClient okHttpClient,
      HelmChartsFileSystem helmChartsFileSystem,
      ServiceClientProvider serviceClientProvider) {
    super(okHttpClient, account);
    this.account = account;
    this.name = this.account.getName();
    this.helmChartsFileSystem = helmChartsFileSystem;
    this.serviceClientProvider = serviceClientProvider;
    this.client = account.getDockerRegistryClient(serviceClientProvider);
  }

  @Override
  public String getType() {
    return CREDENTIALS_TYPE;
  }

  @Override
  public InputStream download(Artifact artifact) throws IOException {
    String helmArtifactName = artifact.getName();
    String helmArtifactVersion = artifact.getVersion();

    Path stagingPath =
        helmChartsFileSystem.getLocalClonePath(helmArtifactName, helmArtifactVersion);

    Path outputFile = Paths.get(stagingPath.toString(), helmArtifactVersion + ".tar.gz");
    try {
      return getLockedInputStream(artifact, outputFile);
    } catch (InterruptedException e) {
      throw new IOException(
          "Interrupted while waiting to acquire file system lock for "
              + helmArtifactName
              + " (version "
              + helmArtifactVersion
              + ").",
          e);
    }
  }

  @NotNull
  private FileInputStream getLockedInputStream(Artifact artifact, Path outputFile)
      throws InterruptedException, IOException {

    String helmArtifactName = artifact.getName();
    String helmArtifactVersion = artifact.getVersion();

    if (helmChartsFileSystem.tryTimedLock(helmArtifactName, helmArtifactVersion)) {
      try {
        return getInputStream(artifact, outputFile);
      } finally {
        if (!helmChartsFileSystem.canRetainClone()) {
          log.debug(
              "Deleting helm chart for {} (version {})", helmArtifactName, helmArtifactVersion);
          FileUtils.deleteDirectory(outputFile.getParent().toFile());
        }
        helmChartsFileSystem.unlock(helmArtifactName, helmArtifactVersion);
      }

    } else {
      throw new IOException(
          "Timeout waiting to acquire file system lock for "
              + helmArtifactName
              + " (version "
              + helmArtifactVersion
              + "). Waited "
              + helmChartsFileSystem.getCloneWaitLockTimeoutSec()
              + " seconds.");
    }
  }

  @NotNull
  private FileInputStream getInputStream(Artifact artifact, Path outputFile) throws IOException {

    if (!outputFile.toFile().exists()) {
      String helmArtifactName = artifact.getName();
      log.info("Creating archive for helm/oci {}", helmArtifactName);
      Path repoPath = Paths.get(outputFile.getParent().toString());
      if (!repoPath.toFile().mkdirs()) {
        throw new IOException("Unable to create directory " + outputFile.toString());
      }
      try {
        ResponseBody response = client.downloadBlob(artifact.getName(), artifact.getVersion());
        if (response == null) {
          throw new RuntimeException("No response body received from registry");
        }

        // Write the input stream to the output file (should be .tgz)
        try (InputStream inputStream = response.byteStream();
            FileOutputStream outputStream = new FileOutputStream(outputFile.toFile())) {
          byte[] buffer = new byte[8192];
          int bytesRead;
          while ((bytesRead = inputStream.read(buffer)) != -1) {
            outputStream.write(buffer, 0, bytesRead);
          }
        }
      } catch (Exception e) {
        log.error("Failed to download artifact blob from registry", e);
        throw new RuntimeException("Failed to download artifact blob from registry", e);
      }
    }

    log.info(
        "Using cached archive for helm/oci {} version {}",
        artifact.getName(),
        artifact.getVersion());
    return new FileInputStream(outputFile.toFile());
  }

  @Override
  public List<String> getArtifactNames() {
    return account.getHelmOciRepositories().stream().collect(toImmutableList());
  }

  @Override
  public List<String> getArtifactVersions(String artifactName) {
    return client.getTags(artifactName).getTags();
  }
}
