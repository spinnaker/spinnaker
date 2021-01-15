/*
 * Copyright 2018 Datadog, Inc.
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

package com.netflix.spinnaker.clouddriver.artifacts.s3;

import com.google.common.base.Strings;
import com.netflix.spinnaker.clouddriver.artifacts.config.ArtifactAccount;
import com.netflix.spinnaker.kork.annotations.NonnullByDefault;
import javax.annotation.ParametersAreNullableByDefault;
import lombok.Builder;
import lombok.Value;
import org.springframework.boot.context.properties.ConstructorBinding;

@NonnullByDefault
@Value
public class S3ArtifactAccount implements ArtifactAccount {
  private final String name;
  private final String apiEndpoint;
  private final String apiRegion;
  private final String region;
  private final String awsAccessKeyId;
  private final String awsSecretAccessKey;

  @Builder
  @ConstructorBinding
  @ParametersAreNullableByDefault
  public S3ArtifactAccount(
      String name,
      String apiEndpoint,
      String apiRegion,
      String region,
      String awsAccessKeyId,
      String awsSecretAccessKey) {
    this.name = Strings.nullToEmpty(name);
    this.apiEndpoint = Strings.nullToEmpty(apiEndpoint);
    this.apiRegion = Strings.nullToEmpty(apiRegion);
    this.region = Strings.nullToEmpty(region);
    this.awsAccessKeyId = Strings.nullToEmpty(awsAccessKeyId);
    this.awsSecretAccessKey = Strings.nullToEmpty(awsSecretAccessKey);
  }
}
