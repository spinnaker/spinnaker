/*
 * Copyright 2019 Netflix, Inc.
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
package com.netflix.spinnaker.front50.events;

import com.netflix.spinnaker.front50.model.application.Application;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public interface ApplicationPermissionEventListener {

  boolean supports(Type type);

  @Nullable
  Application.Permission call(
      @Nullable Application.Permission originalPermission,
      @Nullable Application.Permission updatedPermission);

  void rollback(@Nonnull Application.Permission originalPermission);

  enum Type {
    PRE_UPDATE,
    POST_UPDATE,
    PRE_CREATE,
    POST_CREATE,
    PRE_DELETE,
    POST_DELETE
  }
}
