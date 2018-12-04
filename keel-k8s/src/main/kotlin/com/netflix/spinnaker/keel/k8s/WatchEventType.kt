package com.netflix.spinnaker.keel.k8s

import io.kubernetes.client.util.Watch

/**
 * K8s API does not provide an enum for [io.kubernetes.client.util.Watch.Response.type].
 */
enum class WatchEventType {
  ADDED, MODIFIED, DELETED, ERROR
}

val <T> Watch.Response<T>.eventType: WatchEventType
  get() = WatchEventType.valueOf(type)
