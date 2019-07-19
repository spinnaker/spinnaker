package com.netflix.spinnaker.orca.locks;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import redis.clients.jedis.JedisCluster;

@Component
@ConditionalOnProperty(value = "redis.cluster-enabled")
public class RedisClusterLockManager extends AbstractRedisLockManager {

  private final JedisCluster cluster;

  @Autowired
  public RedisClusterLockManager(JedisCluster cluster, LockingConfigurationProperties properties) {
    super(properties);
    this.cluster = cluster;
  }

  @Override
  @SuppressWarnings("unchecked")
  public void acquireLock(String lockName, LockValue lockValue, String lockHolder, int ttlSeconds)
      throws LockFailureException {
    withLockingConfiguration(
        LockOperation.acquire(lockName, lockValue, lockHolder, ttlSeconds),
        (op) -> {
          final List<String> result =
              (List<String>) cluster.eval(ACQUIRE_LOCK, op.key(), op.acquireArgs());
          checkResult(op, result);
        });
  }

  @Override
  @SuppressWarnings("unchecked")
  public void extendLock(String lockName, LockValue lockValue, int ttlSeconds)
      throws LockFailureException {
    withLockingConfiguration(
        LockOperation.extend(lockName, lockValue, ttlSeconds),
        (op) -> {
          final List<String> result =
              (List<String>) cluster.eval(EXTEND_LOCK, op.key(), op.extendArgs());
          checkResult(op, result);
        });
  }

  @Override
  public void releaseLock(String lockName, LockValue lockValue, String lockHolder) {
    withLockingConfiguration(
        LockOperation.release(lockName, lockValue, lockHolder),
        (op) -> cluster.eval(RELEASE_LOCK, op.key(), op.releaseArgs()));
  }
}
