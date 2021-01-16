/*
 * Copyright 2021 Armory
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

package com.netflix.spinnaker.clouddriver.artifacts;

import static org.assertj.core.api.Assertions.*;

import com.netflix.spinnaker.clouddriver.artifacts.config.ArtifactCredentials;
import com.netflix.spinnaker.credentials.CredentialsRepository;
import com.netflix.spinnaker.credentials.MapBackedCredentialsRepository;
import com.netflix.spinnaker.credentials.NoopCredentialsLifecycleHandler;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import com.netflix.spinnaker.kork.exceptions.MissingCredentialsException;
import java.io.InputStream;
import java.util.List;
import org.junit.jupiter.api.Test;

public class ArtifactDownloaderTest {

  @Test
  public void testNoSupportedArtifactType() {
    CredentialsRepository<TestArtifactCredentials> repository =
        new MapBackedCredentialsRepository<>(
            TestArtifactCredentials.artifactType, new NoopCredentialsLifecycleHandler<>());
    TestArtifactCredentials credentials = new TestArtifactCredentials("test");
    repository.save(credentials);

    ArtifactCredentialsRepository artifactsCredentials =
        new ArtifactCredentialsRepository(List.of(repository));

    assertThat(artifactsCredentials.getCredentialsForType("test", "type1")).isEqualTo(credentials);
    assertThatExceptionOfType(MissingCredentialsException.class)
        .isThrownBy(() -> artifactsCredentials.getCredentialsForType("unknown", "type1"));
    assertThatExceptionOfType(MissingCredentialsException.class)
        .isThrownBy(() -> artifactsCredentials.getCredentialsForType("test", "unsupportedType"));
  }

  private static final class TestArtifactCredentials implements ArtifactCredentials {
    static final String artifactType = "artifactType";
    private static final List<String> types = List.of("type1", "type2");
    private String name;

    public TestArtifactCredentials(String name) {
      this.name = name;
    }

    @Override
    public String getName() {
      return name;
    }

    @Override
    public String getType() {
      return artifactType;
    }

    @Override
    public List<String> getTypes() {
      return types;
    }

    @Override
    public InputStream download(Artifact artifact) {
      return null;
    }

    @Override
    public boolean handlesType(String type) {
      return types.contains(type);
    }
  }
}
