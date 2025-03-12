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

package com.netflix.spinnaker.kork.jedis.lock;

import static com.netflix.spinnaker.kork.jedis.lock.RedisLockManager.LockScripts.*;
import static com.netflix.spinnaker.kork.lock.LockManager.LockReleaseStatus.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spectator.api.Id;
import com.netflix.spectator.api.Registry;
import com.netflix.spectator.api.patterns.LongTaskTimer;
import com.netflix.spinnaker.kork.jedis.RedisClientDelegate;
import com.netflix.spinnaker.kork.lock.RefreshableLockManager;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import javax.annotation.Nonnull;
import javax.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RedisLockManager implements RefreshableLockManager {
  private static final Logger log = LoggerFactory.getLogger(RedisLockManager.class);
  private static final long DEFAULT_HEARTBEAT_RATE_MILLIS = 5000L;
  private static final long DEFAULT_TTL_MILLIS = 10000L;
  private static final int MAX_HEARTBEAT_RETRIES = 3;

  private final String ownerName;
  private final Clock clock;
  private final Registry registry;
  private final ObjectMapper objectMapper;
  private final RedisClientDelegate redisClientDelegate;
  private final ScheduledExecutorService scheduledExecutorService;

  private final Id acquireId;
  private final Id releaseId;
  private final Id heartbeatId;
  private final Id acquireDurationId;

  private long heartbeatRateMillis;
  private long leaseDurationMillis;
  private BlockingDeque<HeartbeatLockRequest> heartbeatQueue;

  public RedisLockManager(
      String ownerName,
      Clock clock,
      Registry registry,
      ObjectMapper objectMapper,
      RedisClientDelegate redisClientDelegate,
      Optional<Long> heartbeatRateMillis,
      Optional<Long> leaseDurationMillis) {
    this.ownerName = Optional.ofNullable(ownerName).orElse(getOwnerName());
    this.clock = clock;
    this.registry = registry;
    this.objectMapper = objectMapper;
    this.redisClientDelegate = redisClientDelegate;
    this.scheduledExecutorService = Executors.newSingleThreadScheduledExecutor();
    this.heartbeatQueue = new LinkedBlockingDeque<>();
    this.heartbeatRateMillis = heartbeatRateMillis.orElse(DEFAULT_HEARTBEAT_RATE_MILLIS);
    this.leaseDurationMillis = leaseDurationMillis.orElse(DEFAULT_TTL_MILLIS);

    acquireId = registry.createId(LockMetricsConstants.ACQUIRE);
    releaseId = registry.createId(LockMetricsConstants.RELEASE);
    heartbeatId = registry.createId(LockMetricsConstants.HEARTBEATS);
    acquireDurationId = registry.createId(LockMetricsConstants.ACQUIRE_DURATION);
    scheduleHeartbeats();
  }

  public RedisLockManager(
      String ownerName,
      Clock clock,
      Registry registry,
      ObjectMapper objectMapper,
      RedisClientDelegate redisClientDelegate) {
    this(
        ownerName,
        clock,
        registry,
        objectMapper,
        redisClientDelegate,
        Optional.of(DEFAULT_HEARTBEAT_RATE_MILLIS),
        Optional.of(DEFAULT_TTL_MILLIS));
  }

  @Override
  public <R> AcquireLockResponse<R> acquireLock(
      @Nonnull final LockOptions lockOptions, @Nonnull final Callable<R> onLockAcquiredCallback) {
    return acquire(lockOptions, onLockAcquiredCallback);
  }

  @Override
  public <R> AcquireLockResponse<R> acquireLock(
      @Nonnull final String lockName,
      final long maximumLockDurationMillis,
      @Nonnull final Callable<R> onLockAcquiredCallback) {
    LockOptions lockOptions =
        new LockOptions()
            .withLockName(lockName)
            .withMaximumLockDuration(Duration.ofMillis(maximumLockDurationMillis));
    return acquire(lockOptions, onLockAcquiredCallback);
  }

  @Override
  public AcquireLockResponse<Void> acquireLock(
      @Nonnull final String lockName,
      final long maximumLockDurationMillis,
      @Nonnull final Runnable onLockAcquiredCallback) {
    LockOptions lockOptions =
        new LockOptions()
            .withLockName(lockName)
            .withMaximumLockDuration(Duration.ofMillis(maximumLockDurationMillis));
    return acquire(lockOptions, onLockAcquiredCallback);
  }

  @Override
  public AcquireLockResponse<Void> acquireLock(
      @Nonnull LockOptions lockOptions, @Nonnull Runnable onLockAcquiredCallback) {
    return acquire(lockOptions, onLockAcquiredCallback);
  }

  @Override
  public boolean releaseLock(@Nonnull final Lock lock, boolean wasWorkSuccessful) {
    // we are aware that the cardinality can get high. To revisit if concerns arise.
    Id lockRelease = releaseId.withTag("lockName", lock.getName());
    String status = tryReleaseLock(lock, wasWorkSuccessful);
    registry.counter(lockRelease.withTag("status", status)).increment();

    switch (status) {
      case SUCCESS:
      case SUCCESS_GONE:
        log.info("Released lock (wasWorkSuccessful: {}, {})", wasWorkSuccessful, lock);
        return true;

      case FAILED_NOT_OWNER:
        log.warn(
            "Failed releasing lock, not owner (wasWorkSuccessful: {}, {})",
            wasWorkSuccessful,
            lock);
        return false;

      default:
        log.error(
            "Unknown release response code {} (wasWorkSuccessful: {}, {})",
            status,
            wasWorkSuccessful,
            lock);
        return false;
    }
  }

  @Override
  public HeartbeatResponse heartbeat(HeartbeatLockRequest heartbeatLockRequest) {
    return doHeartbeat(heartbeatLockRequest);
  }

  @Override
  public void queueHeartbeat(HeartbeatLockRequest heartbeatLockRequest) {
    if (!heartbeatQueue.contains(heartbeatLockRequest)) {
      log.info(
          "Lock {} will heartbeats for {}ms",
          heartbeatLockRequest.getLock(),
          heartbeatLockRequest.getHeartbeatDuration().toMillis());
      heartbeatQueue.add(heartbeatLockRequest);
    }
  }

  private <R> AcquireLockResponse<R> doAcquire(
      @Nonnull final LockOptions lockOptions,
      final Optional<Callable<R>> onLockAcquiredCallbackCallable,
      final Optional<Runnable> onLockAcquiredCallbackRunnable) {
    lockOptions.validateInputs();
    Lock lock = null;
    R workResult = null;
    LockStatus status = LockStatus.TAKEN;
    HeartbeatLockRequest heartbeatLockRequest = null;
    if (lockOptions.getVersion() == null || !lockOptions.isReuseVersion()) {
      lockOptions.setVersion(clock.millis());
    }

    try {
      lock = tryCreateLock(lockOptions);
      if (!matchesLock(lockOptions, lock)) {
        log.debug("Could not acquire already taken lock {}", lock);
        return new AcquireLockResponse<>(lock, null, status, null, false);
      }

      LongTaskTimer acquireDurationTimer =
          LongTaskTimer.get(registry, acquireDurationId.withTag("lockName", lock.getName()));

      status = LockStatus.ACQUIRED;
      log.info("Acquired Lock {}.", lock);
      long timer = acquireDurationTimer.start();

      // Queues the acquired lock to receive heartbeats for the defined max lock duration.
      AtomicInteger heartbeatRetriesOnFailure = new AtomicInteger(MAX_HEARTBEAT_RETRIES);
      heartbeatLockRequest =
          new HeartbeatLockRequest(
              lock,
              heartbeatRetriesOnFailure,
              clock,
              lockOptions.getMaximumLockDuration(),
              lockOptions.isReuseVersion());

      queueHeartbeat(heartbeatLockRequest);
      synchronized (heartbeatLockRequest.getLock()) {
        try {
          if (onLockAcquiredCallbackCallable.isPresent()) {
            workResult = onLockAcquiredCallbackCallable.get().call();
          } else {
            onLockAcquiredCallbackRunnable.ifPresent(Runnable::run);
          }
        } catch (Exception e) {
          log.error("Callback failed using lock {}", lock, e);
          throw new LockCallbackException(e);
        } finally {
          acquireDurationTimer.stop(timer);
        }
      }

      heartbeatQueue.remove(heartbeatLockRequest);
      lock = findAuthoritativeLockOrNull(lock);
      return new AcquireLockResponse<>(
          lock, workResult, status, null, tryLockReleaseQuietly(lock, true));
    } catch (Exception e) {
      log.error(e.getMessage());
      heartbeatQueue.remove(heartbeatLockRequest);
      lock = findAuthoritativeLockOrNull(lock);
      boolean lockWasReleased = tryLockReleaseQuietly(lock, false);

      if (e instanceof LockCallbackException) {
        throw e;
      }

      status = LockStatus.ERROR;
      return new AcquireLockResponse<>(lock, workResult, status, e, lockWasReleased);
    } finally {
      registry
          .counter(
              acquireId
                  .withTag("lockName", lockOptions.getLockName())
                  .withTag("status", status.toString()))
          .increment();
    }
  }

  private AcquireLockResponse<Void> acquire(
      @Nonnull final LockOptions lockOptions, @Nonnull final Runnable onLockAcquiredCallback) {
    return doAcquire(lockOptions, Optional.empty(), Optional.of(onLockAcquiredCallback));
  }

  private <R> AcquireLockResponse<R> acquire(
      @Nonnull final LockOptions lockOptions, @Nonnull final Callable<R> onLockAcquiredCallback) {
    return doAcquire(lockOptions, Optional.of(onLockAcquiredCallback), Optional.empty());
  }

  @PreDestroy
  private void shutdownHeartbeatScheduler() {
    scheduledExecutorService.shutdown();
  }

  private void scheduleHeartbeats() {
    scheduledExecutorService.scheduleAtFixedRate(
        this::sendHeartbeats, 0, heartbeatRateMillis, TimeUnit.MILLISECONDS);
  }

  /**
   * Send heartbeats to queued locks. Monitors maximum heartbeat count when provided. If a max
   * heartbeat is provided, the underlying lock will receive at most the provided maximum
   * heartbeats.
   */
  private void sendHeartbeats() {
    if (heartbeatQueue.isEmpty()) {
      return;
    }

    HeartbeatLockRequest heartbeatLockRequest = heartbeatQueue.getFirst();
    if (heartbeatLockRequest.timesUp()) {
      // Informational warning. Lock may expire as it no longer receive heartbeats.
      log.warn(
          "***MAX HEARTBEAT REACHED***. No longer sending heartbeats to {}",
          heartbeatLockRequest.getLock());
      heartbeatQueue.remove(heartbeatLockRequest);
      registry
          .counter(
              heartbeatId
                  .withTag("lockName", heartbeatLockRequest.getLock().getName())
                  .withTag("status", LockHeartbeatStatus.MAX_HEARTBEAT_REACHED.toString()))
          .increment();
    } else {
      try {
        HeartbeatResponse heartbeatResponse = heartbeat(heartbeatLockRequest);
        switch (heartbeatResponse.getLockStatus()) {
          case EXPIRED:
          case ERROR:
            log.warn(
                "Lock status {} for {}",
                heartbeatResponse.getLockStatus(),
                heartbeatResponse.getLock());
            heartbeatQueue.remove(heartbeatLockRequest);
            break;
          default:
            log.debug(
                "Remaining lock duration {}ms. Refreshed lock {}",
                heartbeatLockRequest.getRemainingLockDuration().toMillis(),
                heartbeatResponse.getLock());
            heartbeatLockRequest.setLock(heartbeatResponse.getLock());
        }
      } catch (Exception e) {
        log.error(
            "Heartbeat {} for {} failed", heartbeatLockRequest, heartbeatLockRequest.getLock(), e);
        if (!heartbeatLockRequest.shouldRetry()) {
          heartbeatQueue.remove(heartbeatLockRequest);
        }
      }
    }
  }

  /**
   * A heartbeat will only be accepted if the provided version matches the version stored in Redis.
   * If a heartbeat is accepted, a new version value will be stored with the lock along side a
   * renewed lease and the system timestamp.
   */
  private HeartbeatResponse doHeartbeat(final HeartbeatLockRequest heartbeatLockRequest) {
    // we are aware that the cardinality can get high. To revisit if concerns arise.
    final Lock lock = heartbeatLockRequest.getLock();
    long nextVersion = heartbeatLockRequest.reuseVersion() ? lock.getVersion() : lock.nextVersion();
    Id lockHeartbeat = heartbeatId.withTag("lockName", lock.getName());
    Lock extendedLock = lock;
    try {
      extendedLock = tryUpdateLock(lock, nextVersion);
      registry
          .counter(lockHeartbeat.withTag("status", LockHeartbeatStatus.SUCCESS.toString()))
          .increment();
      return new HeartbeatResponse(extendedLock, LockHeartbeatStatus.SUCCESS);
    } catch (Exception e) {
      if (e instanceof LockExpiredException) {
        registry
            .counter(lockHeartbeat.withTag("status", LockHeartbeatStatus.EXPIRED.toString()))
            .increment();
        return new HeartbeatResponse(extendedLock, LockHeartbeatStatus.EXPIRED);
      }

      log.error("Heartbeat failed for lock {}", extendedLock, e);
      registry
          .counter(lockHeartbeat.withTag("status", LockHeartbeatStatus.ERROR.toString()))
          .increment();
      return new HeartbeatResponse(extendedLock, LockHeartbeatStatus.ERROR);
    }
  }

  private boolean tryLockReleaseQuietly(final Lock lock, boolean wasWorkSuccessful) {
    if (lock != null) {
      try {
        return releaseLock(lock, wasWorkSuccessful);
      } catch (Exception e) {
        log.warn("Attempt to release lock {} failed", lock, e);
        return false;
      }
    }

    return true;
  }

  private boolean matchesLock(LockOptions lockOptions, Lock lock) {
    return ownerName.equals(lock.getOwnerName()) && lockOptions.getVersion() == lock.getVersion();
  }

  private Lock findAuthoritativeLockOrNull(Lock lock) {
    Object payload =
        redisClientDelegate.withScriptingClient(
            c -> {
              return c.eval(
                  FIND_SCRIPT, Arrays.asList(lockKey(lock.getName())), Arrays.asList(ownerName));
            });

    if (payload == null) {
      return null;
    }

    try {
      return objectMapper.readValue(payload.toString(), Lock.class);
    } catch (IOException e) {
      log.error("Failed to get lock info for {}", lock, e);
      return null;
    }
  }

  @Override
  public Lock tryCreateLock(final LockOptions lockOptions) {
    try {
      List<String> attributes =
          Optional.ofNullable(lockOptions.getAttributes()).orElse(Collections.emptyList());
      Object payload =
          redisClientDelegate.withScriptingClient(
              c -> {
                return c.eval(
                    ACQUIRE_SCRIPT,
                    Arrays.asList(lockKey(lockOptions.getLockName())),
                    Arrays.asList(
                        Long.toString(Duration.ofMillis(leaseDurationMillis).toMillis()),
                        Long.toString(Duration.ofMillis(leaseDurationMillis).getSeconds()),
                        Long.toString(lockOptions.getSuccessInterval().toMillis()),
                        Long.toString(lockOptions.getFailureInterval().toMillis()),
                        ownerName,
                        Long.toString(clock.millis()),
                        String.valueOf(lockOptions.getVersion()),
                        lockOptions.getLockName(),
                        String.join(";", attributes)));
              });

      if (payload == null) {
        throw new LockNotAcquiredException(String.format("Lock not acquired %s", lockOptions));
      }

      return objectMapper.readValue(payload.toString(), Lock.class);
    } catch (IOException e) {
      throw new LockNotAcquiredException(String.format("Lock not acquired %s", lockOptions), e);
    }
  }

  private String tryReleaseLock(final Lock lock, boolean wasWorkSuccessful) {
    long releaseTtl =
        wasWorkSuccessful ? lock.getSuccessIntervalMillis() : lock.getFailureIntervalMillis();

    Object payload =
        redisClientDelegate.withScriptingClient(
            c -> {
              return c.eval(
                  RELEASE_SCRIPT,
                  Arrays.asList(lockKey(lock.getName())),
                  Arrays.asList(
                      ownerName,
                      String.valueOf(lock.getVersion()),
                      String.valueOf(Duration.ofMillis(releaseTtl).getSeconds())));
            });

    return payload.toString();
  }

  private Lock tryUpdateLock(final Lock lock, final long nextVersion) {
    Object payload =
        redisClientDelegate.withScriptingClient(
            c -> {
              return c.eval(
                  HEARTBEAT_SCRIPT,
                  Arrays.asList(lockKey(lock.getName())),
                  Arrays.asList(
                      ownerName,
                      String.valueOf(lock.getVersion()),
                      String.valueOf(nextVersion),
                      Long.toString(lock.getLeaseDurationMillis()),
                      Long.toString(clock.millis())));
            });

    if (payload == null) {
      throw new LockExpiredException(String.format("Lock expired %s", lock));
    }

    try {
      return objectMapper.readValue(payload.toString(), Lock.class);
    } catch (IOException e) {
      throw new LockFailedHeartbeatException(String.format("Lock not acquired %s", lock), e);
    }
  }

  interface LockScripts {
    /**
     * Returns 1 if the release is successful, 0 if the release could not be completed (no longer
     * the owner, different version), 2 if the lock no longer exists.
     *
     * <p>ARGS 1: owner 2: previousRecordVersion 3: newRecordVersion
     */
    String RELEASE_SCRIPT =
        ""
            + "local payload = redis.call('GET', KEYS[1]) "
            + "if payload then"
            + " local lock = cjson.decode(payload)"
            + "  if lock['ownerName'] == ARGV[1] and lock['version'] == ARGV[2] then"
            + "    redis.call('EXPIRE', KEYS[1], ARGV[3])"
            + "    return 'SUCCESS'"
            + "  end"
            + "  return 'FAILED_NOT_OWNER' "
            + "end "
            + "return 'SUCCESS_GONE'";

    /**
     * Returns the active lock, whether or not the desired lock was acquired.
     *
     * <p>ARGS 1: leaseDurationMillis 2: owner 3: ownerSystemTimestamp 4: version
     */
    String ACQUIRE_SCRIPT =
        ""
            + "local payload = cjson.encode({"
            + "  ['leaseDurationMillis']=ARGV[1],"
            + "  ['successIntervalMillis']=ARGV[3],"
            + "  ['failureIntervalMillis']=ARGV[4],"
            + "  ['ownerName']=ARGV[5],"
            + "  ['ownerSystemTimestamp']=ARGV[6],"
            + "  ['version']=ARGV[7],"
            + "  ['name']=ARGV[8],"
            + "  ['attributes']=ARGV[9]"
            + "}) "
            + "if redis.call('SET', KEYS[1], payload, 'NX', 'EX', ARGV[2]) == 'OK' then"
            + "  return payload "
            + "end "
            + "return redis.call('GET', KEYS[1])";

    String FIND_SCRIPT =
        ""
            + "local payload = redis.call('GET', KEYS[1]) "
            + "if payload then"
            + "  local lock = cjson.decode(payload)"
            + "  if lock['ownerName'] == ARGV[1] then"
            + "    return redis.call('GET', KEYS[1])"
            + "  end "
            + "end";

    /**
     * Returns 1 if heartbeat was successful, -1 if the lock no longer exists, 0 if the lock is now
     * owned by someone else or is a different version.
     *
     * <p>If the heartbeat is successful, update the lock with the NRV and updated owner system
     * timestamp.
     *
     * <p>ARGS 1: ownerName 2: previousRecordVersion 3: newRecordVersion 4: newleaseDurationMillis
     * 5: updatedOwnerSystemTimestamp
     */
    String HEARTBEAT_SCRIPT =
        ""
            + "local payload = redis.call('GET', KEYS[1]) "
            + "if payload then"
            + "  local lock = cjson.decode(payload)"
            + "  if lock['ownerName'] == ARGV[1] and lock['version'] == ARGV[2] then"
            + "    lock['version']=ARGV[3]"
            + "    lock['leaseDurationMillis']=ARGV[4]"
            + "    lock['ownerSystemTimestamp']=ARGV[5]"
            + "    redis.call('PSETEX', KEYS[1], ARGV[4], cjson.encode(lock))"
            + "    return redis.call('GET', KEYS[1])"
            + "  end "
            + "end";
  }
}
