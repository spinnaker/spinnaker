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
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import javax.validation.constraints.NotNull;

@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class AtlasCanaryScope extends CanaryScope {

  @NotNull
  private String type;

  public String cq() {
    switch (type) {
      case "application":
        return ":list,(,nf.app," + scope + ",:eq,:cq,),:each";
      case "cluster":
        return ":list,(,nf.cluster," + scope + ",:eq,:cq,),:each";
      default:
        throw new IllegalArgumentException("Scope type is unknown: " + scope);
    }
  }
}
