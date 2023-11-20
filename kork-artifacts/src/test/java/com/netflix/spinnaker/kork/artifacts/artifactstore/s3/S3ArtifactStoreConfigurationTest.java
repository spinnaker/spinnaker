/*
 * Copyright 2023 Salesforce, Inc.
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
 */

package com.netflix.spinnaker.kork.artifacts.artifactstore.s3;

import static org.assertj.core.api.Assertions.assertThat;

import com.netflix.spinnaker.kork.artifacts.artifactstore.ArtifactStore;
import com.netflix.spinnaker.kork.artifacts.artifactstore.ArtifactStoreConfiguration;
import com.netflix.spinnaker.kork.artifacts.artifactstore.ArtifactStoreURIBuilder;
import com.netflix.spinnaker.kork.artifacts.artifactstore.NoopArtifactStoreGetter;
import com.netflix.spinnaker.kork.artifacts.artifactstore.NoopArtifactStoreStorer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.springframework.boot.context.annotation.UserConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import software.amazon.awssdk.services.s3.S3Client;

class S3ArtifactStoreConfigurationTest {

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withConfiguration(UserConfigurations.of(ArtifactStoreConfiguration.class));

  @BeforeEach
  void init(TestInfo testInfo) {
    System.out.println("--------------- Test " + testInfo.getDisplayName());
  }

  @Test
  void testArtifactStoreS3Disabled() {
    runner
        .withPropertyValues(
            "artifact-store.type=s3",
            "artifact-store.s3.enabled=false",
            "artifact-store.s3.region=us-west-2") // arbitrary region
        .run(
            ctx -> {
              assertThat(ctx).hasSingleBean(ArtifactStoreURIBuilder.class);
              assertThat(ctx).hasSingleBean(ArtifactStore.class);
              assertThat(ctx).hasSingleBean(S3ArtifactStoreGetter.class);
              assertThat(ctx).hasSingleBean(NoopArtifactStoreStorer.class);
              assertThat(ctx).doesNotHaveBean(NoopArtifactStoreGetter.class);
              assertThat(ctx).doesNotHaveBean(S3ArtifactStoreStorer.class);
              assertThat(ctx).hasSingleBean(S3Client.class);
            });
  }

  @Test
  void testArtifactStoreS3Enabled() {
    runner
        .withPropertyValues(
            "artifact-store.type=s3",
            "artifact-store.s3.enabled=true",
            "artifact-store.s3.region=us-west-2") // arbitrary region
        .run(
            ctx -> {
              assertThat(ctx).hasSingleBean(ArtifactStoreURIBuilder.class);
              assertThat(ctx).hasSingleBean(ArtifactStore.class);
              assertThat(ctx).hasSingleBean(S3ArtifactStoreGetter.class);
              assertThat(ctx).hasSingleBean(S3ArtifactStoreStorer.class);
              assertThat(ctx).doesNotHaveBean(NoopArtifactStoreGetter.class);
              assertThat(ctx).doesNotHaveBean(NoopArtifactStoreStorer.class);
              assertThat(ctx).hasSingleBean(S3Client.class);
            });
  }
}
