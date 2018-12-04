/*
 * Copyright 2018 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.spinnaker.keel.plugin

import com.google.gson.reflect.TypeToken
import com.netflix.spinnaker.keel.api.Asset
import com.netflix.spinnaker.keel.k8s.WatchEventType
import com.netflix.spinnaker.keel.k8s.WatchEventType.ADDED
import com.netflix.spinnaker.keel.k8s.WatchEventType.DELETED
import com.netflix.spinnaker.keel.k8s.WatchEventType.MODIFIED
import com.netflix.spinnaker.keel.k8s.eventType
import com.netflix.spinnaker.keel.api.AssetKind
import com.netflix.spinnaker.keel.persistence.AssetRepository
import com.netflix.spinnaker.keel.persistence.ResourceVersionTracker
import com.squareup.okhttp.Call
import io.kubernetes.client.apis.ApiextensionsV1beta1Api
import io.kubernetes.client.apis.CustomObjectsApi
import io.kubernetes.client.models.V1beta1CustomResourceDefinition
import io.kubernetes.client.util.Config
import io.kubernetes.client.util.Watch
import io.kubernetes.client.util.Watch.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.slf4j.LoggerFactory
import java.net.SocketException
import java.net.SocketTimeoutException
import javax.annotation.PostConstruct
import javax.annotation.PreDestroy

internal class AssetPluginKubernetesAdapter(
  private val assetRepository: AssetRepository,
  private val resourceVersionTracker: ResourceVersionTracker,
  private val extensionsApi: ApiextensionsV1beta1Api,
  private val customObjectsApi: CustomObjectsApi,
  private val plugin: AssetPlugin
) {
  private var job: Job? = null
  private var calls: MutableMap<String, Call> = mutableMapOf()

  private val AssetKind.crd: String
    get() = "$plural.$group"

  @PostConstruct
  fun start() {
    log.info("Starting Kubernetes agent for {}", plugin.name)

    if (job != null) throw IllegalStateException("Watcher for ${plugin.name} already running")
    job = GlobalScope.launch {
      for ((kind, type) in plugin.supportedKinds) {
        launch {
          val crd = extensionsApi
            .readCustomResourceDefinition(kind.crd, "true", null, null)
          watchForResourceChanges(crd, type)
        }
      }
    }

    log.info("Launched {} watchers", job!!.children.toList().size)
  }

  @PreDestroy
  fun stop() {
    log.info("Stopping Kubernetes agent for {}", plugin.name)

    runBlocking {
      job?.cancel()
      calls.forEach { _, call -> call.cancel() }
      job?.join()
    }
  }

  private suspend fun <T : Any> CoroutineScope.watchForResourceChanges(
    crd: V1beta1CustomResourceDefinition,
    type: Class<T>
  ) {
    while (isActive) {
      val call = customObjectsApi.listClusterCustomObjectCall(
        crd.spec.group,
        crd.spec.version,
        crd.spec.names.plural,
        "true",
        null,
        "${resourceVersionTracker.get()}",
        true,
        null,
        null
      )
      calls[crd.metadata.name] = call
      try {
        log.info("Watching for changes to {} (spec: {}) since {}", crd.metadata.name, type.simpleName, resourceVersionTracker.get())
        call
          .createResourceWatch(type)
          .use { watch ->
            watch.forEach {
              onResourceEvent(it.eventType, it.`object`)
            }
          }
      } catch (e: Exception) {
        when {
          e.cause is SocketTimeoutException -> log.debug("Socket timed out.")
          e.cause is SocketException -> log.debug("Call was cancelled?")
          else -> throw e
        }
      }
      yield()
    }
  }

  internal fun <T : Any> onResourceEvent(type: WatchEventType, asset: Asset<T>) {
    log.info("Event {} on {}", type, asset)
    val result = when (type) {
      ADDED -> {
        assetRepository.store(asset)
        plugin.create(asset)
      }
      MODIFIED -> {
        assetRepository.store(asset)
        plugin.update(asset)
      }
      DELETED -> {
        plugin.delete(asset).also {
          if (it is ConvergeAccepted) {
            assetRepository.delete(asset.metadata.name)
          }
        }
      }
      else -> throw IllegalStateException("Unhandled event type: $type")
    }
    if (result is ConvergeAccepted) {
      asset.metadata.resourceVersion!!.let(resourceVersionTracker::set)
    }
  }

  /**
   * Gets a reified type token for the watch response. This looks complex but
   * `T` is a runtime type and gets erased before the JSON parser can reference
   * it otherwise.
   */
  private fun <T : Any> Call.createResourceWatch(type: Class<T>): Watch<Asset<T>> =
    TypeToken.getParameterized(Asset::class.java, type).type
      .let { TypeToken.getParameterized(Watch.Response::class.java, it).type }
      .let { createWatch<Asset<T>>(Config.defaultClient(), this, it) }

  private val log by lazy { LoggerFactory.getLogger(javaClass) }
}
