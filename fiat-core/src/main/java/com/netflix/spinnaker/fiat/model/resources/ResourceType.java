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

package com.netflix.spinnaker.fiat.model.resources;

import java.util.Objects;

public class ResourceType {

  private final String name;
  private final int hashCode;

  public static ResourceType ACCOUNT = new ResourceType("account");
  public static ResourceType APPLICATION = new ResourceType("application");
  public static ResourceType SERVICE_ACCOUNT =
      new ResourceType("service_account"); // Fiat service account.
  public static ResourceType ROLE = new ResourceType("role");
  public static ResourceType BUILD_SERVICE = new ResourceType("build_service");

  public ResourceType(String name) {
    this.name = name.toLowerCase();
    this.hashCode = Objects.hash(name);
  }

  // TODO(ttomsu): This is Redis-specific, so it probably shouldn't go here.
  public static ResourceType parse(String pluralOrKey) {
    if (pluralOrKey == null) {
      throw new NullPointerException("Resource type must not be null");
    }
    pluralOrKey = pluralOrKey.substring(pluralOrKey.lastIndexOf(':') + 1);
    String singular =
        pluralOrKey.endsWith("s")
            ? pluralOrKey.substring(0, pluralOrKey.length() - 1)
            : pluralOrKey;

    if ("".equals(singular)) {
      throw new IllegalArgumentException(
          String.format("Malformed resource key \"%s\"", pluralOrKey));
    }
    return new ResourceType(singular);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    ResourceType that = (ResourceType) o;
    return Objects.equals(this.name, that.name);
  }

  @Override
  public int hashCode() {
    return hashCode;
  }

  @Override
  public String toString() {
    return name;
  }

  public String keySuffix() {
    return name + "s";
  }
}
