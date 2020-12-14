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

package com.netflix.spinnaker.kork.plugins.api.internal;

import org.pf4j.ExtensionPoint;

/** Designates a Spinnaker interface or abstract class as a PF4J {@link ExtensionPoint}. */
public interface SpinnakerExtensionPoint extends ExtensionPoint {

  /**
   * Return the plugin ID this extension point implementation is associated with. Returns "default"
   * if extension point is not associated with a plugin.
   */
  default String getPluginId() {
    return ExtensionPointMetadataProvider.getPluginId(this);
  }

  /**
   * Spinnaker extension points are typically proxied to provide some extension invocation
   * instrumentation (logging, metrics, etc). To get the extension class type, use this method
   * instead of {@link #getClass()}.
   */
  default Class<? extends SpinnakerExtensionPoint> getExtensionClass() {
    return ExtensionPointMetadataProvider.getExtensionClass(this);
  }
}
