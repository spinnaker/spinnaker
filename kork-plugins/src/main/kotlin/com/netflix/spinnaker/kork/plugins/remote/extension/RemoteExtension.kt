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

package com.netflix.spinnaker.kork.plugins.remote.extension

import com.netflix.spinnaker.kork.annotations.Beta
import com.netflix.spinnaker.kork.common.Header
import com.netflix.spinnaker.kork.plugins.remote.extension.transport.RemoteExtensionTransport
import org.slf4j.MDC

/**
 * The remote extension with the implemented [RemoteExtensionTransport] which is based on the
 * remote extension configuration.
 *
 * @param id Identifier of the remote extension.  Used for tracing.
 * @param pluginId Identifier of the plugin.  Used for tracing.
 * @param type The remote extension type. Services will have a corresponding remote extension point implementation depending on the type.
 * @param config Configuration necessary for the extension point - typically specifying something to configure prior to the remote extension invocation.
 */
@Beta
class RemoteExtension(
  val id: String,
  val pluginId: String,
  val type: String,
  val config: RemoteExtensionPointConfig,
  private val transport: RemoteExtensionTransport
) {

  /**
   * Return the configuration as the requested type.
   */
  @Suppress("UNCHECKED_CAST")
  fun <T: RemoteExtensionPointConfig> getTypedConfig(): T {
    return config as T
  }

  /** Invoke the remote extension via the [RemoteExtensionTransport] implementation. */
  fun invoke(payload: RemoteExtensionPayload) {
    MDC.put(Header.PLUGIN_ID.header, pluginId)
    MDC.put(Header.PLUGIN_EXTENSION.header, id)

    try {
      transport.invoke(payload)
    } finally {
      MDC.remove(Header.PLUGIN_ID.header)
      MDC.remove(Header.PLUGIN_EXTENSION.header)
    }
  }
}
