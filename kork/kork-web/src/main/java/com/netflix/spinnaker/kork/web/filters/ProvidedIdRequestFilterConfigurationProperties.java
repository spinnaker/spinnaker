/*
 * Copyright 2025 Salesforce, Inc.
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
package com.netflix.spinnaker.kork.web.filters;

import com.netflix.spinnaker.kork.common.Header;
import java.util.List;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "provided-id-request-filter")
public class ProvidedIdRequestFilterConfigurationProperties {

  /** The headers to include in the MDC. */
  private List<String> headers =
      List.of(Header.REQUEST_ID.getHeader(), Header.EXECUTION_ID.getHeader());

  /**
   * Additional headers to include in the MDC. It's a separate property since it's more likely to
   * add additional headers, than to remove something from the defaults.
   */
  private List<String> additionalHeaders = List.of();
}
