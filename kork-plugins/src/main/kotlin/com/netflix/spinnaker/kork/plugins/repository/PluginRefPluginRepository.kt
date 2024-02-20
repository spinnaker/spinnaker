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

package com.netflix.spinnaker.kork.plugins.repository

import com.netflix.spinnaker.kork.plugins.pluginref.PluginRef
import java.nio.file.Path
import org.pf4j.BasePluginRepository
import org.pf4j.Plugin
import org.pf4j.PluginRepository
import org.pf4j.util.ExtensionFileFilter

/**
 * A [PluginRepository] supporting [PluginRef] type [Plugin]s by matching files with the extension [PluginRef.EXTENSION].
 */
class PluginRefPluginRepository(pluginPath: Path) : BasePluginRepository(listOf(pluginPath), ExtensionFileFilter(PluginRef.EXTENSION)) {
  override fun deletePluginPath(pluginPath: Path?): Boolean = false
}
