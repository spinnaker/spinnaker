/*
 * Copyright 2016 Google, Inc.
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

package com.netflix.spinnaker.clouddriver.kubernetes.model

import groovy.util.logging.Slf4j
import io.fabric8.kubernetes.api.model.Event

@Slf4j
class KubernetesEvent {
  String message
  Reason reason
  Severity type
  Integer count
  Long firstOccurrence
  Long lastOccurrence

  KubernetesEvent(Event event) {
    this.message = event.message
    this.count = event.count
    switch (event.reason) {
      case "FailedScheduling":
        this.reason = Reason.Scheduling
        break
      case "Unhealthy":
        this.reason = Reason.Unhealthy
        break
      case "Mount":
        this.reason = Reason.Mount
        break
      case "Sync":
        this.reason = Reason.Sync
        break
      default:
        this.reason = Reason.Unknown
        log.info "Unknown event reason: ${event.reason}"
        break
    }

    switch (event.type) {
      case "Warning":
        this.type = Severity.Warning
        break
      case "Normal":
        this.type = Severity.Normal
        break
      default:
        this.type = Severity.Unknown
        log.info "Unknown event severity: ${event.type}"
        break
    }

    this.firstOccurrence = KubernetesModelUtil.translateTime(event.firstTimestamp)
    this.lastOccurrence = KubernetesModelUtil.translateTime(event.lastTimestamp)
  }
}

enum Reason {
  Scheduling,
  Unhealthy,
  Mount,
  Sync,
  Unknown,
}

enum Severity {
  Warning,
  Normal,
  Unknown,
}
