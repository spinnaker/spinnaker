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

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ArchiveExtractorTest {

  @TempDir Path tempDir;

  @Test
  void shouldExtractZipArchivePreservingDirectoryStructure() throws IOException {
    byte[] zipContent =
        createTestZip(
            new String[] {"file1.txt", "dir/file2.txt", "dir/nested/file3.txt"},
            new String[] {"content1", "content2", "content3"});

    Path zipFile = tempDir.resolve("test.zip");
    Files.write(zipFile, zipContent);
    Path extractDir = tempDir.resolve("extracted");

    List<Path> result = ArchiveExtractor.extract(zipFile, extractDir);

    assertEquals(3, result.size());
    assertEquals("content1", Files.readString(extractDir.resolve("file1.txt")));
    assertEquals("content2", Files.readString(extractDir.resolve("dir/file2.txt")));
    assertEquals("content3", Files.readString(extractDir.resolve("dir/nested/file3.txt")));
  }

  @Test
  void shouldDetectZipFormatFromExtension() {
    assertEquals(ArchiveExtractor.Format.ZIP, ArchiveExtractor.detectFormat("file.zip"));
    assertEquals(ArchiveExtractor.Format.TAR, ArchiveExtractor.detectFormat("file.tar"));
    assertEquals(ArchiveExtractor.Format.TAR_GZ, ArchiveExtractor.detectFormat("file.tar.gz"));
    assertEquals(ArchiveExtractor.Format.TAR_GZ, ArchiveExtractor.detectFormat("file.tgz"));
  }

  @Test
  void shouldThrowExceptionForUnsupportedFormat() {
    assertThrows(IllegalArgumentException.class, () -> ArchiveExtractor.detectFormat("file.rar"));
  }

  @Test
  void shouldReturnRelativePathsOfExtractedFiles() throws IOException {
    byte[] zipContent = createTestZip(new String[] {"a.txt", "b/c.txt"}, new String[] {"a", "c"});

    Path zipFile = tempDir.resolve("test.zip");
    Files.write(zipFile, zipContent);
    Path extractDir = tempDir.resolve("extracted");

    List<Path> result = ArchiveExtractor.extract(zipFile, extractDir);

    assertTrue(result.stream().anyMatch(p -> p.toString().equals("a.txt")));
    assertTrue(result.stream().anyMatch(p -> p.toString().equals("b/c.txt")));
  }

  private byte[] createTestZip(String[] paths, String[] contents) throws IOException {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    try (ZipOutputStream zos = new ZipOutputStream(baos)) {
      for (int i = 0; i < paths.length; i++) {
        zos.putNextEntry(new ZipEntry(paths[i]));
        zos.write(contents[i].getBytes());
        zos.closeEntry();
      }
    }
    return baos.toByteArray();
  }
}
