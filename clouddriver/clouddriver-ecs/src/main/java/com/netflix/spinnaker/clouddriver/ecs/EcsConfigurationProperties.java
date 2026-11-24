/*
 * Copyright 2018 Lookout, Inc.
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

package com.netflix.spinnaker.clouddriver.ecs;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties("ecs")
public class EcsConfigurationProperties {
  private CacheProperties cache = new CacheProperties();

  @Data
  public static class CacheProperties {
    /**
     * Look services up through the cache's application index ({@code Cache.getAllByApplication})
     * instead of scanning by service name. Services only carry the indexed application once their
     * caching agent has re-cached them, so enable this after the agents have completed a cycle with
     * the application attribute. Once enabled the index is trusted: an application with no indexed
     * services is treated as having none, rather than probing every account and region by name.
     */
    private boolean queryByApplication = false;
  }
}
