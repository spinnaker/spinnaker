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

import com.google.common.base.Strings;
import com.netflix.spinnaker.fiat.model.resources.Permissions;
import com.netflix.spinnaker.igor.model.BuildServiceProvider;
import com.netflix.spinnaker.igor.service.BuildService;
import com.netflix.spinnaker.kork.annotations.NonnullByDefault;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.annotation.ParametersAreNullableByDefault;
import lombok.Builder;
import lombok.Data;
import lombok.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.ConstructorBinding;

@ConfigurationProperties(prefix = "gcb")
@Data
public class GoogleCloudBuildProperties {
  private List<Account> accounts;

  public List<BuildService> getGcbBuildServices() {
    return this.accounts.stream().map(BuildService::getView).collect(Collectors.toList());
  }

  @NonnullByDefault
  @Value
  public static final class Account implements BuildService {
    private final String name;
    private final String project;
    private final String subscriptionName;
    private final String jsonKey;
    private final Permissions permissions;

    @Builder
    @ConstructorBinding
    @ParametersAreNullableByDefault
    public Account(
        String name,
        String project,
        String subscriptionName,
        String jsonKey,
        Permissions.Builder permissions) {
      this.name = Strings.nullToEmpty(name);
      this.project = Strings.nullToEmpty(project);
      this.subscriptionName = Strings.nullToEmpty(subscriptionName);
      this.jsonKey = Strings.nullToEmpty(jsonKey);
      this.permissions =
          Optional.ofNullable(permissions)
              .map(Permissions.Builder::build)
              .orElse(Permissions.EMPTY);
    }

    @Override
    public BuildServiceProvider getBuildServiceProvider() {
      return BuildServiceProvider.GCB;
    }
  }
}
