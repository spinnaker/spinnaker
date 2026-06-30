/*
 * Copyright 2026 Harness, Inc.
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
package com.netflix.spinnaker.kork.web.config;

import java.util.List;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for the trailing slash {@link org.springframework.web.filter.UrlHandlerFilter}.
 *
 * <p>Spring Framework 6.0 (Spring Boot 3.0) removed lenient trailing slash matching, so a request
 * to {@code /path/} no longer matches a controller mapped to {@code /path}. This regresses
 * inter-service calls where the client (e.g. a Retrofit interface) emits a trailing slash. Enabling
 * this filter restores the previous behavior across all Spinnaker services that depend on kork-web.
 */
@Data
@ConfigurationProperties("url-handler.trailing-slash")
public class UrlHandlerFilterConfigurationProperties {

  /** Whether to register the trailing slash {@code UrlHandlerFilter}. Enabled by default. */
  private boolean enabled = true;

  /**
   * Ant-style path patterns for which a trailing slash is trimmed before request handling. Defaults
   * to all paths.
   */
  private List<String> pathPatterns = List.of("/**");
}
