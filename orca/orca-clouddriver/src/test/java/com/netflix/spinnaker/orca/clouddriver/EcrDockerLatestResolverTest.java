/*
 * Copyright 2026 Moderne, Inc.
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
 */

package com.netflix.spinnaker.orca.clouddriver;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import java.util.Map;
import okhttp3.MediaType;
import okhttp3.ResponseBody;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import retrofit2.Call;
import retrofit2.Response;
import retrofit2.mock.Calls;

final class EcrDockerLatestResolverTest {

  // Full ECR URI form
  private static final String LATEST_REF_FULL =
      "123456789012.dkr.ecr.us-west-2.amazonaws.com/moderne/recipe-worker-arm64:latest";
  private static final String RESOLVED_REF_FULL =
      "123456789012.dkr.ecr.us-west-2.amazonaws.com/moderne/recipe-worker-arm64:0.147.3";

  // Short form as stored by metapipelines.libsonnet defaultArtifact — what actually flows
  // through the repave-allstate execution (confirmed from live Spinnaker execution data).
  // Artifact shape: {type:"docker/image", name:"moderne/audit-reader-arm64",
  //                  reference:"moderne/audit-reader-arm64:latest", version:"latest"}
  private static final String LATEST_REF_SHORT = "moderne/audit-reader-arm64:latest";
  private static final String RESOLVED_REF_SHORT = "moderne/audit-reader-arm64:0.147.3";

  private OortService oortService;
  private EcrDockerLatestResolver target;

  @BeforeEach
  void setUp() {
    oortService = mock(OortService.class);
    target = new EcrDockerLatestResolver(oortService);
  }

  // --- handles() ---

  @Test
  void handles_fullEcrReference() {
    Artifact ecr = Artifact.builder().type("docker/image").reference(LATEST_REF_FULL).build();
    assertThat(target.handles(ecr)).isTrue();
  }

  @Test
  void handles_shortEcrReference_asStoredByMetapipelines() {
    // This is the exact artifact shape from repave-allstate execution that was
    // passing through unresolved before the fix.
    Artifact ecr =
        Artifact.builder()
            .type("docker/image")
            .name("moderne/audit-reader-arm64")
            .version("latest")
            .reference(LATEST_REF_SHORT)
            .build();
    assertThat(target.handles(ecr)).isTrue();
  }

  @Test
  void doesNotHandle_dockerHubReference() {
    Artifact dockerHub =
        Artifact.builder().type("docker/image").reference("docker.io/library/nginx:latest").build();
    assertThat(target.handles(dockerHub)).isFalse();
  }

  @Test
  void doesNotHandle_nullReference() {
    Artifact noRef = Artifact.builder().type("docker/image").build();
    assertThat(target.handles(noRef)).isFalse();
  }

  // --- canonicalize() with full ECR URI ---

  @Test
  void canonicalize_fullUri_callsResolveDockerTag() {
    Call<Map<String, String>> call =
        Calls.response(Map.of("resolvedTag", "0.147.3", "reference", RESOLVED_REF_FULL));
    when(oortService.resolveDockerTag(eq(LATEST_REF_FULL))).thenReturn(call);

    Artifact input =
        Artifact.builder()
            .type("docker/image")
            .name("moderne/recipe-worker-arm64")
            .version("latest")
            .reference(LATEST_REF_FULL)
            .build();
    Artifact result = target.canonicalize(input);
    assertThat(result.getVersion()).isEqualTo("0.147.3");
    assertThat(result.getReference()).isEqualTo(RESOLVED_REF_FULL);
    assertThat(result.getName()).isEqualTo("moderne/recipe-worker-arm64");
  }

  // --- canonicalize() with short reference (the actual bug scenario) ---

  @Test
  void canonicalize_shortRef_callsResolveDockerTagByName_andPinsVersion() {
    // Reproduces the exact failure: artifact from repave-allstate find stage with
    // reference="moderne/audit-reader-arm64:latest", version="latest".
    // After fix, canonicalize() must call resolveDockerTagByName and rewrite the artifact.
    Call<Map<String, String>> call =
        Calls.response(Map.of("resolvedTag", "0.147.3", "reference", RESOLVED_REF_SHORT));
    when(oortService.resolveDockerTagByName(eq("moderne/audit-reader-arm64"), eq("latest")))
        .thenReturn(call);

    Artifact input =
        Artifact.builder()
            .type("docker/image")
            .name("moderne/audit-reader-arm64")
            .version("latest")
            .reference(LATEST_REF_SHORT)
            .build();
    Artifact result = target.canonicalize(input);
    assertThat(result.getVersion()).isEqualTo("0.147.3");
    assertThat(result.getReference()).isEqualTo(RESOLVED_REF_SHORT);
    assertThat(result.getName()).isEqualTo("moderne/audit-reader-arm64");
  }

  @Test
  void canonicalize_oortReturnsIncompleteResponse_throws() {
    Call<Map<String, String>> call = Calls.response(Map.of("resolvedTag", "0.147.3"));
    when(oortService.resolveDockerTag(eq(LATEST_REF_FULL))).thenReturn(call);

    Artifact input = Artifact.builder().type("docker/image").reference(LATEST_REF_FULL).build();
    assertThatThrownBy(() -> target.canonicalize(input))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("incomplete response");
  }

  @Test
  void canonicalize_oortPropagatesErrorAsRuntime() {
    Call<Map<String, String>> call =
        Calls.response(
            Response.error(404, ResponseBody.create("not found", MediaType.parse("text/plain"))));
    when(oortService.resolveDockerTag(eq(LATEST_REF_FULL))).thenReturn(call);

    Artifact input = Artifact.builder().type("docker/image").reference(LATEST_REF_FULL).build();
    assertThatThrownBy(() -> target.canonicalize(input)).isInstanceOf(RuntimeException.class);
  }
}
