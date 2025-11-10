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

package com.netflix.kayenta.atlas.canary;

import com.netflix.kayenta.canary.CanaryScope;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class AtlasCanaryScope extends CanaryScope {

  @NotNull private String type;

  @NotNull private String deployment;

  @NotNull private String dataset;

  @NotNull private String environment;

  private String
      accountId; // AWS or other account ID we will use to look up the atlas back-end for.

  public String cq() {
    if (type == null) {
      throw new IllegalArgumentException(
          "Atlas canary scope requires 'type' to be asg, cluster, or query.");
    }

    switch (type) {
      case "asg":
        return ":list,(,nf.asg," + scope + ",:eq,:cq,),:each";
      case "cluster":
        return ":list,(,nf.cluster," + scope + ",:eq,:cq,),:each";
      case "query":
        return ":list,(," + scope + ",:cq,),:each";
      default:
        throw new IllegalArgumentException("Scope type '" + type + "' is unknown: scope=" + scope);
    }
  }
}
