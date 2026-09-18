/*
 * Copyright 2026 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.kork.metrics;

import com.sun.management.GarbageCollectionNotificationInfo;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.List;
import javax.management.ListenerNotFoundException;
import javax.management.Notification;
import javax.management.NotificationEmitter;
import javax.management.NotificationListener;
import javax.management.openmbean.CompositeData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Logs a line for each garbage collection pause observed on any {@link GarbageCollectorMXBean}.
 * This preserves the console-logging behavior previously provided by Spectator's GcLogger; GC
 * *metrics* are handled separately by Micrometer's auto-registered JvmGcMetrics binder.
 */
public class GcPauseLogger {
  private static final Logger log = LoggerFactory.getLogger(GcPauseLogger.class);

  private final NotificationListener listener = this::onNotification;
  private final List<NotificationEmitter> emitters = new ArrayList<>();

  public void start() {
    for (GarbageCollectorMXBean bean : ManagementFactory.getGarbageCollectorMXBeans()) {
      if (bean instanceof NotificationEmitter) {
        NotificationEmitter emitter = (NotificationEmitter) bean;
        emitter.addNotificationListener(listener, null, null);
        emitters.add(emitter);
      }
    }
  }

  public void stop() {
    for (NotificationEmitter emitter : emitters) {
      try {
        emitter.removeNotificationListener(listener);
      } catch (ListenerNotFoundException e) {
        // already removed
      }
    }
    emitters.clear();
  }

  private void onNotification(Notification notification, Object handback) {
    if (!GarbageCollectionNotificationInfo.GARBAGE_COLLECTION_NOTIFICATION.equals(
        notification.getType())) {
      return;
    }
    GarbageCollectionNotificationInfo info =
        GarbageCollectionNotificationInfo.from((CompositeData) notification.getUserData());
    log.info(
        "GC {} ({}, cause: {}) duration={}ms",
        info.getGcName(),
        info.getGcAction(),
        info.getGcCause(),
        info.getGcInfo().getDuration());
  }
}
