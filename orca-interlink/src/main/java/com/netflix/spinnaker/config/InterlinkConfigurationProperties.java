/*
 * Copyright 2020 Netflix, Inc.
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

package com.netflix.spinnaker.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "interlink")
public class InterlinkConfigurationProperties {
  FlaggerProperties flagger;

  /** see {@link com.netflix.spinnaker.orca.interlink.MessageFlagger} */
  @Data
  public static class FlaggerProperties {
    public boolean enabled = true;
    public int maxSize = 32;
    public int threshold = 8;
    public long lookbackSeconds = 60;
  }
}
