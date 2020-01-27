package com.netflix.spinnaker.halyard.config.services.v1;

import com.amazonaws.util.IOUtils;
import com.netflix.spinnaker.halyard.config.config.v1.HalconfigDirectoryStructure;
import com.netflix.spinnaker.halyard.core.secrets.v1.SecretSessionManager;
import com.netflix.spinnaker.kork.configserver.CloudConfigResourceService;
import com.netflix.spinnaker.kork.configserver.ConfigFileService;
import com.netflix.spinnaker.kork.secrets.EncryptedSecret;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** Service for file operations. */
@Component
public class FileService {
  private final SecretSessionManager secretSessionManager;
  private final HalconfigDirectoryStructure halconfigDirectoryStructure;
  private final ConfigFileService configFileService;
  private final CloudConfigResourceService cloudConfigResourceService;

  @Autowired
  public FileService(
      SecretSessionManager secretSessionManager,
      HalconfigDirectoryStructure halconfigDirectoryStructure,
      ConfigFileService configFileService,
      CloudConfigResourceService cloudConfigResourceService) {
    this.secretSessionManager = secretSessionManager;
    this.halconfigDirectoryStructure = halconfigDirectoryStructure;
    this.configFileService = configFileService;
    this.cloudConfigResourceService = cloudConfigResourceService;
  }

  /**
   * Returns an absolute file path in the local file system resolved by this file reference,
   * retrieving the file from external systems if necessary.
   *
   * @param fileReference a file reference can be a secret, a config server resource or a path in
   *     the local file system.
   * @return an absolute path to the file, or null if the reference cannot be resolved to a local
   *     path.
   */
  public Path getLocalFilePath(String fileReference) {
    if (StringUtils.isEmpty(fileReference)) {
      return null;
    }
    if (CloudConfigResourceService.isCloudConfigResource(fileReference)) {
      return Paths.get(cloudConfigResourceService.getLocalPath(fileReference));
    }
    if (EncryptedSecret.isEncryptedSecret(fileReference)) {
      return Paths.get(secretSessionManager.decryptAsFile(fileReference));
    }

    return absolutePath(fileReference);
  }

  /**
   * Return the contents of a file as a string.
   *
   * @param fileReference a file reference can be a secret, a config server resource or a path in
   *     the local file system.
   * @return file contents.
   */
  public String getFileContents(String fileReference) throws IOException {
    byte[] contentBytes = getFileContentBytes(fileReference);
    if (contentBytes == null) {
      return null;
    }
    return new String(contentBytes);
  }

  /**
   * Return the contents of a file as a byte array.
   *
   * @param fileReference a file reference can be a secret, a config server resource or a path in
   *     the local file system.
   * @return file contents as bytes.
   */
  public byte[] getFileContentBytes(String fileReference) throws IOException {
    if (CloudConfigResourceService.isCloudConfigResource(fileReference)) {
      String localPath = cloudConfigResourceService.getLocalPath(fileReference);
      return configFileService.getContents(localPath).getBytes();
    }
    if (EncryptedSecret.isEncryptedSecret(fileReference)) {
      return secretSessionManager.decryptAsBytes(fileReference);
    }

    return readFromLocalFilesystem(fileReference);
  }

  /**
   * Indicates if the given file reference is for a remote (secret reference, config server) or
   * local file.
   *
   * @param fileReference to be checked.
   * @return true if it's a remote file.
   */
  public boolean isRemoteFile(String fileReference) {
    return CloudConfigResourceService.isCloudConfigResource(fileReference)
        || EncryptedSecret.isEncryptedFile(fileReference);
  }

  private byte[] readFromLocalFilesystem(String path) throws IOException {
    Path absolutePath = absolutePath(path);
    if (absolutePath == null) {
      throw new IOException(
          "Provided path: \"" + path + "\" cannot be resolved to a local absolute path.");
    }
    return IOUtils.toByteArray(new FileInputStream(absolutePath.toString()));
  }

  private Path absolutePath(String path) {
    if (StringUtils.isEmpty(path)) {
      return null;
    }
    Path filePath = Paths.get(path);
    if (!filePath.isAbsolute()) {
      filePath = Paths.get(halconfigDirectoryStructure.getHalconfigDirectory(), path);
    }
    return filePath;
  }
}
