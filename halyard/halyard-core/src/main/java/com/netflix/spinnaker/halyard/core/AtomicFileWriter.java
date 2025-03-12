/*
 * Copyright 2017 Google, Inc.
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
 *
 */

package com.netflix.spinnaker.halyard.core;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import static java.nio.file.StandardOpenOption.*;

import java.io.IOException;
import java.io.Writer;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class AtomicFileWriter {
  private final Path path;
  private final Path tmpPath;
  private Writer writer;

  public AtomicFileWriter(String path) throws IOException {
    this(FileSystems.getDefault().getPath(path));
  }

  public AtomicFileWriter(Path path) throws IOException {
    FileSystem defaultFileSystem = FileSystems.getDefault();
    this.path = path;
    path.getParent().toFile().mkdirs();
    String tmpDir = System.getProperty("java.io.tmpdir");
    this.tmpPath = defaultFileSystem.getPath(tmpDir, UUID.randomUUID().toString());
    this.writer = Files.newBufferedWriter(this.tmpPath, UTF_8, WRITE, APPEND, CREATE);
  }

  public void write(String contents) throws IOException {
    writer.write(contents);
  }

  public void commit() throws IOException {
    writer.close();
    writer = null;
    Files.move(tmpPath, path, REPLACE_EXISTING);
  }

  public void close() {
    if (writer != null) {
      try {
        writer.close();
      } catch (IOException e) {
        log.error("Failed to close file writer responsible for " + path.toString(), e);
        e.printStackTrace();
      }
    }
  }
}
