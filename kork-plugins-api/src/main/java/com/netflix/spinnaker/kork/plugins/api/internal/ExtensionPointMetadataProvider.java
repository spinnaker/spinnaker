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

import java.lang.reflect.Proxy;

public class ExtensionPointMetadataProvider {

  public static Class<? extends SpinnakerExtensionPoint> getExtensionClass(
      SpinnakerExtensionPoint extensionPoint) {
    if (Proxy.isProxyClass(extensionPoint.getClass())) {
      ExtensionInvocationHandler extensionInvocationHandler =
          (ExtensionInvocationHandler) Proxy.getInvocationHandler(extensionPoint);
      return extensionInvocationHandler.getTargetClass();
    }
    return extensionPoint.getClass();
  }

  public static String getPluginId(SpinnakerExtensionPoint extensionPoint) {
    if (Proxy.isProxyClass(extensionPoint.getClass())) {
      ExtensionInvocationHandler extensionInvocationHandler =
          (ExtensionInvocationHandler) Proxy.getInvocationHandler(extensionPoint);
      return extensionInvocationHandler.getPluginId();
    }
    return "default";
  }
}
