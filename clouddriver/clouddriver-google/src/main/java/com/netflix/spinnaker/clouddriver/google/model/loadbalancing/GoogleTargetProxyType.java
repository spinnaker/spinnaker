/*
 * Copyright 2016 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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
package com.netflix.spinnaker.clouddriver.google.model.loadbalancing;

import com.netflix.spinnaker.kork.annotations.NonnullByDefault;

@NonnullByDefault
public enum GoogleTargetProxyType {
  HTTP,
  HTTPS,
  SSL,
  TCP,
  UNKNOWN;

  /**
   * Given a string representing a resource type (as found in the URI for a target proxy), returns
   * the corresponding {@link GoogleTargetProxyType}, or {@link GoogleTargetProxyType#UNKNOWN} if no
   * {@link GoogleTargetProxyType} matches the resource type.
   *
   * @param identifier the identifier
   * @return the corresponding {@link GoogleTargetProxyType}
   */
  public static GoogleTargetProxyType fromResourceType(String identifier) {
    switch (identifier) {
      case "targetHttpProxies":
        return GoogleTargetProxyType.HTTP;
      case "targetHttpsProxies":
        return GoogleTargetProxyType.HTTPS;
      case "targetSslProxies":
        return GoogleTargetProxyType.SSL;
      case "targetTcpProxies":
        return GoogleTargetProxyType.TCP;
      default:
        return UNKNOWN;
    }
  }
}
