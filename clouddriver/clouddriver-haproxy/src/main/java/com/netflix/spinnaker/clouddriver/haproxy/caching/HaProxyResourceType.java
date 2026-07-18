/*
 * Copyright 2026 McIntosh.farm
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
package com.netflix.spinnaker.clouddriver.haproxy.caching;

/** Enumerates all HAProxy resource types that can be cached. */
public enum HaProxyResourceType {
  APPLICATION("application"),
  CLUSTER("cluster"),
  FRONTEND("frontend"),
  BACKEND("backend");

  private final String name;

  HaProxyResourceType(String name) {
    this.name = name;
  }

  public String getName() {
    return name;
  }

  public static HaProxyResourceType fromName(String name) {
    for (HaProxyResourceType type : values()) {
      if (type.name.equalsIgnoreCase(name)) {
        return type;
      }
    }
    throw new IllegalArgumentException("Unknown HAProxy resource type: " + name);
  }
}
