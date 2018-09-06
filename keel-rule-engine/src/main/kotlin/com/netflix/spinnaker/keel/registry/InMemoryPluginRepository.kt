package com.netflix.spinnaker.keel.registry

import com.netflix.spinnaker.keel.api.TypeMetadata
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.stereotype.Component

@Component
@ConditionalOnMissingBean(PluginRepository::class)
class InMemoryPluginRepository : PluginRepository {

  private val assetPlugins: MutableMap<TypeMetadata, PluginAddress> = mutableMapOf()
  private val vetoPlugins: MutableSet<PluginAddress> = mutableSetOf()

  override fun vetoPlugins(): Iterable<PluginAddress> = vetoPlugins

  override fun addVetoPlugin(address: PluginAddress) {
    vetoPlugins.add(address)
  }

  override fun assetPluginFor(type: TypeMetadata): PluginAddress? =
    assetPlugins[type]

  override fun addAssetPluginFor(type: TypeMetadata, address: PluginAddress) {
    assetPlugins[type] = address
  }
}
