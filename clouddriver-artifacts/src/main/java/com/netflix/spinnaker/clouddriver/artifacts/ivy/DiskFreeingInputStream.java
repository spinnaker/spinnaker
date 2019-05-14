/*
 * Copyright 2018 Pivotal, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.artifacts.ivy;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

/** An {@link java.io.InputStream} that frees local disk resources when closed. */
public class DiskFreeingInputStream extends InputStream {
  private final InputStream delegate;
  private final Path deleteOnClose;

  public DiskFreeingInputStream(InputStream delegate, Path deleteOnClose) {
    this.delegate = delegate;
    this.deleteOnClose = deleteOnClose;
  }

  @Override
  public int read() throws IOException {
    return delegate.read();
  }

  @Override
  public void close() throws IOException {
    super.close();
    if (Files.exists(deleteOnClose)) {
      Files.walk(deleteOnClose)
          .map(Path::toFile)
          .sorted(Comparator.reverseOrder())
          .forEach(File::delete);
    }
  }
}
