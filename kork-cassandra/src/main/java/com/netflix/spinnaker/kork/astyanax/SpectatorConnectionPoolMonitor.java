/*
 * Copyright 2015 Netflix, Inc.
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

package com.netflix.spinnaker.kork.astyanax;

import com.google.common.base.Preconditions;
import com.netflix.astyanax.connectionpool.ConnectionPoolMonitor;
import com.netflix.astyanax.connectionpool.Host;
import com.netflix.astyanax.connectionpool.HostConnectionPool;
import com.netflix.astyanax.connectionpool.impl.CountingConnectionPoolMonitor;
import com.netflix.spectator.api.Id;
import com.netflix.spectator.api.Registry;

import java.util.Optional;

public class SpectatorConnectionPoolMonitor extends CountingConnectionPoolMonitor {

  public static KeyspaceConnectionPoolMonitorFactory factory(final Registry registry) {
    return new KeyspaceConnectionPoolMonitorFactory() {
      @Override
      public ConnectionPoolMonitor createMonitorForKeyspace(String clusterName, String keyspace) {
        return new SpectatorConnectionPoolMonitor(registry, clusterName, keyspace);
      }
    };
  }

  private final Registry registry;
  private final String cluster;
  private final String keyspace;

  public SpectatorConnectionPoolMonitor(Registry registry, String cluster, String keyspace) {
    this.registry = Preconditions.checkNotNull(registry);
    this.cluster = Preconditions.checkNotNull(cluster);
    this.keyspace = Preconditions.checkNotNull(keyspace);
  }

  @Override
  public void incOperationFailure(Host host, Exception reason) {
    super.incOperationFailure(host, reason);
    registry.counter(hostId("operationFailure", host, reason)).increment();
  }

  Id baseId(String name) {
    return registry.createId("astyanax.connectionPool." + name, "cluster", cluster, "keyspace", keyspace);
  }

  Id hostId(String name, Host host) {
    String rack = Optional.ofNullable(host).map(Host::getRack).orElse("unknown");
    return baseId(name).withTag("rack", rack);
  }

  Id hostId(String name, Host host, Exception cause) {
    String causeTag = Optional.ofNullable(cause).map(Object::getClass).map(Class::getSimpleName).orElse("unknown");
    return hostId(name, host).withTag("cause", causeTag);
  }

  @Override
  public void incOperationSuccess(Host host, long latency) {
    super.incOperationSuccess(host, latency);
    registry.counter(hostId("operationSuccess", host)).increment();
    registry.distributionSummary(hostId("operationLatency", host)).record(latency);
  }

  @Override
  public void incConnectionCreated(Host host) {
    super.incConnectionCreated(host);
    registry.counter(hostId("connectionCreated", host)).increment();
  }

  @Override
  public void incConnectionClosed(Host host, Exception reason) {
    super.incConnectionClosed(host, reason);
    registry.counter(hostId("connectionClosed", host, reason)).increment();
  }

  @Override
  public void incConnectionCreateFailed(Host host, Exception reason) {
    super.incConnectionCreateFailed(host, reason);
    registry.counter(hostId("connectionCreateFailed", host, reason)).increment();
  }

  @Override
  public void incConnectionBorrowed(Host host, long delay) {
    super.incConnectionBorrowed(host, delay);
    registry.counter(hostId("connectionBorrowed", host)).increment();
    registry.distributionSummary(hostId("connectionBorrowedDelay", host)).record(delay);
  }

  @Override
  public void incConnectionReturned(Host host) {
    super.incConnectionReturned(host);
    registry.counter(hostId("connectionReturned", host)).increment();
  }

  @Override
  public void incFailover(Host host, Exception reason) {
    super.incFailover(host, reason);
    registry.counter(hostId("failover", host, reason)).increment();
  }

  @Override
  public void onHostAdded(Host host, HostConnectionPool<?> pool) {
    super.onHostAdded(host, pool);
    registry.counter(hostId("hostAdded", host)).increment();
  }

  @Override
  public void onHostRemoved(Host host) {
    super.onHostRemoved(host);
    registry.counter(hostId("hostRemoved", host)).increment();
  }

  @Override
  public void onHostDown(Host host, Exception reason) {
    super.onHostDown(host, reason);
    registry.counter(hostId("hostDown", host, reason)).increment();
  }

  @Override
  public void onHostReactivated(Host host, HostConnectionPool<?> pool) {
    super.onHostReactivated(host, pool);
    registry.counter(hostId("hostReactivated", host)).increment();
  }

  @Override
  public long getBadRequestCount() {
    return super.getBadRequestCount();
  }
}
