/*
 * Copyright 2019 Netflix, Inc.
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
package com.netflix.spinnaker.kork.plugins

import com.netflix.spinnaker.kork.annotations.Beta
import com.netflix.spinnaker.kork.plugins.bundle.PluginBundleExtractor
import com.netflix.spinnaker.kork.plugins.config.ConfigFactory
import com.netflix.spinnaker.kork.plugins.finders.SpinnakerPluginDescriptorFinder
import com.netflix.spinnaker.kork.plugins.loaders.PluginRefPluginLoader
import com.netflix.spinnaker.kork.plugins.loaders.SpinnakerDefaultPluginLoader
import com.netflix.spinnaker.kork.plugins.loaders.SpinnakerDevelopmentPluginLoader
import com.netflix.spinnaker.kork.plugins.loaders.SpinnakerJarPluginLoader
import com.netflix.spinnaker.kork.plugins.repository.PluginRefPluginRepository
import com.netflix.spinnaker.kork.plugins.sdk.SdkFactory
import com.netflix.spinnaker.kork.version.ServiceVersion
import java.nio.file.Files
import java.nio.file.Path
import org.pf4j.CompoundPluginLoader
import org.pf4j.CompoundPluginRepository
import org.pf4j.DefaultPluginManager
import org.pf4j.ExtensionFactory
import org.pf4j.PluginDescriptorFinder
import org.pf4j.PluginFactory
import org.pf4j.PluginLoader
import org.pf4j.PluginRepository
import org.pf4j.PluginStatusProvider
import org.pf4j.PluginWrapper
import org.pf4j.VersionManager
import org.slf4j.LoggerFactory

/**
 * The primary entry-point to the plugins system from a provider-side (services, libs, CLIs, and so-on).
 *
 * @param spinnakerVersionManager The [VersionManager] configured for Spinnaker.
 * @param statusProvider A Spring Environment-aware plugin status provider.
 * @param pluginsRoot The root path to search for in-process plugin artifacts.
 */
@Beta
@Suppress("LongParameterList")
open class SpinnakerPluginManager(
  private val serviceVersion: ServiceVersion,
  val spinnakerVersionManager: VersionManager,
  val statusProvider: PluginStatusProvider,
  configFactory: ConfigFactory,
  sdkFactories: List<SdkFactory>,
  private val serviceName: String,
  pluginsRoot: Path,
  private val pluginBundleExtractor: PluginBundleExtractor,
  private val spinnakerPluginFactory: PluginFactory
) : DefaultPluginManager(pluginsRoot) {

  private val log by lazy { LoggerFactory.getLogger(javaClass) }

  private val springExtensionFactory: ExtensionFactory = SpinnakerExtensionFactory(
    this,
    configFactory,
    sdkFactories
  )

  private inner class ExtensionFactoryDelegate : ExtensionFactory {
    override fun <T : Any?> create(extensionClass: Class<T>?): T = springExtensionFactory.create(extensionClass)
  }

  private inner class PluginStatusProviderDelegate : PluginStatusProvider {
    override fun disablePlugin(pluginId: String?) = statusProvider.disablePlugin(pluginId)

    override fun isPluginDisabled(pluginId: String?): Boolean = statusProvider.isPluginDisabled(pluginId)

    override fun enablePlugin(pluginId: String?) = statusProvider.enablePlugin(pluginId)
  }

  private inner class VersionManagerDelegate : VersionManager {
    override fun checkVersionConstraint(version: String, constraint: String): Boolean = spinnakerVersionManager.checkVersionConstraint(version, constraint)

    override fun compareVersions(v1: String, v2: String): Int = spinnakerVersionManager.compareVersions(v1, v2)
  }

  override fun getSystemVersion(): String {
    // TODO(jonsie): For now this is ok, but eventually we will want to throw an exception if the
    // system version is null.
    return serviceVersion.resolve().let {
      if (it == ServiceVersion.UNKNOWN_VERSION || it.isEmpty()) {
        ServiceVersion.DEFAULT_VERSION
      } else {
        it
      }
    }
  }

  override fun createExtensionFactory(): ExtensionFactory = ExtensionFactoryDelegate()

  override fun createPluginStatusProvider(): PluginStatusProvider = PluginStatusProviderDelegate()

  override fun createVersionManager(): VersionManager = VersionManagerDelegate()

  override fun createPluginLoader(): PluginLoader =
    CompoundPluginLoader()
      .add(PluginRefPluginLoader(this), this::isDevelopment)
      .add(SpinnakerDevelopmentPluginLoader(this), this::isDevelopment)
      .add(SpinnakerDefaultPluginLoader(this))
      .add(SpinnakerJarPluginLoader(this))

  override fun createPluginDescriptorFinder(): PluginDescriptorFinder =
    SpinnakerPluginDescriptorFinder(this.getRuntimeMode())

  override fun loadPluginFromPath(pluginPath: Path): PluginWrapper? {
    val extractedPath = pluginBundleExtractor.extractService(pluginPath, serviceName) ?: return null
    return super.loadPluginFromPath(extractedPath)
  }

  internal fun setPlugins(specifiedPlugins: Collection<PluginWrapper>) {
    this.plugins = specifiedPlugins.associateBy { it.pluginId }
  }

  override fun createPluginRepository(): PluginRepository = CompoundPluginRepository()
    .add(PluginRefPluginRepository(getPluginsRoot()), this::isDevelopment)
    .add(super.createPluginRepository())

  override fun getPluginFactory(): PluginFactory = spinnakerPluginFactory

  // TODO (link108): remove this override, once plugin deployments via halyard are fixed
  override fun loadPlugin(pluginPath: Path?): String? {
    require(!(pluginPath == null || Files.notExists(pluginPath))) { "Specified plugin '$pluginPath' does not exist!" }
    log.debug("Loading plugin from '{}'", pluginPath)
    return loadPluginFromPath(pluginPath)
      ?.let {
        // try to resolve  the loaded plugin together with other possible plugins that depend on this plugin
        resolvePlugins()
        it.descriptor.pluginId
      }
  }

  init {
    systemVersion = getSystemVersion()
  }
}
