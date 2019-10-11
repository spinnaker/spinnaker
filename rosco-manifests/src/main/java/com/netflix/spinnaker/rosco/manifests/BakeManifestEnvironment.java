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
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.annotation.ParametersAreNonnullByDefault;

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
}
