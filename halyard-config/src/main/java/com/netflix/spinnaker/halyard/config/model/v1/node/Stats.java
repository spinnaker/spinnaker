/*
 * Copyright 2019 Armory, Inc.
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

package com.netflix.spinnaker.halyard.config.model.v1.node;

import de.huxhorn.sulky.ulid.ULID;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = false)
public class Stats extends Node {

  public static String DEFAULT_STATS_ENDPOINT = "https://stats.spinnaker.io";

  @Override
  public String getNodeName() {
    return "stats";
  }

  @ValidForSpinnakerVersion(
      lowerBound = "1.19.0",
      tooLowMessage = "Stats are not available prior to this release.")
  private Boolean enabled = true;

  private String endpoint = DEFAULT_STATS_ENDPOINT;
  private String instanceId = new ULID().nextULID();
  private String spinnakerVersion;
  private DeploymentMethod deploymentMethod = new DeploymentMethod();
  private int connectionTimeoutMillis = 3000;
  private int readTimeoutMillis = 5000;

  @Data
  public static class DeploymentMethod {

    public static String HALYARD = "halyard";

    private String type;
    private String version;
  }
}
