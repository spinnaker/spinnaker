/*
 * Copyright 2020 Netflix, Inc.
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
 *
 */

package com.netflix.spinnaker.kork.plugins.update.release

import com.netflix.spinnaker.kork.api.plugins.remote.RemoteExtensionConfig
import com.netflix.spinnaker.kork.plugins.update.internal.SpinnakerPluginInfo
import java.time.Instant
import java.util.Date

val plugin1 = SpinnakerPluginInfo().apply {
  id = "com.netflix.plugin1"
  name = "plugin1"
  description = "A test plugin"
  provider = "netflix"
  releases = listOf(
    SpinnakerPluginInfo.SpinnakerPluginRelease(false).apply {
      requires = "orca>=1.0.0"
      version = "2.0.0"
      date = Date.from(Instant.now())
      url = "front50.com/plugin.zip"
    },
    SpinnakerPluginInfo.SpinnakerPluginRelease(true).apply {
      requires = "orca>=1.0.0"
      version = "3.0.0"
      date = Date.from(Instant.now())
      url = "front50.com/plugin.zip"
    }
  )
}

val plugin2 = SpinnakerPluginInfo().apply {
  id = "com.netflix.plugin2"
  name = "plugin2"
  description = "A test plugin"
  provider = "netflix"
  releases = listOf(
    SpinnakerPluginInfo.SpinnakerPluginRelease(false).apply {
      requires = "orca>=2.0.0"
      version = "3.0.0"
      date = Date.from(Instant.now())
      url = "front50.com/plugin.zip"
    },
    SpinnakerPluginInfo.SpinnakerPluginRelease(false).apply {
      requires = "orca>=1.0.0"
      version = "4.0.0"
      date = Date.from(Instant.now())
      url = "front50.com/plugin.zip"
    },
    SpinnakerPluginInfo.SpinnakerPluginRelease(true).apply {
      requires = "orca>=1.0.0"
      version = "5.0.0"
      date = Date.from(Instant.now())
      url = "front50.com/plugin.zip"
    }
  )
}

val plugin3 = SpinnakerPluginInfo().apply {
  id = "com.netflix.plugin3"
  name = "plugin3"
  description = "A test plugin"
  provider = "netflix"
  releases = listOf(
    SpinnakerPluginInfo.SpinnakerPluginRelease(false).apply {
      requires = "orca>=2.0.0"
      version = "7.0.0"
      date = Date.from(Instant.now())
      url = "front50.com/plugin.zip"
    }
  )
}

val pluginWithRemoteExtension = SpinnakerPluginInfo().apply {
  id = "com.netflix.plugin.remote"
  name = "remote"
  description = "A test plugin"
  provider = "netflix"
  releases = listOf(
    SpinnakerPluginInfo.SpinnakerPluginRelease(
      true,
      mutableListOf(
        RemoteExtensionConfig(
          "type",
          "netflix.remote.extension",
          RemoteExtensionConfig.RemoteExtensionTransportConfig(
            RemoteExtensionConfig.RemoteExtensionTransportConfig.Http(
              "https://example.com",
              mutableMapOf()
            )
          ),
          mutableMapOf()
        )
      )
    ).apply {
      requires = "orca>=2.0.0"
      version = "7.0.0"
      date = Date.from(Instant.now())
      url = null
    }
  )
}

val pluginNoReleases = SpinnakerPluginInfo().apply {
  id = "com.netflix.no.releases"
  name = "plugin2"
  description = "A test plugin"
  provider = "netflix"
  releases = emptyList()
}
