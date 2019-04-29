/*
 * Copyright 2019 Pivotal, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.igor.config;

import com.netflix.spinnaker.fiat.model.resources.Permissions;
import java.util.List;
import javax.annotation.Nullable;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "concourse")
public class ConcourseProperties {
  private List<Host> masters;

  @Data
  public static class Host {
    private String name;

    /** Including scheme because the default docker-compose setup does not support https. */
    private String url;

    private String username;
    private String password;

    @Nullable private List<String> teams;

    /**
     * Events will only be created for builds containing resource(s) matching this pattern. Build
     * properties will contain only metadata from matching resource(s).
     */
    @Nullable private String resourceFilterRegex;

    /**
     * When retrieving build or build-related information like git revision information, never look
     * past this number of recent builds.
     */
    private Integer buildLookbackLimit = 200;

    private Permissions.Builder permissions = new Permissions.Builder();
  }
}
