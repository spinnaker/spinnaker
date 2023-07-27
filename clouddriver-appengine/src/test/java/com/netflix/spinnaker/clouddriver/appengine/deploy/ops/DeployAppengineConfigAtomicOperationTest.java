/*
 * Copyright 2020 Armory, Inc.
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
 *
 */

package com.netflix.spinnaker.clouddriver.appengine.deploy.ops;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.clouddriver.appengine.deploy.description.DeployAppengineConfigDescription;
import com.netflix.spinnaker.clouddriver.artifacts.ArtifactDownloader;
import com.netflix.spinnaker.kork.artifacts.ArtifactTypes;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

public class DeployAppengineConfigAtomicOperationTest {

  DeployAppengineConfigDescription description = new DeployAppengineConfigDescription();
  DeployAppengineConfigAtomicOperation deployAppengineConfigAtomicOperation;
  ObjectMapper mapper;
  ArtifactDownloader artifactDownloader = mock(ArtifactDownloader.class);

  @BeforeEach
  public void init() {
    deployAppengineConfigAtomicOperation = new DeployAppengineConfigAtomicOperation(description);
    mapper = new ObjectMapper();
    ReflectionTestUtils.setField(
        deployAppengineConfigAtomicOperation, "artifactDownloader", artifactDownloader);
  }

  @Test
  public void shouldCreateEmptyDirectory() throws IOException {
    Path path = null;
    try {
      path = deployAppengineConfigAtomicOperation.createEmptyDirectory();
      assertTrue(path.isAbsolute());
    } finally {
      FileUtils.cleanDirectory(path.toFile());
      FileUtils.forceDelete(path.toFile());
    }
  }

  @Test
  public void shouldDownloadFiletoDirectory() throws IOException {
    InputStream is = new ByteArrayInputStream("dosomething".getBytes(StandardCharsets.UTF_8));
    Map<String, Object> artifactMap = new HashMap<>();
    artifactMap.put("artifactAccount", "embedded-artifact");
    artifactMap.put("id", "123abc");
    artifactMap.put("reference", "ZG9zb21ldGhpbmc=");
    artifactMap.put("type", ArtifactTypes.EMBEDDED_BASE64.getMimeType());
    Artifact artifact = mapper.convertValue(artifactMap, Artifact.class);

    Path path = null;
    try {
      path = deployAppengineConfigAtomicOperation.createEmptyDirectory();
      when(artifactDownloader.download(any())).thenReturn(is);
      File file =
          deployAppengineConfigAtomicOperation.downloadFileToDirectory(
              artifact, path, DeployAppengineConfigAtomicOperation.SupportedConfigTypes.CRON);
      assertTrue(file.exists());
      assertTrue(file.canRead());
    } finally {
      FileUtils.cleanDirectory(path.toFile());
      FileUtils.forceDelete(path.toFile());
    }
  }
}
