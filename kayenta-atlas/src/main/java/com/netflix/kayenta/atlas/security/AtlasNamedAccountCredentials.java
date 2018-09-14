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

package com.netflix.kayenta.atlas.security;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.netflix.kayenta.atlas.backends.BackendUpdater;
import com.netflix.kayenta.security.AccountCredentials;
import lombok.Builder;
import lombok.Data;
import lombok.Singular;

import javax.validation.constraints.NotNull;
import java.util.Collections;
import java.util.List;

@Builder
@Data
public class AtlasNamedAccountCredentials implements AccountCredentials<AtlasCredentials> {

  @NotNull
  private String name;

  @NotNull
  @Singular
  private List<Type> supportedTypes;

  @Override
  public String getType() {
    return "atlas";
  }

  @NotNull
  private AtlasCredentials credentials;

  private String fetchId;

  private List<String> recommendedLocations;

  @JsonIgnore
  private BackendUpdater backendUpdater;

  @Override
  public List<String> getLocations() {
    return getBackendUpdater().getBackendDatabase().getLocations();
  }

  @Override
  public List<String> getRecommendedLocations() {
    if (recommendedLocations == null) {
      return Collections.emptyList();
    } else {
      return recommendedLocations;
    }
  }

}
