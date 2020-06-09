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
package com.netflix.spinnaker.orca.clouddriver.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

@Data
@ConfigurationProperties("poller")
public class PollerConfigurationProperties {

  @NestedConfigurationProperty
  private EphemeralServerGroupsPollerProperties ephemeralServerGroupsPoller =
      new EphemeralServerGroupsPollerProperties();

  @Data
  public static class EphemeralServerGroupsPollerProperties {

    /**
     * If defined, this username will be used for authorization of the executions created by the
     * poller.
     *
     * <p>If left undefined, the application owner email will be used.
     */
    private String username;
  }
}
