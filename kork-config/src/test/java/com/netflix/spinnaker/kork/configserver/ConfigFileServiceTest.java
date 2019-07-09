/*
 * Copyright 2019 Pivotal, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.kork.configserver;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.config.environment.Environment;
import org.springframework.cloud.config.server.environment.EnvironmentRepository;
import org.springframework.cloud.config.server.resource.NoSuchResourceException;
import org.springframework.cloud.config.server.resource.ResourceRepository;
import org.springframework.core.io.ByteArrayResource;

class ConfigFileServiceTest {
  private static final String TEST_FILE_NAME = "testfile";
  private static final String TEST_FILE_PATH =
      Paths.get(System.getProperty("java.io.tmpdir"), TEST_FILE_NAME).toString();
  private static final String CLOUD_TEST_FILE_NAME = "configserver:" + TEST_FILE_NAME;
  private static final String TEST_FILE_CONTENTS = "test file contents";

  private ResourceRepository resourceRepository = mock(ResourceRepository.class);
  private EnvironmentRepository environmentRepository = mock(EnvironmentRepository.class);

  private ConfigFileService configFileService;

  @BeforeEach
  void setUp() {
    configFileService = new ConfigFileService(resourceRepository, environmentRepository);
  }

  @AfterEach
  void tearDown() throws IOException {
    Files.deleteIfExists(Paths.get(TEST_FILE_PATH));
  }

  @Test
  void getLocalPathWhenFileExists() throws IOException {
    createExpectedFile();

    String fileName = configFileService.getLocalPath(TEST_FILE_PATH);
    assertThat(fileName).isEqualTo(TEST_FILE_PATH);
  }

  @Test
  void getLocalPathWhenFileDoesNotExist() {
    RuntimeException exception =
        assertThrows(RuntimeException.class, () -> configFileService.getLocalPath(TEST_FILE_PATH));
    assertThat(exception.getMessage()).contains(TEST_FILE_PATH);
  }

  @Test
  void getLocalPathWhenFileInConfigServer() {
    expectFileInConfigServer();

    String fileName = configFileService.getLocalPath(CLOUD_TEST_FILE_NAME);
    assertThat(fileName).isEqualTo(TEST_FILE_PATH);
  }

  @Test
  void getLocalPathWhenFileNotInConfigServer() {
    expectFileNotInConfigServer();

    RuntimeException exception =
        assertThrows(
            RuntimeException.class, () -> configFileService.getLocalPath(CLOUD_TEST_FILE_NAME));
    assertThat(exception.getMessage()).contains(CLOUD_TEST_FILE_NAME);
  }

  @Test
  void getLocalPathWhenConfigServerNotConfigured() {
    configFileService = new ConfigFileService();

    RuntimeException exception =
        assertThrows(
            RuntimeException.class, () -> configFileService.getLocalPath(CLOUD_TEST_FILE_NAME));
    assertThat(exception.getMessage()).contains(CLOUD_TEST_FILE_NAME);
  }

  @Test
  void getLocalPathWhenContentProvided() {
    String fileName = configFileService.getLocalPathForContents(TEST_FILE_CONTENTS, TEST_FILE_NAME);
    assertThat(fileName).isEqualTo(TEST_FILE_PATH);
  }

  @Test
  void getContentsWhenFileExists() throws IOException {
    createExpectedFile();

    String contents = configFileService.getContents(TEST_FILE_PATH);
    assertThat(contents).isEqualTo(TEST_FILE_CONTENTS);
  }

  @Test
  void getContentsWhenFileInConfigServer() {
    expectFileInConfigServer();

    String contents = configFileService.getContents(CLOUD_TEST_FILE_NAME);
    assertThat(contents).isEqualTo(TEST_FILE_CONTENTS);
  }

  @Test
  void getContentsWhenFileNotInConfigServer() {
    expectFileNotInConfigServer();

    RuntimeException exception =
        assertThrows(
            RuntimeException.class, () -> configFileService.getContents(CLOUD_TEST_FILE_NAME));
    assertThat(exception.getMessage()).contains(CLOUD_TEST_FILE_NAME);
  }

  private void createExpectedFile() throws IOException {
    File file = new File(TEST_FILE_PATH);
    file.deleteOnExit();

    FileWriter fileWriter = new FileWriter(file);
    fileWriter.write(TEST_FILE_CONTENTS);
    fileWriter.close();
  }

  private void expectFileInConfigServer() {
    when(resourceRepository.findOne(anyString(), isNull(), isNull(), eq(TEST_FILE_NAME)))
        .thenReturn(new ByteArrayResource(TEST_FILE_CONTENTS.getBytes()));
    when(environmentRepository.findOne(anyString(), isNull(), isNull()))
        .thenReturn(new Environment("application"));
  }

  private void expectFileNotInConfigServer() {
    when(resourceRepository.findOne(anyString(), isNull(), isNull(), eq(TEST_FILE_NAME)))
        .thenThrow(new NoSuchResourceException(TEST_FILE_NAME));
  }
}
