/*
 * Copyright 2017 Google, Inc.
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

import com.netflix.spinnaker.fiat.model.Authorization;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Slf4j
abstract class BaseAccessControlled<R extends BaseAccessControlled>
    implements Resource.AccessControlled {

  abstract R setPermissions(Permissions p);

  /**
   * Legacy holdover where setting `requiredGroupMembership` implied both read and write
   * permissions.
   */
  @SuppressWarnings("unchecked")
  public <T extends BaseAccessControlled> T setRequiredGroupMembership(List<String> membership) {
    if (membership == null || membership.isEmpty()) {
      return (T) this;
    }

    if (getPermissions() != null && getPermissions().isRestricted()) {
      String msg =
          String.join(
              " ",
              "`requiredGroupMembership` found on",
              getResourceType().toString(),
              getName(),
              "and ignored because `permissions` are present");
      log.warn(msg);
      return (T) this;
    }

    String msg =
        String.join(
            " ",
            "Deprecated `requiredGroupMembership` found on",
            getResourceType().toString(),
            getName(),
            ". Please update to `permissions`.");
    log.warn(msg);
    this.setPermissions(
        new Permissions.Builder()
            .add(Authorization.READ, membership)
            .add(Authorization.WRITE, membership)
            .build());
    return (T) this;
  }
}
