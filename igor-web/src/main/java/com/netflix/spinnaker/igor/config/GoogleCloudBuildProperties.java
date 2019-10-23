/*
 * Copyright 2019 Google, Inc.
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

package com.netflix.spinnaker.igor.config;

import com.netflix.spinnaker.fiat.model.resources.Permissions;
import com.netflix.spinnaker.igor.model.BuildServiceProvider;
import com.netflix.spinnaker.igor.service.BuildService;
import java.util.List;
import java.util.stream.Collectors;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "gcb")
@Data
public class GoogleCloudBuildProperties {
  private List<Account> accounts;

  public List<BuildService> getGcbBuildServices() {
    return this.accounts.stream().map(BuildService::getView).collect(Collectors.toList());
  }

  @Data
  public static class Account implements BuildService {
    private String name;
    private String project;
    private String subscriptionName;
    private String jsonKey;
    private Permissions.Builder permissions = new Permissions.Builder();

    @Override
    public BuildServiceProvider getBuildServiceProvider() {
      return BuildServiceProvider.GCB;
    }

    @Override
    public Permissions getPermissions() {
      return this.permissions.build();
    }
  }
}
