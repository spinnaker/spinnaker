package com.netflix.spinnaker.orca.clouddriver.utils;

import com.netflix.spinnaker.moniker.Moniker;
import com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.support.Location;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Objects;
import java.util.Optional;

public class ClusterLockHelper {
  private static final String CLUSTER_LOCK_FORMAT = "account:%s,locationType:%s,location:%s,cluster:%s";

  @Nonnull
  public static String clusterLockName(@Nonnull Moniker clusterMoniker, @Nonnull String account, @Nullable Location location) {

    Optional<Location> loc = Optional.ofNullable(location);
    return String.format(
      CLUSTER_LOCK_FORMAT,
      Objects.requireNonNull(account),
      loc.map(Location::getType).orElse(null),
      loc.map(Location::getValue).orElse(null),
      Optional.ofNullable(clusterMoniker).map(Moniker::getCluster).orElseThrow(NullPointerException::new));
  }
}
