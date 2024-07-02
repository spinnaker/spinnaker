/*
 * Copyright 2022 Armory, Inc.
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
 */

package com.netflix.spinnaker.kork.expressions.config;

import lombok.Data;
import lombok.experimental.Accessors;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Configuration properties for Spring Expression Language (SpEL) evaluation related features. */
@Data
@ConfigurationProperties(prefix = "expression")
public class ExpressionProperties {

  /** Flag to determine if SpEL evaluation to be skipped. */
  private final FeatureFlag doNotEvalSpel = new FeatureFlag().setEnabled(true);

  /**
   * To set the maximum limit of characters in expression for SpEL evaluation. Default value -1
   * signifies to use default maximum limit of 10,000 characters provided by springframework.
   */
  private int maxExpressionLength = -1;

  @Data
  @Accessors(chain = true)
  public static class FeatureFlag {
    private boolean enabled;
  }
}
