/*
 * Copyright 2023 Apple Inc.
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
package com.netflix.spinnaker.kork.artifacts.artifactstore;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties("artifact-store")
public class ArtifactStoreConfigurationProperties {
  private String applicationsRegex = null;

  /** The type of artifact store to use (e.g. s3). */
  private String type = null;

  /** Configuration for an s3 client which will utilize credentials in the AWS credentials file. */
  @Data
  public static class S3ClientConfig {
    private boolean enabled = false;
    private String profile = null;
    private String region = null;
    /**
     * Url may be used to override the contact URL to an s3 compatible object store. This is useful
     * for testing utilizing things like seaweedfs.
     */
    private String url = null;

    private String bucket = null;
    private String accessKey = null;
    private String secretKey = null;
    private boolean forcePathStyle = true;
  }

  @Data
  public static class HelmConfig {
    /** Enables Rosco to expand any artifact URIs passed as parameters for Helm. */
    private boolean expandOverrides = false;
  }

  private S3ClientConfig s3 = new S3ClientConfig();
  private HelmConfig helm = new HelmConfig();
}
