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
package com.netflix.spinnaker.front50.model.plugins;

import com.netflix.spinnaker.front50.model.Timestamped;
import java.util.Map;
import javax.annotation.Nonnull;
import lombok.Data;

@Data
public class ServerGroupPluginVersions
    implements Timestamped, Comparable<ServerGroupPluginVersions> {

  @Nonnull private String id;

  @Nonnull private String serverGroupName;

  @Nonnull private String location;

  @Nonnull Map<String, String> pluginVersions;

  private Long createTs;

  private Long lastModified;

  private String lastModifiedBy;

  @Override
  public int compareTo(@Nonnull ServerGroupPluginVersions o) {
    return createTs.compareTo(o.createTs);
  }
}
