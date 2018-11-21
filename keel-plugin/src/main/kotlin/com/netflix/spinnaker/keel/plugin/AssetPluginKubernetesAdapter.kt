package com.netflix.spinnaker.keel.plugin

import com.netflix.spinnaker.keel.api.Asset
import com.squareup.okhttp.Call
import io.kubernetes.client.apis.CustomObjectsApi
import io.kubernetes.client.models.V1beta1CustomResourceDefinition
import io.kubernetes.client.util.Config
import io.kubernetes.client.util.Watch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.slf4j.LoggerFactory
import java.lang.reflect.Type
import java.net.SocketException

internal class AssetPluginKubernetesAdapter<T : Any>(
  private val customObjectsApi: CustomObjectsApi,
  private val plugin: AssetPlugin,
  private val crd: V1beta1CustomResourceDefinition,
  private val watchType: Type
) {
  private var job: Job? = null
  private var watch: Watch<Asset<T>>? = null
  private var call: Call? = null

  fun start() {
    runBlocking {
      launch {
        if (job != null) throw IllegalStateException("Watcher for ${crd.metadata.name} already running")
        job = GlobalScope.launch {
          watchForResourceChanges()
        }
      }
        .join()
      log.debug("All CRDs are registered")
    }
  }

  fun stop() {
    runBlocking {
      job?.cancel()
      call?.cancel()
      job?.join()
    }
  }

  private suspend fun CoroutineScope.watchForResourceChanges() {
    var seen = 0L
    while (isActive) {
      call = customObjectsApi.listClusterCustomObjectCall(
        crd.spec.group,
        crd.spec.version,
        crd.spec.names.plural,
        "true",
        null,
        "0", // TODO: this should update based on `seen`
        true,
        null,
        null
      )
      try {
        watch = createResourceWatch()
        watch?.use { watch ->
          watch.forEach {
            log.info("Event {} on {}", it.type, it.`object`)
            log.info("Event {} on {} v{}, last seen {}", it.type, it.`object`.metadata.name, it.`object`.metadata.resourceVersion, seen)
            val version = it.`object`.metadata.resourceVersion ?: 0L
            if (version > seen) {
              seen = version
              when (it.type) {
                "ADDED" -> plugin.create(it.`object`)
                "MODIFIED" -> plugin.update(it.`object`)
                "DELETED" -> plugin.delete(it.`object`)
              }
            }
          }
        }
      } catch (e: Exception) {
        if (e.cause is SocketException) {
          log.debug("Socket timed out or call was cancelled.")
        } else {
          throw e
        }
      }
      yield()
    }
  }

  private fun <T : Any> createResourceWatch(): Watch<Asset<T>> =
    watchType
      .also {
        log.info(it.toString())
      }
      .let {
        Watch.createWatch<Asset<T>>(
          Config.defaultClient(),
          call,
          it
        )
      }

  private val log by lazy { LoggerFactory.getLogger(javaClass) }
}
