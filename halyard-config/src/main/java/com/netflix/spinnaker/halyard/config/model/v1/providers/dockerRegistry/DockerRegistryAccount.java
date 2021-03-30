/*
 * Copyright 2016 Google, Inc.
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

package com.netflix.spinnaker.halyard.config.model.v1.providers.dockerRegistry;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.netflix.spinnaker.halyard.config.model.v1.node.Account;
import com.netflix.spinnaker.halyard.config.model.v1.node.LocalFile;
import com.netflix.spinnaker.halyard.config.model.v1.node.Secret;
import com.netflix.spinnaker.halyard.config.model.v1.node.SecretFile;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@JsonIgnoreProperties({"providerVersion"})
public class DockerRegistryAccount extends Account {
  private String address;
  private String username;
  @Secret private String password;
  private String passwordCommand;
  private String email;
  private Long cacheIntervalSeconds = 30L;
  private Long clientTimeoutMillis = 60_000L;
  private int cacheThreads = 1;
  private int paginateSize = 100;
  private Boolean sortTagsByDate = false;
  private Boolean trackDigests = false;
  private Boolean insecureRegistry = false;
  private List<String> repositories = new ArrayList<>();
  private String repositoriesRegex;
  @LocalFile @SecretFile private String passwordFile;
  @LocalFile private String dockerconfigFile;

  public String getAddress() {
    if (address.startsWith("https://") || address.startsWith("http://")) {
      return address;
    } else {
      return "https://" + address;
    }
  }
}
