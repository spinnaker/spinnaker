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

import com.netflix.spinnaker.clouddriver.kubernetes.caching.agent.KubernetesCacheDataConverter;
import com.netflix.spinnaker.clouddriver.kubernetes.caching.agent.streaming.KubernetesStreamingEvent.Type;
import com.netflix.spinnaker.clouddriver.kubernetes.description.manifest.KubernetesManifest;
import io.kubernetes.client.common.KubernetesObject;
import io.kubernetes.client.informer.ResourceEventHandler;
import io.kubernetes.client.util.generic.dynamic.DynamicKubernetesObject;
import java.util.concurrent.BlockingQueue;
import javax.annotation.Nullable;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@Slf4j
class KubernetesStreamingWatcher implements ResourceEventHandler<DynamicKubernetesObject> {

  private final State state;
  private final String kind;
  private final String apiGroup;
  private final BlockingQueue<KubernetesStreamingEvent> eventQueue;

  public KubernetesStreamingWatcher(
      State state,
      String kind,
      String group,
      String version,
      BlockingQueue<KubernetesStreamingEvent> eventQueue) {
    this.state = state;
    this.kind = kind;
    this.apiGroup = StringUtils.isBlank(group) ? version : group + "/" + version;
    this.eventQueue = eventQueue;
  }

  @Override
  public void onAdd(DynamicKubernetesObject obj) {
    process(Type.UPSERT, obj);
  }

  @Override
  public void onUpdate(DynamicKubernetesObject oldObj, DynamicKubernetesObject newObj) {
    process(Type.UPSERT, newObj);
  }

  @Override
  public void onDelete(DynamicKubernetesObject obj, boolean deletedFinalStateUnknown) {
    process(Type.DELETE, obj);
  }

  private void process(Type eventType, DynamicKubernetesObject obj) {
    if (obj == null || obj.getMetadata() == null) {
      log.warn("{}:: Received null object in {}", watcherId(), eventType);
      return;
    }
    fillMissedFields(obj);
    KubernetesManifest manifest = convert(obj);
    if (manifest == null) {
      return;
    }

    try {
      eventQueue.put(new KubernetesStreamingEvent(eventType, manifest));
    } catch (InterruptedException e) {
      log.info("Terminating {} - {}...", watcherId(), eventType);
      Thread.currentThread().interrupt();
      return;
    }

    state.updateLastReceivedEventTime();
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
          "{}:: Failed to convert kubernetes object to manifest: {}",
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
