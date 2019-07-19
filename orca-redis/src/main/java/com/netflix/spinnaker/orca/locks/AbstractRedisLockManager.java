package com.netflix.spinnaker.orca.locks;

import static java.util.Arrays.asList;
import static net.logstash.logback.argument.StructuredArguments.kv;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class AbstractRedisLockManager implements LockManager {

  private static final int LOCK_VALUE_APPLICATION_IDX = 0;
  private static final int LOCK_VALUE_TYPE_IDX = 1;
  private static final int LOCK_VALUE_IDX = 2;

  private final LockingConfigurationProperties lockingConfigurationProperties;
  private final Logger log = LoggerFactory.getLogger(getClass());

  AbstractRedisLockManager(LockingConfigurationProperties lockingConfigurationProperties) {
    this.lockingConfigurationProperties = lockingConfigurationProperties;
  }

  public abstract void acquireLock(
      String lockName, LockValue lockValue, String lockHolder, int ttlSeconds)
      throws LockFailureException;

  public abstract void extendLock(String lockName, LockValue lockValue, int ttlSeconds)
      throws LockFailureException;

  public abstract void releaseLock(String lockName, LockValue lockValue, String lockHolder);

  static class LockOperation {
    static LockOperation acquire(
        String lockName, LockValue lockValue, String lockHolder, int ttlSeconds) {
      return new LockOperation("acquireLock", lockName, lockValue, lockHolder, ttlSeconds);
    }

    static LockOperation extend(String lockName, LockValue lockValue, int ttlSeconds) {
      return new LockOperation("extendLock", lockName, lockValue, null, ttlSeconds);
    }

    static LockOperation release(String lockName, LockValue lockValue, String lockHolder) {
      return new LockOperation("releaseLock", lockName, lockValue, lockHolder, -1);
    }

    final String operationName;
    final String lockName;
    final LockValue lockValue;
    final String lockHolder;
    final int ttlSeconds;

    LockOperation(
        String operationName,
        String lockName,
        LockValue lockValue,
        String lockHolder,
        int ttlSeconds) {
      this.operationName = Objects.requireNonNull(operationName);
      this.lockName = Objects.requireNonNull(lockName);
      this.lockValue = Objects.requireNonNull(lockValue);
      this.lockHolder = lockHolder;
      this.ttlSeconds = ttlSeconds;
    }

    List<String> key() {
      return asList(getLockKey(lockName));
    }

    List<String> acquireArgs() {
      return asList(
          lockValue.getApplication(),
          lockValue.getType(),
          lockValue.getId(),
          lockHolder,
          Integer.toString(ttlSeconds));
    }

    List<String> extendArgs() {
      return asList(
          lockValue.getApplication(),
          lockValue.getType(),
          lockValue.getId(),
          Integer.toString(ttlSeconds));
    }

    List<String> releaseArgs() {
      return asList(lockValue.getApplication(), lockValue.getType(), lockValue.getId(), lockHolder);
    }
  }

  void checkResult(LockOperation op, List<String> result) {
    final LockValue currentLockValue = buildResultLockValue(result);
    if (!(op.lockValue.equals(currentLockValue))) {
      throw new LockFailureException(op.lockName, currentLockValue);
    }
  }

  LockValue buildResultLockValue(List<String> result) {
    if (result == null || result.size() < 3) {
      throw new IllegalStateException("Unexpected result from redis: " + result);
    }
    if (result.stream().allMatch(Objects::isNull)) {
      return null;
    }
    return new LockValue(
        result.get(LOCK_VALUE_APPLICATION_IDX),
        result.get(LOCK_VALUE_TYPE_IDX),
        result.get(LOCK_VALUE_IDX));
  }

  void withLockingConfiguration(
      LockOperation lockOperation, Consumer<LockOperation> lockManagementOperation)
      throws LockFailureException {
    if (!lockingConfigurationProperties.isEnabled()) {
      return;
    }
    try {
      lockManagementOperation.accept(lockOperation);
    } catch (Throwable t) {
      if (t instanceof LockFailureException) {
        LockFailureException lfe = (LockFailureException) t;
        Optional<LockValue> currentLockValue = Optional.ofNullable(lfe.getCurrentLockValue());
        log.debug(
            "LockFailureException during {} for lock {} currently held by {} {} {} requested by {} {} {} {}",
            kv("operationName", lockOperation.operationName),
            kv("lockName", lockOperation.lockName),
            kv(
                "currentLockValue.application",
                currentLockValue.map(LockValue::getApplication).orElse(null)),
            kv("currentLockValue.type", currentLockValue.map(LockValue::getType).orElse(null)),
            kv("currentLockValue.id", currentLockValue.map(LockValue::getId).orElse(null)),
            kv("requestLockValue.application", lockOperation.lockValue.getApplication()),
            kv("requestLockValue.type", lockOperation.lockValue.getType()),
            kv("requestLockValue.id", lockOperation.lockValue.getId()),
            kv(
                "requestLockHolder",
                Optional.ofNullable(lockOperation.lockHolder).orElse("UNSPECIFIED")),
            lfe);
        if (lockingConfigurationProperties.isLearningMode()) {
          return;
        }
        throw lfe;
      } else {
        log.debug(
            "Exception during {} for lock {} requested by {} {} {} {}",
            kv("operationName", lockOperation.operationName),
            kv("operationName", lockOperation.lockName),
            kv("requestLockValue.application", lockOperation.lockValue.getApplication()),
            kv("requestLockValue.type", lockOperation.lockValue.getType()),
            kv("requestLockValue.id", lockOperation.lockValue.getId()),
            kv(
                "requestLockHolder",
                Optional.ofNullable(lockOperation.lockHolder).orElse("UNSPECIFIED")),
            t);
        if (lockingConfigurationProperties.isLearningMode()) {
          return;
        }

        if (t instanceof RuntimeException) {
          throw (RuntimeException) t;
        }
        throw new RuntimeException("Exception in RedisLockManager", t);
      }
    }
  }

  static String getLockKey(String lockName) {
    return "{namedlock}:" + lockName;
  }

  static final String ACQUIRE_LOCK =
      ""
          + "local lockKey, lockValueApplication, lockValueType, lockValue, holderHashKey, ttlSeconds = "
          + "  KEYS[1], ARGV[1], ARGV[2], ARGV[3], 'lockHolder.' .. ARGV[4], tonumber(ARGV[5]);"
          + "if redis.call('exists', lockKey) == 1 then"
          + "  if not (redis.call('hget', lockKey, 'lockValueApplication') == lockValueApplication and "
          + "          redis.call('hget', lockKey, 'lockValueType') == lockValueType and"
          + "          redis.call('hget', lockKey, 'lockValue') == lockValue) then"
          + "    return redis.call('hmget', lockKey, 'lockValueApplication', 'lockValueType', 'lockValue');"
          + "  end;"
          + "end;"
          + "redis.call('hmset', lockKey, 'lockValueApplication', lockValueApplication, "
          + "  'lockValueType', lockValueType, 'lockValue', lockValue, holderHashKey, 'true');"
          + "redis.call('expire', lockKey, ttlSeconds);"
          + "return {lockValueApplication, lockValueType, lockValue};";

  static final String EXTEND_LOCK =
      ""
          + "local lockKey, lockValueApplication, lockValueType, lockValue, ttlSeconds = "
          + "  KEYS[1], ARGV[1], ARGV[2], ARGV[3], tonumber(ARGV[4]);"
          + "if not (redis.call('hget', lockKey, 'lockValueApplication') == lockValueApplication and "
          + "        redis.call('hget', lockKey, 'lockValueType') == lockValueType and"
          + "        redis.call('hget', lockKey, 'lockValue') == lockValue) then"
          + "  return redis.call('hmget', lockKey, 'lockValueApplication', 'lockValueType', 'lockValue');"
          + "end;"
          + "redis.call('expire', lockKey, ttlSeconds);"
          + "return {lockValueApplication, lockValueType, lockValue};";

  static final String RELEASE_LOCK =
      ""
          + "local lockKey, lockValueApplication, lockValueType, lockValue, holderHashKey = "
          + "  KEYS[1], ARGV[1], ARGV[2], ARGV[3], 'lockHolder.' .. ARGV[4];"
          + "if (redis.call('hget', lockKey, 'lockValueApplication') == lockValueApplication and "
          + "    redis.call('hget', lockKey, 'lockValueType') == lockValueType and"
          + "    redis.call('hget', lockKey, 'lockValue') == lockValue) then"
          + "  redis.call('hdel', lockKey, holderHashKey);"
          + "  if (redis.call('hlen', lockKey) == 3) then"
          + "    redis.call('del', lockKey);"
          + "  end;"
          + "end;"
          + "return 1;";
}
