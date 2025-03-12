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
 */
package com.netflix.spinnaker.kork.plugins.sdk

import org.pf4j.PluginWrapper

/**
 * Resolves an "ID" for an extension, preferring the plugin ID, if it belongs to one.
 */
internal object IdResolver {

  fun pluginOrExtensionId(extensionClass: Class<*>, pluginWrapper: PluginWrapper?): String {
    if (pluginWrapper != null) {
      return pluginWrapper.pluginId
    }

    // TODO(rz): Extensions need an ID that isn't coupled to the class name. Ideally we'd move back to using
    //  a SpinnakerExtension annotation or upstream an ID property on PF4J's Extension annotation.
    return extensionClass.simpleName
  }
}
