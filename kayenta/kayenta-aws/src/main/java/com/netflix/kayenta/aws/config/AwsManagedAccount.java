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

package com.netflix.kayenta.aws.config;

import com.netflix.kayenta.security.AccountCredentials;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import lombok.Data;

@Data
public class AwsManagedAccount {

  @NotNull private String name;

  private String bucket;
  private String region;
  private String rootFolder = "kayenta";
  private String profileName;
  private String endpoint;
  private String proxyHost;
  private String proxyPort;
  private String proxyProtocol;
  private ExplicitAwsCredentials explicitCredentials;

  private List<AccountCredentials.Type> supportedTypes;

  @Data
  public static class ExplicitAwsCredentials {

    String accessKey;
    String secretKey;
    String sessionToken;
  }
}
