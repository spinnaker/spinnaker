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

package com.netflix.kayenta.google.security;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.api.services.monitoring.v3.Monitoring;
import com.google.api.services.storage.Storage;
import com.netflix.kayenta.security.AccountCredentials;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.SuperBuilder;

@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
public class GoogleNamedAccountCredentials extends AccountCredentials<GoogleClientFactory> {

  @NotNull private GoogleClientFactory credentials;

  @NotNull private String project;

  private String bucket;
  private String bucketLocation;
  private String rootFolder;

  @Override
  public String getType() {
    return "google";
  }

  public String getMetricsStoreType() {
    return getSupportedTypes().contains(Type.METRICS_STORE) ? "stackdriver" : null;
  }

  @JsonIgnore private Monitoring monitoring;

  @JsonIgnore private Storage storage;
}
