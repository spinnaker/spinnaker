/*
 * Copyright 2025 Wise, PLC.
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

package com.netflix.spinnaker.clouddriver.kubernetes.caching.agent.streaming;

import static com.netflix.spinnaker.clouddriver.kubernetes.caching.agent.streaming.K8SListWatchAdapter.RESOURCE_VERSION_LIST_ALL;
import static com.netflix.spinnaker.clouddriver.kubernetes.caching.agent.streaming.K8SListWatchAdapter.RESOURCE_VERSION_USE_FROM_CACHE;

import com.netflix.spinnaker.cats.agent.StartupConcurrencyControl;
import com.netflix.spinnaker.cats.agent.StartupConcurrencyPermit;
import com.netflix.spinnaker.clouddriver.kubernetes.caching.Keys;
import com.netflix.spinnaker.clouddriver.kubernetes.caching.agent.KubernetesCacheDataConverter;
import com.netflix.spinnaker.clouddriver.kubernetes.caching.agent.streaming.KubernetesStreamingEvent.Type;
import com.netflix.spinnaker.clouddriver.kubernetes.description.manifest.KubernetesManifest;
import io.kubernetes.client.common.KubernetesObject;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.models.V1ListMeta;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.util.Watch;
import io.kubernetes.client.util.Watchable;
import io.kubernetes.client.util.generic.dynamic.DynamicKubernetesListObject;
import io.kubernetes.client.util.generic.dynamic.DynamicKubernetesObject;
import java.io.InterruptedIOException;
import java.net.ConnectException;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@Slf4j
public class KubernetesStreamingWatcher implements Runnable {

  @FunctionalInterface
  interface Sleeper {
    void sleep(long millis) throws InterruptedException;
  }

  private static final String ERROR_EVENT = "ERROR";
  private static final String BOOKMARK_EVENT = "BOOKMARK";
  private static final String ADDED_EVENT = "ADDED";
  private static final String DELETED_EVENT = "DELETED";
  private static final String MODIFIED_EVENT = "MODIFIED";

  private final Set<String> knownKeys;
  private final State state;
  private final String kind;
  private final String apiGroup;
  private final String account;
  private final BlockingQueue<KubernetesStreamingEvent> eventQueue;
  private final Supplier<Boolean> isRunning;
  private final int retryTimeoutMillis;
  private final int listTimeoutSeconds;
  private final int watchTimeoutSeconds;
  private final K8SListWatchAdapter adapter;
  private final StartupConcurrencyControl concurrencyControl;
  private final Sleeper sleeper;
  private final LongSupplier tickerMillis;
  private final AtomicLong lastHeartbeatTimeMillis = new AtomicLong();
  private final AtomicBoolean heartbeatRecorded = new AtomicBoolean();
  private String lastSyncResourceVersion = RESOURCE_VERSION_USE_FROM_CACHE;
  private final int paginationSize;

  public KubernetesStreamingWatcher(
      K8SListWatchAdapter adapter,
      State state,
      String kind,
      String group,
      String version,
      String account,
      int paginationSize,
      BlockingQueue<KubernetesStreamingEvent> eventQueue,
      Set<Keys.InfrastructureCacheKey> initialKnownKeys,
      int retryTimeoutMillis,
      int listTimeoutSeconds,
      int watchTimeoutSeconds,
      StartupConcurrencyControl concurrencyControl) {
    this(
        adapter,
        state,
        kind,
        group,
        version,
        account,
        paginationSize,
        eventQueue,
        initialKnownKeys,
        retryTimeoutMillis,
        listTimeoutSeconds,
        watchTimeoutSeconds,
        concurrencyControl,
        () -> !Thread.currentThread().isInterrupted());
  }

  KubernetesStreamingWatcher(
      K8SListWatchAdapter adapter,
      State state,
      String kind,
      String group,
      String version,
      String account,
      int paginationSize,
      BlockingQueue<KubernetesStreamingEvent> eventQueue,
      Set<Keys.InfrastructureCacheKey> initialKnownKeys,
      int retryTimeoutMillis,
      int listTimeoutSeconds,
      int watchTimeoutSeconds,
      StartupConcurrencyControl concurrencyControl,
      LongSupplier tickerMillis) {
    this(
        adapter,
        state,
        kind,
        group,
        version,
        account,
        paginationSize,
        eventQueue,
        initialKnownKeys,
        retryTimeoutMillis,
        listTimeoutSeconds,
        watchTimeoutSeconds,
        concurrencyControl,
        () -> !Thread.currentThread().isInterrupted(),
        Thread::sleep,
        tickerMillis);
  }

  public KubernetesStreamingWatcher(
      K8SListWatchAdapter adapter,
      State state,
      String kind,
      String group,
      String version,
      String account,
      int paginationSize,
      BlockingQueue<KubernetesStreamingEvent> eventQueue,
      Set<Keys.InfrastructureCacheKey> initialKnownKeys,
      int retryTimeoutMillis,
      int listTimeoutSeconds,
      int watchTimeoutSeconds,
      StartupConcurrencyControl concurrencyControl,
      Supplier<Boolean> isRunning) {
    this(
        adapter,
        state,
        kind,
        group,
        version,
        account,
        paginationSize,
        eventQueue,
        initialKnownKeys,
        retryTimeoutMillis,
        listTimeoutSeconds,
        watchTimeoutSeconds,
        concurrencyControl,
        isRunning,
        Thread::sleep);
  }

  KubernetesStreamingWatcher(
      K8SListWatchAdapter adapter,
      State state,
      String kind,
      String group,
      String version,
      String account,
      int paginationSize,
      BlockingQueue<KubernetesStreamingEvent> eventQueue,
      Set<Keys.InfrastructureCacheKey> initialKnownKeys,
      int retryTimeoutMillis,
      int listTimeoutSeconds,
      int watchTimeoutSeconds,
      StartupConcurrencyControl concurrencyControl,
      Supplier<Boolean> isRunning,
      Sleeper sleeper) {
    this(
        adapter,
        state,
        kind,
        group,
        version,
        account,
        paginationSize,
        eventQueue,
        initialKnownKeys,
        retryTimeoutMillis,
        listTimeoutSeconds,
        watchTimeoutSeconds,
        concurrencyControl,
        isRunning,
        sleeper,
        systemTickerMillis());
  }

  KubernetesStreamingWatcher(
      K8SListWatchAdapter adapter,
      State state,
      String kind,
      String group,
      String version,
      String account,
      int paginationSize,
      BlockingQueue<KubernetesStreamingEvent> eventQueue,
      Set<Keys.InfrastructureCacheKey> initialKnownKeys,
      int retryTimeoutMillis,
      int listTimeoutSeconds,
      int watchTimeoutSeconds,
      StartupConcurrencyControl concurrencyControl,
      Supplier<Boolean> isRunning,
      Sleeper sleeper,
      LongSupplier tickerMillis) {
    this.adapter = adapter;
    this.state = state;
    this.account = account;
    this.paginationSize = paginationSize;
    this.kind = kind;
    this.apiGroup = StringUtils.isBlank(group) ? version : group + "/" + version;
    this.eventQueue = eventQueue;
    this.knownKeys =
        initialKnownKeys.stream()
            .map(KubernetesStreamingWatcher::toKey)
            .collect(Collectors.toSet());
    this.retryTimeoutMillis = retryTimeoutMillis;
    this.listTimeoutSeconds = listTimeoutSeconds;
    this.watchTimeoutSeconds = watchTimeoutSeconds;
    this.concurrencyControl = concurrencyControl;
    this.isRunning = isRunning;
    this.sleeper = sleeper;
    this.tickerMillis = tickerMillis;
  }

  public void run() {
    while (isRunning.get()) {
      try {
        try (StartupConcurrencyPermit permit = concurrencyControl.acquire()) {
          log.info(
              "{}:{}:: Starting initial syncList (resourceVersion: {})",
              account,
              watcherId(),
              lastSyncResourceVersion);
          syncList();
        }

        boolean watchActive = true;
        while (isRunning.get() && watchActive) {
          Watchable<DynamicKubernetesObject> watch;
          try {
            watch = adapter.watch(watchTimeoutSeconds, lastSyncResourceVersion);
          } catch (Exception e) {
            if (!Thread.currentThread().isInterrupted()
                && hasCause(e, SocketTimeoutException.class)) {
              log.debug(
                  "{}:{}:: Watch open timed out, retrying from resourceVersion {}.",
                  account,
                  watcherId(),
                  lastSyncResourceVersion);
              if (!sleepBeforeRetry()) {
                return;
              }
              continue;
            }
            throw e;
          }

          try (watch) {
            recordHeartbeat();
            watchActive = handle(watch);
          } catch (Exception e) {
            if (!Thread.currentThread().isInterrupted()
                && hasCause(e, SocketTimeoutException.class)) {
              log.debug(
                  "{}:{}:: Watch timed out, reconnecting from resourceVersion {}.",
                  account,
                  watcherId(),
                  lastSyncResourceVersion);
              continue;
            }
            throw e;
          }
        }
      } catch (ApiException e) {
        if (Thread.currentThread().isInterrupted()) {
          log.info("{}:{}:: Kubernetes watcher has been interrupted.", account, watcherId(), e);
          Thread.currentThread().interrupt();
          return;
        } else if (hasCause(e, InterruptedIOException.class)
            && !hasCause(e, SocketTimeoutException.class)) {
          log.info("{}:{}:: Kubernetes watcher has been interrupted.", account, watcherId(), e);
          Thread.currentThread().interrupt();
          return;
        } else if (e.getCode() == HttpURLConnection.HTTP_GONE) {
          log.info("{}:{}:: Restarting watching.", account, watcherId());
          lastSyncResourceVersion = RESOURCE_VERSION_LIST_ALL;
        } else if (hasCause(e, ConnectException.class)) {
          log.warn(
              "{}:{}:: Error connecting to Kubernetes API, retrying in {} ms.",
              account,
              watcherId(),
              retryTimeoutMillis);
          if (!sleepBeforeRetry()) {
            return;
          }
        } else {
          if (hasCause(e, SocketTimeoutException.class)) {
            log.debug("{}:{}:: List timed out, retrying.", account, watcherId());
          } else {
            log.warn("{}:{}:: Error watching Kubernetes objects.", account, watcherId(), e);
          }
          if (!sleepBeforeRetry()) {
            return;
          }
        }
      } catch (InterruptedException ie) {
        log.info("{}:{}:: Kubernetes watcher has been interrupted.", account, watcherId(), ie);
        Thread.currentThread().interrupt();
        return;
      } catch (Exception e) {
        if (Thread.currentThread().isInterrupted()) {
          log.info("{}:{}:: Kubernetes watcher has been interrupted.", account, watcherId(), e);
          Thread.currentThread().interrupt();
          return;
        } else if (hasCause(e, InterruptedIOException.class)
            && !hasCause(e, SocketTimeoutException.class)) {
          log.info("{}:{}:: Kubernetes watcher has been interrupted.", account, watcherId(), e);
          Thread.currentThread().interrupt();
          return;
        } else {
          if (hasCause(e, SocketTimeoutException.class)) {
            log.debug("{}:{}:: List timed out, retrying.", account, watcherId());
          } else {
            log.warn("{}:{}:: Error watching Kubernetes objects.", account, watcherId(), e);
          }
          if (!sleepBeforeRetry()) {
            return;
          }
        }
      }
    }
  }

  /**
   * Lists all kubernetes objects either from cache if `lastSyncResourceVersion` is set to `0` or
   * bypassing cache if set to empty string.
   *
   * @return the last sync version to be used in future lists and watches
   * @throws ApiException
   * @throws InterruptedException
   */
  private void syncList() throws ApiException, InterruptedException {
    Set<String> seenKeys = new HashSet<>();
    String lastContinue = null;
    String resourceVersion = null;
    do {
      if (Thread.interrupted()) {
        throw new InterruptedException("syncList::list is interrupted");
      }
      DynamicKubernetesListObject list =
          adapter.list(listTimeoutSeconds, lastSyncResourceVersion, paginationSize, lastContinue);
      recordHeartbeat();

      V1ListMeta listMeta = list.getMetadata();
      resourceVersion = listMeta.getResourceVersion();

      List<DynamicKubernetesObject> items = list.getItems();

      for (DynamicKubernetesObject obj : items) {
        if (Thread.interrupted()) {
          throw new InterruptedException("syncList::newItems is interrupted");
        }
        seenKeys.add(toKey(obj));
        fillMissedFields(obj);
        KubernetesManifest manifest = convert(obj);

        if (manifest == null) {
          continue;
        }

        state.enqueueEvent(eventQueue, new KubernetesStreamingEvent(Type.UPSERT, manifest));
      }
      if (paginationSize > 0) {
        if (paginationSize < items.size()) {
          log.debug(
              "{}:{}:: Paginated List returned {} items. Limit ({}) possibly ignored",
              account,
              watcherId(),
              items.size(),
              paginationSize);
        } else {
          log.debug(
              "{}:{}:: Paginated List returned {} items. Estimated remaining items {}",
              account,
              watcherId(),
              items.size(),
              listMeta.getRemainingItemCount() == null
                  ? "unknown"
                  : listMeta.getRemainingItemCount());
        }
      }
      lastContinue = listMeta.getContinue();
    } while (paginationSize > 0 && !StringUtils.isEmpty(lastContinue));
    lastSyncResourceVersion = resourceVersion;

    log.debug("{}:{}:: List resulted in {} UPSERTS", account, watcherId(), seenKeys.size());

    Set<String> deletedKeys =
        knownKeys.stream().filter(key -> !seenKeys.contains(key)).collect(Collectors.toSet());

    List<KubernetesStreamingEvent> deleteEvents =
        deletedKeys.stream()
            .map(this::getEmptyObject)
            .map(
                obj -> {
                  fillMissedFields(obj);
                  KubernetesManifest manifest = convert(obj);
                  return new KubernetesStreamingEvent(Type.DELETE, manifest);
                })
            .toList();
    knownKeys.addAll(seenKeys);
    knownKeys.removeAll(deletedKeys);

    for (KubernetesStreamingEvent event : deleteEvents) {
      if (Thread.interrupted()) {
        throw new InterruptedException("syncList::deleteEvents is interrupted");
      }
      state.enqueueEvent(eventQueue, event);
    }

    log.debug("{}:{}:: List resulted in {} DELETES", account, watcherId(), deletedKeys.size());
  }

  private DynamicKubernetesObject getEmptyObject(String key) {
    String[] split = StringUtils.split(key, "/", 2);
    V1ObjectMeta meta = new V1ObjectMeta();
    if (split.length == 1) {
      meta.setName(split[0]);
    } else {
      meta.setNamespace(split[0]);
      meta.setName(split[1]);
    }
    DynamicKubernetesObject obj = new DynamicKubernetesObject();
    obj.setMetadata(meta);

    return obj;
  }

  private static String toKey(DynamicKubernetesObject obj) {
    return toKey(obj.getMetadata().getNamespace(), obj.getMetadata().getName());
  }

  private static String toKey(Keys.InfrastructureCacheKey key) {
    return toKey(key.getNamespace(), key.getName());
  }

  private static String toKey(String namespace, String name) {
    if (StringUtils.isBlank(namespace)) {
      return name;
    }
    return String.format("%s/%s", namespace, name);
  }

  private boolean handle(Watchable<DynamicKubernetesObject> watch) throws InterruptedException {
    int eventCount = 0;
    Set<String> seenKeys = new HashSet<>();
    Set<String> deletedKeys = new HashSet<>();
    try {
      while (isRunning.get() && watch.hasNext()) {
        eventCount++;
        Watch.Response<DynamicKubernetesObject> response = watch.next();
        recordHeartbeat();
        String eventType = response.type.toUpperCase();
        log.debug("{}:{}:: Received event type: {}", account, watcherId(), eventType);
        if (ERROR_EVENT.equals(eventType)) {
          if (response.status != null && response.status.getCode() == HttpURLConnection.HTTP_GONE) {
            log.info(
                "{}:{}:: Restarting watching after HTTP {}",
                account,
                watcherId(),
                response.status.getCode());
            lastSyncResourceVersion = RESOURCE_VERSION_LIST_ALL;
            return false;
          }
          log.error("{}:{}:: Received error event: {}", account, watcherId(), response.object);
          sleeper.sleep(retryTimeoutMillis);
          return true;
        }
        DynamicKubernetesObject obj = response.object;

        V1ObjectMeta meta = obj.getMetadata();
        if (meta == null) {
          continue;
        }

        lastSyncResourceVersion = meta.getResourceVersion();
        if (BOOKMARK_EVENT.equals(eventType)) {
          continue;
        }

        fillMissedFields(obj);
        KubernetesManifest manifest = convert(obj);
        if (manifest == null) {
          continue;
        }

        try {
          KubernetesStreamingEvent event =
              new KubernetesStreamingEvent(getEventType(eventType), manifest);
          state.enqueueEvent(eventQueue, event);
          String key = toKey(obj);
          if (event.getType() == Type.UPSERT) {
            seenKeys.add(key);
          } else {
            deletedKeys.add(key);
          }
        } catch (IllegalArgumentException e) {
          log.warn("{}:{}:: unsupported event type: {}", account, watcherId(), eventType);
        }
      }
      return true;
    } finally {
      knownKeys.addAll(seenKeys);
      knownKeys.removeAll(deletedKeys);
      log.debug("{}:{}:: Watcher processed {} events", account, watcherId(), eventCount);
    }
  }

  private Type getEventType(String eventType) {
    return switch (eventType) {
      case MODIFIED_EVENT, ADDED_EVENT -> Type.UPSERT;
      case DELETED_EVENT -> Type.DELETE;
      default -> throw new IllegalArgumentException("Unsupported event type: " + eventType);
    };
  }

  private void fillMissedFields(DynamicKubernetesObject obj) {
    // https://github.com/kubernetes-client/java/issues/4006
    if (obj.getRaw().get("kind") == null) {
      obj.setKind(kind);
    }
    if (obj.getRaw().get("apiVersion") == null) {
      obj.setApiVersion(apiGroup);
    }
  }

  @Nullable
  private KubernetesManifest convert(DynamicKubernetesObject obj) {
    try {
      return KubernetesCacheDataConverter.getResource(obj.getRaw(), KubernetesManifest.class);
    } catch (Exception e) {
      log.error(
          "{}:{}:: Failed to convert kubernetes object to manifest: {}",
          account,
          watcherId(),
          safeGetName(obj),
          e);
      return null;
    }
  }

  private static String safeGetName(KubernetesObject obj) {
    if (obj == null || obj.getMetadata() == null) {
      return "null";
    }
    return obj.getMetadata().getName();
  }

  private static boolean hasCause(Throwable throwable, Class<? extends Throwable> causeType) {
    Throwable cause = throwable;
    while (cause != null) {
      if (causeType.isInstance(cause)) {
        return true;
      }
      cause = cause.getCause();
    }
    return false;
  }

  private boolean sleepBeforeRetry() {
    try {
      sleeper.sleep(retryTimeoutMillis);
      return true;
    } catch (InterruptedException e) {
      log.info("{}:{}:: Kubernetes watcher has been interrupted.", account, watcherId(), e);
      Thread.currentThread().interrupt();
      return false;
    }
  }

  private void recordHeartbeat() {
    lastHeartbeatTimeMillis.set(tickerMillis.getAsLong());
    heartbeatRecorded.set(true);
  }

  long getLastHeartbeatTimeMillis() {
    return lastHeartbeatTimeMillis.get();
  }

  boolean hasRecordedHeartbeat() {
    return heartbeatRecorded.get();
  }

  static LongSupplier systemTickerMillis() {
    return () -> TimeUnit.NANOSECONDS.toMillis(System.nanoTime());
  }

  String watcherId() {
    return String.format("Kubernetes Watcher[%s/%s]", apiGroup, kind);
  }
}
