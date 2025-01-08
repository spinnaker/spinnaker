/*
 * Copyright 2023 JPMorgan Chase & Co.
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

package com.netflix.spinnaker.orca.clouddriver.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "lambda")
public class LambdaConfigurationProperties {
  private int cloudDriverPostTimeoutSeconds = 120;

  private int cacheRefreshRetryWaitTime = 15;
  private int cacheOnDemandRetryWaitTime = 15;
  private int cloudDriverPostRequestRetries = 5;

  // the time the request for LambdaTrafficUpdateTask takes to show on aws
  // and start moving the weights around and the fastest average is 30-35 seconds
  private int cloudDriverRetrieveNewPublishedLambdaWaitSeconds = 40;

  // the max time a lambda takes to finish moving the weights when it has provisioned concurrency
  // the longest time average is 3 minutes with 20 seconds so the default value is 240 seconds (4
  // min)
  private int cloudDriverRetrieveMaxValidateWeightsTimeSeconds = 240;
}
