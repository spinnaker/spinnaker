package com.netflix.spinnaker.gradle.publishing.artifactregistry;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.artifactregistry.v1beta1.ArtifactRegistryScopes;
import com.google.api.services.artifactregistry.v1beta1.model.Operation;
import com.google.auth.Credentials;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.artifactregistry.auth.DefaultCredentialProvider;
import com.google.cloud.storage.*;
import com.google.common.base.Stopwatch;
import com.google.common.base.Strings;
import com.google.common.io.ByteStreams;
import org.apache.tools.ant.filters.StringInputStream;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.RegularFile;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.TaskAction;

import javax.inject.Inject;
import java.io.IOException;
import java.nio.channels.ByteChannel;
import java.nio.channels.WritableByteChannel;
import java.nio.file.Files;
import java.security.GeneralSecurityException;
import java.util.concurrent.TimeUnit;

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
  void publishDeb() throws GeneralSecurityException, InterruptedException, IOException {
    Storage storage = StorageOptions.newBuilder().setCredentials(resolveCredentials()).build().getService();
    BlobId blobId = uploadDebToGcs(storage);
    Operation importOperation = importDebToArtifactRegistry(blobId);

    deleteDebFromGcs(storage, blobId);

    if (!operationIsDone(importOperation)) {
      throw new IOException("Operation timed out importing debian package to Artifact Registry.");
    } else if (getErrors(importOperation) != null) {
      throw new IOException(
        "Received an error importing debian package to Artifact Registry: " + getErrors(importOperation)
      );
    }
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
        ArtifactRegistryScopes.CLOUD_PLATFORM
      );
    }

    return new DefaultCredentialProvider().getCredential();
  }

  /**
   * Import the blob into Artifact Registry and return an Operation representing the import.
   *
   * <p>
   * If the Operation is not done, that means we timed out before finishing the import. The operation
   * should also be checked for errors.
   */
  private Operation importDebToArtifactRegistry(BlobId blobId) throws GeneralSecurityException, IOException,
    InterruptedException {

    ArtifactRegistryAlphaClient artifactRegistryClient = new ArtifactRegistryAlphaClient(
      GoogleNetHttpTransport.newTrustedTransport(), JacksonFactory.getDefaultInstance(), new HttpCredentialsAdapter(
        resolveCredentials()
      )
    );

    Operation operation = artifactRegistryClient.importArtifacts(
      repoProject.get(),
      location.get(),
      repository.get(),
      String.format("gs://%s/%s", blobId.getBucket(), blobId.getName())
    ).execute();

    Stopwatch timer = Stopwatch.createStarted();
    while (!operationIsDone(operation) && !operationTimedOut(timer)) {
      Thread.sleep(1000);
      operation = artifactRegistryClient.projects().locations().operations().get(operation.getName()).execute();
    }

    return operation;
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

  /** Checks for done, correctly handling a null result. */
  private boolean operationIsDone(Operation operation) {
    return Boolean.TRUE.equals(operation.getDone());
  }

  private boolean operationTimedOut(Stopwatch timer) {
    return timer.elapsed(TimeUnit.SECONDS) > aptImportTimeoutSeconds.get();
  }

  private Object getErrors(Operation operation) {
    return operation.getResponse().get("errors");
  }
}
