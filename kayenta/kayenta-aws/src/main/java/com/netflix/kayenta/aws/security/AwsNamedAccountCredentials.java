/*
 * Copyright 2017 Google, Inc.
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

package com.netflix.kayenta.aws.security;

import com.amazonaws.services.s3.AmazonS3;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.netflix.kayenta.security.AccountCredentials;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@SuperBuilder
@Setter
@Getter
public class AwsNamedAccountCredentials extends AccountCredentials<AwsCredentials> {

  @NotNull private AwsCredentials credentials;

  private String bucket;
  private String region;
  private String rootFolder;

  @Override
  public String getType() {
    return "aws";
  }

  @JsonIgnore private AmazonS3 amazonS3;
}
