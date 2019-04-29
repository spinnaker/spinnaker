/*
 * Copyright 2019 Pivotal, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.igor.artifactory.model;

import javax.annotation.Nullable;
import lombok.Data;

@Data
public class ArtifactorySearch {
  private String name;
  private ArtifactoryRepositoryType repoType = ArtifactoryRepositoryType.Maven;
  private String baseUrl;
  private String repo;

  /** One of username/password or an access token is required. */
  @Nullable private String username;

  @Nullable private String password;

  /** One of username/password or an access token is required. */
  @Nullable private String accessToken;

  private boolean ignoreSslIssues = false;

  /** Filter published artifact searches to just this group id. */
  @Nullable private String groupId;

  public String getPartitionName() {
    return baseUrl + "/" + repo;
  }
}
