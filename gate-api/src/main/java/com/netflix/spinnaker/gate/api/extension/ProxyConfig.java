/*
 * Copyright 2020 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.gate.api.extension;

import com.netflix.spinnaker.kork.annotations.Alpha;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Data;

@Data
@Alpha
public class ProxyConfig {
  /** Identifier for this proxy, must be unique. */
  private String id;

  /** Target uri for this proxy. */
  private String uri;

  /** Supported http methods for this proxy. */
  private List<String> methods = new ArrayList<>();

  /** Connection timeout, defaults to 30s. */
  private Long connectTimeoutMs = 30_000L;

  /** Read timeout, defaults to 59s. */
  private Long readTimeoutMs = 59_000L;

  /** Write timeout, defaults to 30s. */
  private Long writeTimeoutMs = 30_000L;

  /** Additional attributes for this proxy. */
  private Map<String, String> additionalAttributes = new HashMap<>();
}
