package com.netflix.spinnaker.gradle.publishing.artifactregistry;

import com.google.api.gax.longrunning.OperationFuture;
import com.google.auth.Credentials;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageException;
import com.google.cloud.storage.StorageOptions;
import com.google.common.base.Strings;
import com.google.common.io.ByteStreams;
import com.google.devtools.artifactregistry.v1.ArtifactRegistryClient;
import com.google.devtools.artifactregistry.v1.ArtifactRegistrySettings;
import com.google.devtools.artifactregistry.v1.ImportAptArtifactsGcsSource;
import com.google.devtools.artifactregistry.v1.ImportAptArtifactsMetadata;
import com.google.devtools.artifactregistry.v1.ImportAptArtifactsRequest;
import com.google.devtools.artifactregistry.v1.ImportAptArtifactsResponse;
import com.google.devtools.artifactregistry.v1.RepositoryName;
import java.io.IOException;
import java.nio.channels.ByteChannel;
import java.nio.channels.WritableByteChannel;
import java.nio.file.Files;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import javax.inject.Inject;
import org.apache.tools.ant.filters.StringInputStream;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.RegularFile;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.TaskAction;

class ArtifactRegistryDebPublishTask extends DefaultTask {

  private static final String GOOGLE_SERVICE_ACCT_JSON_ENV_VAR = "GAR_JSON_KEY";

  private Provider<String> uploadBucket;
  private Provider<String> repoProject;
  private Provider<String> location;
  private Provider<String> repository;
  private Provider<RegularFile> archiveFile;
  private Provider<Integer> aptImportTimeoutSeconds;

  @Inject
  public ArtifactRegistryDebPublishTask() {}

  @Input
  public Provider<String> getUploadBucket() {
    return uploadBucket;
  }

  public void setUploadBucket(Provider<String> uploadBucket) {
    this.uploadBucket = uploadBucket;
  }

  @Input
  public Provider<String> getRepoProject() {
    return repoProject;
  }

  public void setRepoProject(Provider<String> repoProject) {
    this.repoProject = repoProject;
  }

  @Input
  public Provider<String> getLocation() {
    return location;
  }

  public void setLocation(Provider<String> location) {
    this.location = location;
  }

  @Input
  public Provider<String> getRepository() {
    return repository;
  }

  public void setRepository(Provider<String> repository) {
    this.repository = repository;
  }

  @Input
  public Provider<Integer> getAptImportTimeoutSeconds() {
    return aptImportTimeoutSeconds;
  }

  public void setAptImportTimeoutSeconds(Provider<Integer> aptImportTimeout) {
    this.aptImportTimeoutSeconds = aptImportTimeout;
  }

  @InputFile
  public Provider<RegularFile> getArchiveFile() {
    return archiveFile;
  }

  public void setArchiveFile(Provider<RegularFile> archiveFile) {
    this.archiveFile = archiveFile;
  }

  @TaskAction
  void publishDeb() throws InterruptedException, IOException {
    Storage storage = StorageOptions.newBuilder().setCredentials(resolveCredentials()).build().getService();
    BlobId blobId = uploadDebToGcs(storage);
    importDebToArtifactRegistry(blobId);
    deleteDebFromGcs(storage, blobId);
  }

  private void deleteDebFromGcs(Storage storage, BlobId blobId) {
    try {
      storage.delete(blobId);
    } catch (StorageException e) {
      getProject().getLogger().warn("Error deleting deb from temp GCS storage", e);
    }
  }

  private Credentials resolveCredentials() throws IOException {
    String fromEnvironmentVar = System.getenv(GOOGLE_SERVICE_ACCT_JSON_ENV_VAR);

    if (!Strings.isNullOrEmpty(fromEnvironmentVar)) {
      return GoogleCredentials.fromStream(new StringInputStream(fromEnvironmentVar)).createScoped(
        "https://www.googleapis.com/auth/cloud-platform"
      );
    }

    return GoogleCredentials.getApplicationDefault().createScoped("https://www.googleapis.com/auth/cloud-platform");
  }

  /**
   * Import the blob into Artifact Registry.
   *
   * <p>
   * Blocks until the import completes or times out.
   */
  private void importDebToArtifactRegistry(BlobId blobId) throws IOException, InterruptedException {
    ArtifactRegistrySettings settings = ArtifactRegistrySettings.newBuilder().setCredentialsProvider(
      () -> resolveCredentials()
    ).build();

    try (ArtifactRegistryClient artifactRegistryClient = ArtifactRegistryClient.create(settings)) {
      RepositoryName parent = RepositoryName.of(repoProject.get(), location.get(), repository.get());

      ImportAptArtifactsGcsSource gcsSource = ImportAptArtifactsGcsSource.newBuilder().addUris(
        String.format("gs://%s/%s", blobId.getBucket(), blobId.getName())
      ).build();

      ImportAptArtifactsRequest request = ImportAptArtifactsRequest.newBuilder().setParent(parent.toString())
        .setGcsSource(gcsSource).build();

      OperationFuture<ImportAptArtifactsResponse, ImportAptArtifactsMetadata> operationFuture = artifactRegistryClient
        .importAptArtifactsAsync(request);

      try {
        operationFuture.get(aptImportTimeoutSeconds.get(), TimeUnit.SECONDS);
      } catch (ExecutionException e) {
        throw new IOException("Error importing debian package to Artifact Registry: " + e.getMessage(), e);
      } catch (TimeoutException e) {
        throw new IOException("Operation timed out importing debian package to Artifact Registry.", e);
      }
    }
  }

  private BlobId uploadDebToGcs(Storage storage) throws IOException {
    BlobId blobId = BlobId.of(uploadBucket.get(), archiveFile.get().getAsFile().getName());
    BlobInfo blobInfo = BlobInfo.newBuilder(blobId).build();
    try (ByteChannel fileChannel = Files.newByteChannel(archiveFile.get().getAsFile().toPath());
      WritableByteChannel gcsChannel = storage.writer(blobInfo)) {
      ByteStreams.copy(fileChannel, gcsChannel);
    }
    return blobId;
  }
}
