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

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junitpioneer.jupiter.TempDirectory;

@ExtendWith(TempDirectory.class)
class DiskFreeingInputStreamTest {
  @Test
  void onlyFreeResourcesOnce(@TempDirectory.TempDir Path tempDir) throws IOException {
    Path temp = tempDir.resolve("temp");
    Files.createDirectories(temp);
    Path test = temp.resolve("test.txt");
    Files.write(test, "hello world".getBytes());
    FileInputStream fis = new FileInputStream(test.toFile());
    DiskFreeingInputStream dfis = new DiskFreeingInputStream(fis, temp);
    assertThat(dfis)
        .hasSameContentAs(new ByteArrayInputStream("hello world".getBytes())); // closes once
    dfis.close();
  }
}
