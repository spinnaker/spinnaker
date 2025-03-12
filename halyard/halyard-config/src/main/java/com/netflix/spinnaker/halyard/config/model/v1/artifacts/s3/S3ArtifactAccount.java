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

package com.netflix.spinnaker.halyard.config.model.v1.artifacts.s3;

import com.netflix.spinnaker.halyard.config.model.v1.node.ArtifactAccount;
import com.netflix.spinnaker.halyard.config.model.v1.node.Secret;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class S3ArtifactAccount extends ArtifactAccount {
  public String name;
  public String apiEndpoint;
  public String apiRegion;
  public String region;
  public String awsAccessKeyId;
  @Secret public String awsSecretAccessKey;
}
