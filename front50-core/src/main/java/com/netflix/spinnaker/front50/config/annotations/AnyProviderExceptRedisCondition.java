/*
 * Copyright 2020 Schibsted ASA.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 *
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.front50.config.annotations;

import org.springframework.boot.autoconfigure.condition.AnyNestedCondition;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

public class AnyProviderExceptRedisCondition extends AnyNestedCondition {

  public AnyProviderExceptRedisCondition() {
    super(ConfigurationPhase.PARSE_CONFIGURATION);
  }

  @ConditionalOnProperty("sql.enabled")
  static class SqlEnabled {}

  @ConditionalOnProperty("spinnaker.s3.enabled")
  static class S3Enabled {}

  @ConditionalOnProperty("spinnaker.oracle.enabled")
  static class OracleEnabled {}

  @ConditionalOnProperty("spinnaker.gcs.enabled")
  static class GcsEnabled {}

  @ConditionalOnProperty("spinnaker.azs.enabled")
  static class AzureEnabled {}

  @ConditionalOnProperty("spinnaker.swift.enabled")
  static class SwiftEnabled {}
}
