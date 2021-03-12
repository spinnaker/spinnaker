/*
 * Copyright 2018 Netflix, Inc.
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

package com.netflix.spinnaker.orca.clouddriver.utils;

import com.netflix.spinnaker.moniker.Moniker;
import com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.support.Location;
import java.util.Objects;
import java.util.Optional;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class ClusterLockHelper {
  private static final String CLUSTER_LOCK_FORMAT =
      "account:%s,locationType:%s,location:%s,cluster:%s";

  @Nonnull
  public static String clusterLockName(
      @Nonnull Moniker clusterMoniker, @Nonnull String account, @Nullable Location location) {

    Optional<Location> loc = Optional.ofNullable(location);
    return String.format(
        CLUSTER_LOCK_FORMAT,
        Objects.requireNonNull(account),
        loc.map(Location::getType).orElse(null),
        loc.map(Location::getValue).orElse(null),
        Optional.ofNullable(clusterMoniker)
            .map(Moniker::getCluster)
            .orElseThrow(NullPointerException::new));
  }
}
