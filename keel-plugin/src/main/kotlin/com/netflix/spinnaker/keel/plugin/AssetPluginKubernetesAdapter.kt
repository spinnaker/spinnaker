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

internal class AssetPluginKubernetesAdapter<T : Any>(
  private val customObjectsApi: CustomObjectsApi,
  private val crd: V1beta1CustomResourceDefinition,
  private val plugin: AssetPlugin,
  private val watchType: Type
) {
  private var job: Job? = null
  private var watch: Watch<Asset<T>>? = null
  private var call: Call? = null

  fun start() {
    if (job != null) throw IllegalStateException("already running")
    job = GlobalScope.launch {
      watchForResourceChanges()
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
      watch = createResourceWatch()
      try {
        watch?.use { watch ->
          watch.forEach {
            log.info("Event {} on {}", it.type, it.`object`)
            log.info("Event {} on {} v{}, last seen {}", it.type, it.`object`.metadata.name, it.`object`.metadata.resourceVersion, seen)
            val version = it.`object`.metadata.resourceVersion ?: 0L
            if (version > seen) {
              when (it.type) {
                "ADDED" -> {
                  seen = version
                  plugin.create(it.`object`)
                }
                "MODIFIED" -> {
                  seen = version
                  plugin.update(it.`object`)
                }
              }
            }
          }
        }
      } catch (e: Exception) {
        log.warn("handling exception from watch: ${e.message}", e)
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
