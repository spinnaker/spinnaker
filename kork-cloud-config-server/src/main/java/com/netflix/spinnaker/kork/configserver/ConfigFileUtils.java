/*
 * Copyright 2019 Pivotal, Inc.
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

package com.netflix.spinnaker.kork.configserver;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ConfigFileUtils {
  static String writeToTempFile(String contents, String resourceName) {
    try {
      Path tempDirPath = Paths.get(System.getProperty("java.io.tmpdir"), resourceName);
      createParentDirsIfNecessary(tempDirPath);
      Files.write(
          tempDirPath,
          contents.getBytes(),
          StandardOpenOption.WRITE,
          StandardOpenOption.CREATE,
          StandardOpenOption.TRUNCATE_EXISTING);

      return tempDirPath.toString();
    } catch (IOException e) {
      throw new ConfigFileLoadingException(
          "Exception writing local file for resource \"" + resourceName + "\": " + e.getMessage(),
          e);
    }
  }

  private static void createParentDirsIfNecessary(Path tempDirPath) throws IOException {
    if (Files.notExists(tempDirPath) && tempDirPath.getParent() != null) {
      Files.createDirectories(tempDirPath.getParent());
    }
  }
}
