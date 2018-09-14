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
package com.netflix.spinnaker.keel.registry

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.stereotype.Component

@Component
@ConditionalOnMissingBean(PluginRepository::class)
class InMemoryPluginRepository : PluginRepository {

  private val assetPlugins: MutableMap<AssetType, PluginAddress> = mutableMapOf()
  private val vetoPlugins: MutableSet<PluginAddress> = mutableSetOf()

  override fun allPlugins(): Iterable<PluginAddress> = assetPlugins.values.distinct() + vetoPlugins

  override fun assetPlugins(): Iterable<PluginAddress> = assetPlugins.values.distinct()

  override fun vetoPlugins(): Iterable<PluginAddress> = vetoPlugins

  override fun addVetoPlugin(address: PluginAddress) {
    vetoPlugins.add(address)
  }

  override fun assetPluginFor(type: AssetType): PluginAddress? =
    assetPlugins[type]

  override fun addAssetPluginFor(type: AssetType, address: PluginAddress) {
    assetPlugins[type] = address
  }

  internal fun clear() {
    assetPlugins.clear()
    vetoPlugins.clear()
  }
}
