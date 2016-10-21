/*
 * Copyright 2016 Netflix, Inc.
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

package com.netflix.spinnaker.retrofit;

import org.springframework.boot.context.properties.ConfigurationProperties;
import retrofit.RestAdapter;

@ConfigurationProperties("retrofit")
public class RetrofitConfigurationProperties {
  RestAdapter.LogLevel logLevel = RestAdapter.LogLevel.BASIC;

  public RestAdapter.LogLevel getLogLevel() {
    return logLevel;
  }

  public void setLogLevel(RestAdapter.LogLevel logLevel) {
    this.logLevel = logLevel;
  }
}
