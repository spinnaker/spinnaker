/*
 * Copyright 2020 Coveo, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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
 *
 */
package com.netflix.spinnaker.clouddriver.kubernetes.config;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import lombok.Data;
import lombok.Getter;

@Data
public class RawResourcesEndpointConfig {
  private Set<String> kindExpressions = new HashSet<>();
  private Set<String> omitKindExpressions = new HashSet<>();
  @Getter private List<Pattern> kindPatterns = new ArrayList<>();
  @Getter private List<Pattern> omitKindPatterns = new ArrayList<>();

  public void validate() {
    if (!kindExpressions.isEmpty() && !omitKindExpressions.isEmpty()) {
      throw new IllegalArgumentException(
          "At most one of 'kindExpressions' and 'omitKindExpressions' can be specified");
    }
    for (String exp : kindExpressions) {
      kindPatterns.add(Pattern.compile(exp));
    }
    for (String exp : omitKindExpressions) {
      omitKindPatterns.add(Pattern.compile(exp));
    }
  }
}
