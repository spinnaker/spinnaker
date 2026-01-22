/*
 * Copyright 2019 Pivotal, Inc.
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

package com.netflix.spinnaker.clouddriver.artifacts.docker;

import com.google.common.collect.ImmutableList;
import com.netflix.spinnaker.clouddriver.artifacts.config.ArtifactCredentials;
import com.netflix.spinnaker.kork.annotations.NonnullByDefault;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import java.io.InputStream;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@NonnullByDefault
@Value
final class DockerArtifactCredentials implements ArtifactCredentials {
  public static final String CREDENTIALS_TYPE = "artifacts-docker";
  public static final String TYPE = "docker/image";

  private final String name;
  private final ImmutableList<String> types = ImmutableList.of(TYPE);

  DockerArtifactCredentials(DockerArtifactAccount account) {
    this.name = account.getName();
  }

  public InputStream download(Artifact artifact) {
    throw new UnsupportedOperationException(
        "Docker references are passed on to cloud platforms who retrieve images directly");
  }

  @Override
  public String getType() {
    return CREDENTIALS_TYPE;
  }
}
