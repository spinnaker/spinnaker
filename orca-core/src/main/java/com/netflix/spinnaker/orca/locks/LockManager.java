package com.netflix.spinnaker.orca.locks;

import java.util.Objects;

/**
 * Manages acquisition / release of locks.
 *
 * The lock is held with a value which is the combination of application, type, and id. This
 * would typically be the execution application, type (pipeline or orchestration) and execution id.
 *
 * A named lock can be acquired if either it is not currently locked, or if the current value of the
 * lock matches the supplied lockValue fields.
 *
 * A lock has a TTL to ensure that in the event of an unexpected failure or JVM exit that eventually locks
 * are released - the general expectation is that a call to acquireLock should be accompanied by a call to
 * releaseLock.
 *
 * Multiple holders can acquire the same lock by supplying the same lockValue fields, and a lock continues to be
 * held until all holders have issued a releaseLock.
 *
 * A stage that operates on a particular cluster should acquire the lock for that cluster. Any synthetic stages
 * can also acquire the same lock. For example:
 *
 * As an example, a deploy with Red/Black would go through the following:
 *
 *
 * lockValue = new LockValue(application, execution.type, execution.id)
 * createServerGroup
 *   acquireLock(clusterName, lockValue, createServerGroup.stage.id)
 *   deploy
 *   waitForUpInstances
 *
 *   disableCluster
 *     acquireLock(clusterName, lockValue, disableCluster.stage.id)
 *     disableCluster
 *     waitForDisableCluster
 *     releaseLock(clusterName, lockValue, disableCluster.stage.id)
 *
 *   scaleDownCluster
 *     acquireLock(clusterName, lockValue, scaleDownCluster.stage.id)
 *     scaleDownCluster
 *     waitForScaleDown
 *     releaseLock(clusterName, lockValue, scaleDownCluster.stage.id)
 *
 *   releaseLock(clusterName, lockValue, createServerGroup.stage.id)
 *
 *
 * The lifespan of the lock would be the entire duration of the createServerGroup stage including all
 * the child stages (disableCluster, scaleDownCluster).
 */
public interface LockManager {

  class LockValue {
    final String application;
    final String type;
    final String id;

    public LockValue(String application, String type, String id) {
      this.application = Objects.requireNonNull(application);
      this.type = Objects.requireNonNull(type);
      this.id = Objects.requireNonNull(id);
    }

    public String getApplication() {
      return application;
    }

    public String getType() {
      return type;
    }

    public String getId() {
      return id;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      LockValue lockValue = (LockValue) o;
      return Objects.equals(application, lockValue.application) &&
        Objects.equals(type, lockValue.type) &&
        Objects.equals(id, lockValue.id);
    }

    @Override
    public int hashCode() {
      return Objects.hash(application, type, id);
    }

    @Override
    public String toString() {
      return "LockValue{" +
        "application='" + application + '\'' +
        ", type='" + type + '\'' +
        ", id='" + id + '\'' +
        '}';
    }
  }

  /**
   * Acquires a named lock.
   * @param lockName The name of the lock.
   * @param lockValue The value of the lock - if the lock is already held with this value, acquisition is successful otherwise lock acquisition fails.
   * @param lockHolder The holder of the lock.
   * @param ttlSeconds How long to acquire for or extend an existing lock for.
   * @throws LockFailureException if the lock is currently held with a different lockValueApplication/lockValueType/lockValue
   */
  void acquireLock(String lockName, LockValue lockValue, String lockHolder, int ttlSeconds) throws LockFailureException;

  /**
   * Extends a named lock ttl.
   * @param lockName The name of the lock.
   * @param lockValue The value of the lock - if the lock is already held with this value, the ttl extension is successful otherwise lock extension fails.
   * @param ttlSeconds How long to extend the lock for.
   * @throws LockFailureException if the lock is currently held with a different lockValueApplication/lockValueType/lockValue
   */
  void extendLock(String lockName, LockValue lockValue, int ttlSeconds) throws LockFailureException;

  /**
   * Releases a named lock for a specific lockHolder.
   *
   * After release, if there are no additional lock holders the lock itself is freed.
   *
   * @param lockName The name of the lock.
   * @param lockValue The value of the lock - An existing lock must be held with this value for release to succeed.
   * @param lockHolder The holder of the lock.
   */
  void releaseLock(String lockName, LockValue lockValue, String lockHolder);
}
