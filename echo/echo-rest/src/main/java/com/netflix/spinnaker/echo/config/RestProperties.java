/*
 * Copyright 2019 Netflix, Inc.
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

package com.netflix.spinnaker.echo.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import java.util.Map;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

/** Helper class to map hosts in properties file into a validated list */
@Configuration
@ConfigurationProperties(prefix = "rest")
@Validated
@Data
public class RestProperties {
  @Valid List<RestEndpointConfiguration> endpoints;

  @Data
  public static class RestEndpointConfiguration {
    String eventName;
    String fieldName;
    String template;
    Boolean wrap = false;
    @NotEmpty String url;
    Boolean insecure = false;
    String username;
    String password;
    Map<String, String> headers;
    String headersFile;
    Boolean flatten = false;
    int retryCount = 1;
    Boolean circuitBreakerEnabled = false;
  }
}
