/*
 * Copyright 2018 Pivotal, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.artifacts.ivy;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.netflix.spinnaker.clouddriver.artifacts.config.ArtifactAccount;
import com.netflix.spinnaker.clouddriver.artifacts.ivy.settings.IvySettings;
import com.netflix.spinnaker.kork.annotations.NonnullByDefault;
import javax.annotation.Nullable;
import javax.annotation.ParametersAreNullableByDefault;
import lombok.Builder;
import lombok.Value;
import org.springframework.boot.context.properties.ConstructorBinding;

@NonnullByDefault
@Value
public class IvyArtifactAccount implements ArtifactAccount {
  private final String name;
  @Nullable private final IvySettings settings;

  @JsonIgnore
  private final ImmutableList<String> resolveConfigurations = ImmutableList.of("master");

  @Builder
  @ConstructorBinding
  @ParametersAreNullableByDefault
  public IvyArtifactAccount(String name, IvySettings settings) {
    this.name = Strings.nullToEmpty(name);
    this.settings = settings;
  }
}
