package com.netflix.spinnaker.orca.notifications;

import org.jetbrains.annotations.NotNull;
import redis.clients.jedis.JedisCluster;

public class RedisClusterNotificationClusterLock implements NotificationClusterLock {
  private final JedisCluster cluster;

  public RedisClusterNotificationClusterLock(JedisCluster cluster) {
    this.cluster = cluster;
  }

  @Override
  public boolean tryAcquireLock(@NotNull String notificationType, long lockTimeoutSeconds) {
    String key = "lock:" + notificationType;
    return "OK".equals(cluster.set(key, "\uD83D\uDD12", "NX", "EX", lockTimeoutSeconds));
  }
}
