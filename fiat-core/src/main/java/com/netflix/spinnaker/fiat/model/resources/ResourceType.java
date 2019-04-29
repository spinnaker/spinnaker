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

import javax.annotation.Nonnull;

public enum ResourceType {
  ACCOUNT(Account.class),
  APPLICATION(Application.class),
  SERVICE_ACCOUNT(ServiceAccount.class), // Fiat service account.
  ROLE(Role.class),
  BUILD_SERVICE(BuildService.class);

  public Class<? extends Resource> modelClass;

  ResourceType(Class<? extends Resource> modelClass) {
    this.modelClass = modelClass;
  }

  // TODO(ttomsu): This is Redis-specific, so it probably shouldn't go here.
  public static ResourceType parse(@Nonnull String pluralOrKey) {
    pluralOrKey = pluralOrKey.substring(pluralOrKey.lastIndexOf(':') + 1);
    String singular =
        pluralOrKey.endsWith("s")
            ? pluralOrKey.substring(0, pluralOrKey.length() - 1)
            : pluralOrKey;
    return ResourceType.valueOf(singular.toUpperCase());
  }

  public String keySuffix() {
    return this.toString().toLowerCase() + "s";
  }
}
