/*
 *
 * Copyright 2019 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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

package com.netflix.spinnaker.igor.artifacts;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import com.netflix.spinnaker.kork.web.exceptions.NotFoundException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class ArtifactServiceTest {

  private ArtifactServices artifactServices;

  @BeforeEach
  public void setUp() {
    artifactServices = new ArtifactServices();
    Map<String, ArtifactService> services = new HashMap<>();
    services.put("artifactory", new TestArtifactService());
    artifactServices.addServices(services);
  }

  @Test
  public void findsMatchingService() {
    ArtifactService service = artifactServices.getService("artifactory");
    assertThat(service).isNotNull();
  }

  @Test
  public void doesNotFindNonMatchingService() {
    ArtifactService service = artifactServices.getService("what");
    assertThat(service).isNull();
  }

  @Test
  public void serviceFindsArtifactVersions() {
    ArtifactService service = artifactServices.getService("artifactory");
    List<String> versions = service.getArtifactVersions("deb", "test", (List<String>) null);

    assertThat(versions).isNotNull();
    assertThat(versions).isNotEmpty();
    assertThat(versions.size()).isGreaterThan(0);
  }

  @Test
  public void serviceFindsOnlySnapshotArtifacts() {
    ArtifactService service = artifactServices.getService("artifactory");
    List<String> versions = service.getArtifactVersions("deb", "test", "snapshot");

    assertThat(versions).isNotNull();
    assertThat(versions).isNotEmpty();
    assertThat(versions.size()).isEqualTo(1);
  }

  @Test
  public void serviceFindsArtifact() {
    ArtifactService service = artifactServices.getService("artifactory");
    Artifact artifact = service.getArtifact("deb", "test", "v0.4.0");

    assertThat(artifact).isNotNull();
    assertThat(artifact.getName()).isEqualTo("test");
    assertThat(artifact.getVersion()).isEqualTo("v0.4.0");
  }

  @Test
  public void versionsListIsEmptyWhenNoVersionsFound() {
    ArtifactService service = artifactServices.getService("artifactory");
    List<String> versions = service.getArtifactVersions("deb", "blah", "");

    assertThat(versions).isNotNull();
    assertThat(versions).isEmpty();
  }

  @Test
  public void notFoundExceptionIsThrownWhenArtifactNotFound() {
    ArtifactService service = artifactServices.getService("artifactory");
    assertThrows(NotFoundException.class, () -> service.getArtifact("deb", "blah", "v0.0.1"));
  }
}
