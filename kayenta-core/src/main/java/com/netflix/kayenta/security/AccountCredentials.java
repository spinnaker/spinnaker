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

package com.netflix.kayenta.security;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.*;
import lombok.experimental.SuperBuilder;

@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "name")
public abstract class AccountCredentials<T> {
  private String name;
  @Builder.Default private List<Type> supportedTypes = new ArrayList<>();

  public abstract String getType();

  /*
   * If this account provides a metrics service, return a list of valid "location" values for the metric
   * scope.  This is used as a UI hint.  If the service cannot enumerate the locations, it should return
   * an empty list, and the UI may provide an input field rather than a selection.
   */

  @Builder.Default private List<String> locations = Collections.emptyList();

  /*
   * If this account provides a recommended list of locations, this can also be used by the UI to limit
   * the initially presented list to something shorter than "everything."  Note that this list may be
   * present even if locations() returns an empty list; this would imply that there are commonly
   * used locations, but the full list is unknown by the metrics service.
   */
  @Builder.Default private List<String> recommendedLocations = Collections.emptyList();

  @JsonIgnore
  public abstract T getCredentials();

  public enum Type {
    METRICS_STORE,
    OBJECT_STORE,
    CONFIGURATION_STORE,
    REMOTE_JUDGE
  }
}
