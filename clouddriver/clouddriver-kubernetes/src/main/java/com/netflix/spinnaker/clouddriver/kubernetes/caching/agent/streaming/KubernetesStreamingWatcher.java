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
import java.net.ConnectException;
import java.net.HttpURLConnection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@Slf4j
public class KubernetesStreamingWatcher implements Runnable {

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
  private final int watchTimeoutSeconds;
  private final K8SListWatchAdapter adapter;
  private String lastSyncResourceVersion = RESOURCE_VERSION_USE_FROM_CACHE;

  public KubernetesStreamingWatcher(
      K8SListWatchAdapter adapter,
      State state,
      String kind,
      String group,
      String version,
      String account,
      BlockingQueue<KubernetesStreamingEvent> eventQueue,
      Set<Keys.InfrastructureCacheKey> initialKnownKeys,
      int retryTimeoutMillis,
      int watchTimeoutSeconds) {
    this(
        adapter,
        state,
        kind,
        group,
        version,
        account,
        eventQueue,
        initialKnownKeys,
        retryTimeoutMillis,
        watchTimeoutSeconds,
        () -> !Thread.currentThread().isInterrupted());
  }

  public KubernetesStreamingWatcher(
      K8SListWatchAdapter adapter,
      State state,
      String kind,
      String group,
      String version,
      String account,
      BlockingQueue<KubernetesStreamingEvent> eventQueue,
      Set<Keys.InfrastructureCacheKey> initialKnownKeys,
      int retryTimeoutMillis,
      int watchTimeoutSeconds,
      Supplier<Boolean> isRunning) {
    this.adapter = adapter;
    this.state = state;
    this.account = account;
    this.kind = kind;
    this.apiGroup = StringUtils.isBlank(group) ? version : group + "/" + version;
    this.eventQueue = eventQueue;
    this.knownKeys =
        initialKnownKeys.stream()
            .map(KubernetesStreamingWatcher::toKey)
            .collect(Collectors.toSet());
    this.retryTimeoutMillis = retryTimeoutMillis;
    this.watchTimeoutSeconds = watchTimeoutSeconds;
    this.isRunning = isRunning;
  }

  public void run() {
    while (isRunning.get()) {
      try {

        this.lastSyncResourceVersion = syncList();

        boolean watchActive = true;
        while (isRunning.get() && watchActive) {

          try (Watchable<DynamicKubernetesObject> watch =
              adapter.watch(watchTimeoutSeconds, lastSyncResourceVersion)) {
            watchActive = handle(watch);
          }
        }
      } catch (ApiException e) {
        if (e.getCode() == HttpURLConnection.HTTP_GONE) {
          log.info("{}:{}:: Restarting watching.", account, watcherId());
          lastSyncResourceVersion = RESOURCE_VERSION_LIST_ALL;
        } else if (e.getCause() instanceof ConnectException) {
          log.warn(
              "{}:{}:: Error connecting to Kubernetes API, retrying in {} ms.",
              account,
              watcherId(),
              retryTimeoutMillis);
          try {
            Thread.currentThread().sleep(retryTimeoutMillis);
          } catch (InterruptedException ie) {
            log.info("{}:{}:: Kubernetes watcher has been interrupted.", account, watcherId(), ie);
            return;
          }
        } else {
          log.warn("{}:{}:: Error watching Kubernetes objects.", account, watcherId(), e);
        }
      } catch (InterruptedException ie) {
        log.info("{}:{}:: Kubernetes watcher has been interrupted.", account, watcherId(), ie);
        return;
      } catch (Exception e) {
        log.warn("{}:{}:: Error watching Kubernetes objects.", account, watcherId(), e);
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
  private String syncList() throws ApiException, InterruptedException {
    DynamicKubernetesListObject list = adapter.list(lastSyncResourceVersion);

    V1ListMeta listMeta = list.getMetadata();
    String resourceVersion = listMeta.getResourceVersion();
    List<DynamicKubernetesObject> items = list.getItems();

    Set<String> seenKeys = new HashSet<>();

    for (DynamicKubernetesObject obj : items) {
      seenKeys.add(toKey(obj));
      fillMissedFields(obj);
      KubernetesManifest manifest = convert(obj);

      if (manifest == null) {
        continue;
      }

      eventQueue.put(new KubernetesStreamingEvent(Type.UPSERT, manifest));
    }

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
      eventQueue.put(event);
    }

    log.debug("{}:{}:: List resulted in {} DELETES", account, watcherId(), deletedKeys.size());

    return resourceVersion;
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

  private boolean handle(Watchable<DynamicKubernetesObject> watch) {
    int eventCount = 0;
    Set<String> seenKeys = new HashSet<>();
    Set<String> deletedKeys = new HashSet<>();
    try {
      while (isRunning.get() && watch.hasNext()) {
        eventCount++;
        Watch.Response<DynamicKubernetesObject> response = watch.next();
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
          return true;
        }
        state.updateLastReceivedEventTime();

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
          eventQueue.put(event);
          String key = toKey(obj);
          if (event.getType() == Type.UPSERT) {
            seenKeys.add(key);
          } else {
            deletedKeys.add(key);
          }
        } catch (InterruptedException e) {
          throw new RuntimeException(e);
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

  private String watcherId() {
    return String.format("Kubernetes Watcher[%s/%s]", apiGroup, kind);
  }
}
