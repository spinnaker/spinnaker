/*
 * Copyright 2022 Salesforce.com, Inc.
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

package com.netflix.spinnaker.orca.clouddriver.config.tasks;

import lombok.Data;

@Data
public class CheckIfApplicationExistsTaskConfig {
  // controls whether clouddriver should be queried for an application or not. Defaults to true
  boolean checkClouddriver = true;

  // controls whether the task should fail or simply log a warning
  boolean auditModeEnabled = true;

  // front50 specific retry config. This is only applicable when services.front50.enabled: true
  private RetryConfig front50Retries = new RetryConfig();

  // clouddriver specific retry config. This is only applicable when checkClouddriver: true
  private RetryConfig clouddriverRetries = new RetryConfig();
}
