package com.netflix.spinnaker.keel.plugin.monitoring

import com.fasterxml.jackson.dataformat.yaml.YAMLMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.google.common.hash.Hashing
import com.netflix.spinnaker.keel.api.ApiVersion
import com.netflix.spinnaker.keel.api.Asset
import com.netflix.spinnaker.keel.api.AssetKind
import com.netflix.spinnaker.keel.persistence.AssetRepository
import com.netflix.spinnaker.keel.plugin.AssetPlugin
import com.netflix.spinnaker.keel.plugin.ResourceError
import com.netflix.spinnaker.keel.plugin.ResourceMissing
import com.netflix.spinnaker.keel.plugin.ResourceState
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.nio.charset.Charset

@Component
class AssetStateMonitor(
  private val assetRepository: AssetRepository,
  private val plugins: List<AssetPlugin>
) {

  @Scheduled(fixedDelayString = "\${keel.asset.monitoring.frequency.ms:60000}")
  fun validateManagedAssets() {
    assetRepository.allAssets { (uid, apiVersion, singular) ->
      val plugin = pluginFor(apiVersion, singular) ?: throw UnsupportedKind(apiVersion, singular)
      val type = plugin.typeFor(singular)
      val asset = assetRepository.get(uid, type)
      when (val response = plugin.current(asset)) {
        is ResourceMissing -> {
          log.warn("Asset {} {} is missing", asset.kind)
          plugin.create(asset)
        }
        is ResourceState<*> -> if (asset.matches(response.spec)) {
          log.info("Asset {} {} is valid", asset.kind, asset.metadata.name)
        } else {
          log.warn("Asset {} {} is invalid", asset.kind, asset.metadata.name)
          plugin.update(asset)
        }
        is ResourceError ->
          log.error(
            "Asset {} {} current state could not be determined due to \"{}\"",
            asset.kind,
            asset.metadata.name,
            response.reason
          )
      }
    }
  }

  private val hashFunction = Hashing
    .murmur3_128()

  private fun AssetPlugin.typeFor(singular: String): Class<out Any> =
    supportedKinds.entries.find { it.key.singular == singular }?.value
      ?: throw IllegalArgumentException("Plugin $name does not support $singular")

  private fun Asset<*>.matches(current: Any): Boolean =
    hashFunction.hashString(mapper.writeValueAsString(spec), UTF_8) == hashFunction.hashString(mapper.writeValueAsString(current), UTF_8)

  private fun pluginFor(apiVersion: ApiVersion, singular: String): AssetPlugin? =
    plugins.find { it.apiVersion == apiVersion && it.supportedKinds.keys.map(AssetKind::singular).contains(singular) }

  private val log by lazy { LoggerFactory.getLogger(javaClass) }
  private val mapper by lazy { YAMLMapper().registerKotlinModule() }

  companion object {
    private val UTF_8 = Charset.forName("UTF-8")
  }
}

class UnsupportedKind(apiVersion: ApiVersion, kind: String) : IllegalStateException("No plugin supporting \"$kind\" in \"$apiVersion\" is available")
