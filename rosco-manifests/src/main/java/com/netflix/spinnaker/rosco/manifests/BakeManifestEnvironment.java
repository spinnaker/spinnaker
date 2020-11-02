/*
 * Copyright 2019 Google, LLC
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

package com.netflix.spinnaker.rosco.manifests;

import static com.google.common.io.RecursiveDeleteOption.ALLOW_INSECURE;

import com.google.common.io.MoreFiles;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.annotation.ParametersAreNonnullByDefault;
import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;

@ParametersAreNonnullByDefault
public final class BakeManifestEnvironment implements AutoCloseable {
  private final Path stagingPath;

  private BakeManifestEnvironment(Path stagingPath) {
    this.stagingPath = stagingPath;
  }

  public static BakeManifestEnvironment create() throws IOException {
    Path stagingPath = Files.createTempDirectory("rosco-");
    return new BakeManifestEnvironment(stagingPath);
  }

  public Path resolvePath(String fileName) {
    return checkPath(stagingPath.resolve(fileName));
  }

  public Path resolvePath(Path fileName) {
    return checkPath(stagingPath.resolve(fileName));
  }

  @Override
  public void close() throws IOException {
    MoreFiles.deleteRecursively(stagingPath, ALLOW_INSECURE);
  }

  private Path checkPath(final Path path) {
    if (!path.normalize().startsWith(stagingPath)) {
      throw new IllegalStateException("Attempting to create a file outside of the staging path.");
    }
    return path;
  }

  /**
   * Download an artifact that's a compressed tarball, and extract the contents of the tarball into
   * this environment
   */
  public void downloadArtifactTarballAndExtract(
      ArtifactDownloader artifactDownloader, Artifact artifact) throws IOException {
    InputStream inputStream;
    try {
      inputStream = artifactDownloader.downloadArtifact(artifact);
    } catch (IOException e) {
      throw new IOException("Failed to download artifact: " + e.getMessage(), e);
    }

    try {
      extractArtifact(inputStream, resolvePath(""));
    } catch (IOException e) {
      throw new IOException("Failed to extract artifact: " + e.getMessage(), e);
    }
  }

  private static void extractArtifact(InputStream inputStream, Path outputPath) throws IOException {
    try (TarArchiveInputStream tarArchiveInputStream =
        new TarArchiveInputStream(
            new GzipCompressorInputStream(new BufferedInputStream(inputStream)))) {

      ArchiveEntry archiveEntry;
      while ((archiveEntry = tarArchiveInputStream.getNextEntry()) != null) {
        Path archiveEntryOutput = validateArchiveEntry(archiveEntry.getName(), outputPath);
        if (archiveEntry.isDirectory()) {
          if (!Files.exists(archiveEntryOutput)) {
            Files.createDirectory(archiveEntryOutput);
          }
        } else {
          Files.copy(tarArchiveInputStream, archiveEntryOutput);
        }
      }
    }
  }

  private static Path validateArchiveEntry(String archiveEntryName, Path outputPath) {
    Path entryPath = outputPath.resolve(archiveEntryName);
    if (!entryPath.normalize().startsWith(outputPath)) {
      throw new IllegalStateException("Attempting to create a file outside of the staging path.");
    }
    return entryPath;
  }
}
