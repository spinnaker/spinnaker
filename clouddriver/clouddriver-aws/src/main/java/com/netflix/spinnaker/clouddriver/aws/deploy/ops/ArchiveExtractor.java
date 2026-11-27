/*
 * Copyright 2026 Wise, PLC.
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

package com.netflix.spinnaker.clouddriver.aws.deploy.ops;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.ArchiveInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;

/**
 * Utility class for extracting archive files (ZIP, TAR, TAR.GZ).
 *
 * <p>Supports extraction of common archive formats while preserving directory structure. Includes
 * protection against zip-slip vulnerabilities by validating that extracted paths remain within the
 * target directory.
 */
public class ArchiveExtractor {

  public enum Format {
    ZIP,
    TAR,
    TAR_GZ
  }

  private ArchiveExtractor() {}

  /**
   * Detects the archive format based on the filename extension.
   *
   * @param filename the name of the archive file
   * @return the detected format
   * @throws IllegalArgumentException if the format is not supported
   */
  public static Format detectFormat(String filename) {
    String lower = filename.toLowerCase(Locale.ROOT);
    if (lower.endsWith(".zip")) {
      return Format.ZIP;
    } else if (lower.endsWith(".tar.gz") || lower.endsWith(".tgz")) {
      return Format.TAR_GZ;
    } else if (lower.endsWith(".tar")) {
      return Format.TAR;
    }
    throw new IllegalArgumentException(
        "Unsupported archive format: "
            + filename
            + ". Supported formats: .zip, .tar, .tar.gz, .tgz");
  }

  /**
   * Extracts an archive file to the target directory.
   *
   * @param archivePath path to the archive file
   * @param targetDir directory to extract files into
   * @return list of relative paths of extracted files
   * @throws IOException if extraction fails
   */
  public static List<Path> extract(Path archivePath, Path targetDir) throws IOException {
    Format format = detectFormat(archivePath.getFileName().toString());
    try (InputStream is = Files.newInputStream(archivePath)) {
      return extract(is, format, targetDir);
    }
  }

  /**
   * Extracts an archive from an input stream to the target directory.
   *
   * <p>Creates the target directory if it doesn't exist. Preserves the directory structure from the
   * archive. Skips directory entries and only extracts files.
   *
   * @param inputStream the archive input stream
   * @param format the archive format
   * @param targetDir directory to extract files into
   * @return list of relative paths of extracted files
   * @throws IOException if extraction fails or if a zip-slip attack is detected
   */
  public static List<Path> extract(InputStream inputStream, Format format, Path targetDir)
      throws IOException {
    Files.createDirectories(targetDir);
    List<Path> extractedFiles = new ArrayList<>();

    try (ArchiveInputStream<?> archiveStream = createArchiveInputStream(inputStream, format)) {
      ArchiveEntry entry;
      while ((entry = archiveStream.getNextEntry()) != null) {
        if (entry.isDirectory()) {
          continue;
        }

        Path entryPath = targetDir.resolve(entry.getName()).normalize();

        // Security check: prevent zip slip vulnerability
        if (!entryPath.startsWith(targetDir)) {
          throw new IOException("Archive entry is outside of target directory: " + entry.getName());
        }

        Files.createDirectories(entryPath.getParent());

        try (OutputStream out = Files.newOutputStream(entryPath)) {
          archiveStream.transferTo(out);
        }

        extractedFiles.add(targetDir.relativize(entryPath));
      }
    }

    return extractedFiles;
  }

  private static ArchiveInputStream<?> createArchiveInputStream(InputStream input, Format format)
      throws IOException {
    BufferedInputStream buffered = new BufferedInputStream(input);
    return switch (format) {
      case ZIP -> new ZipArchiveInputStream(buffered);
      case TAR -> new TarArchiveInputStream(buffered);
      case TAR_GZ -> new TarArchiveInputStream(new GzipCompressorInputStream(buffered));
    };
  }
}
