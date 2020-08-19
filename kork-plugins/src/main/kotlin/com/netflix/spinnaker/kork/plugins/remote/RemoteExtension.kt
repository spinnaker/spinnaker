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

package com.netflix.spinnaker.kork.plugins.remote

import com.netflix.spinnaker.kork.annotations.Beta
import com.netflix.spinnaker.kork.common.Header
import com.netflix.spinnaker.kork.plugins.remote.transport.RemoteExtensionPayload
import com.netflix.spinnaker.kork.plugins.remote.transport.RemoteExtensionTransport
import org.slf4j.MDC

/**
 * The remote extension with the implemented [RemoteExtensionTransport] which is based on the
 * remote extension configuration.
 */
@Beta
class RemoteExtension(

  /** Identifier of the remote extension.  Used for tracing. */
  val id: String,

  /** Identifier of the plugin.  Used for tracing. */
  val pluginId: String,

  /**
   * The remote extension type. Services will have a corresponding remote extension point
   * implementation depending on the type.
   */
  val type: String,

  /**
   * Configuration necessary for the extension point - typically specifying something to configure
   * prior to the remote extension invocation.
   */
  val config: Map<String, Any>,

  private val transport: RemoteExtensionTransport
) {

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
