/*
 * Copyright 2017 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.netflix.spinnaker.clouddriver.artifacts;

import java.io.*;
import java.util.Stack;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.utils.IOUtils;

public class ArtifactUtils {
  public static final String GCE_IMAGE_TYPE = "gce/image";

  public static void untarStreamToPath(InputStream inputStream, String basePath)
      throws IOException {
    class DirectoryTimestamp {
      public DirectoryTimestamp(File d, long m) {
        directory = d;
        millis = m;
      }

      public File directory;
      public long millis;
    }
    // Directories come in hierarchical order within the stream, but
    // we need to set their timestamps after their children have been written.
    Stack<DirectoryTimestamp> directoryStack = new Stack<>();

    File baseDirectory = new File(basePath);
    baseDirectory.mkdir();

    TarArchiveInputStream tarStream = new TarArchiveInputStream(inputStream);
    for (TarArchiveEntry entry = tarStream.getNextTarEntry();
        entry != null;
        entry = tarStream.getNextTarEntry()) {
      File target = new File(baseDirectory, entry.getName());
      if (entry.isDirectory()) {
        directoryStack.push(new DirectoryTimestamp(target, entry.getModTime().getTime()));
        continue;
      }
      writeStreamToFile(tarStream, target);
      target.setLastModified(entry.getModTime().getTime());
    }

    while (!directoryStack.empty()) {
      DirectoryTimestamp info = directoryStack.pop();
      info.directory.setLastModified(info.millis);
    }
    tarStream.close();
  }

  public static void writeStreamToFile(InputStream sourceStream, File target) throws IOException {
    File parent = target.getParentFile();
    if (!parent.exists()) {
      parent.mkdirs();
    }
    OutputStream targetStream = new FileOutputStream(target);
    IOUtils.copy(sourceStream, targetStream);
    targetStream.close();
  }
}
