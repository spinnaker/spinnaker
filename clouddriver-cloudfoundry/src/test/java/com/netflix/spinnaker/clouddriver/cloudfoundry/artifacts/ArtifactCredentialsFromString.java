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

package com.netflix.spinnaker.clouddriver.cloudfoundry.artifacts;

import com.netflix.spinnaker.clouddriver.artifacts.config.ArtifactCredentials;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class ArtifactCredentialsFromString implements ArtifactCredentials {

  private final String name;
  private final List<String> types;
  private final String downloadContent;

  public ArtifactCredentialsFromString(String name, List<String> types, String downloadContent) {
    this.name = name;
    this.types = types;
    this.downloadContent = downloadContent;
  }

  @Override
  public InputStream download(Artifact artifact) {
    return new ByteArrayInputStream(downloadContent.getBytes(StandardCharsets.UTF_8));
  }

  public String getName() {
    return name;
  }

  public List<String> getTypes() {
    return types;
  }

  public String getDownloadContent() {
    return downloadContent;
  }
}
