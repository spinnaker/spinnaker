/*
 * Copyright 2023 Salesforce, Inc.
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

package com.netflix.spinnaker.orca.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Defines properties to configure the {@link
 * com.netflix.spinnaker.orca.pipeline.persistence.RedisExecutionUpdateTimeRepository}
 */
@Data
@ConfigurationProperties("execution-repository.sql.read-replica.execution-update-time-repository")
public class RedisExecutionUpdateTimeRepositoryProperties {
  /**
   * TTL for entries stored in the repository. Defaults to 86400 seconds = 1 day. This is sufficient
   * in most cases because the TTL gets refreshed after each update to an execution and there won't
   * be an execution that runs for longer than a day without an update.
   */
  private Integer TTL = 86400;
}
