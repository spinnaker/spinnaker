/*
 * Copyright 2026 Spinnaker.io, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.netflix.spinnaker.clouddriver.artifacts.config;

import com.netflix.spinnaker.fiat.model.resources.Permissions;
import com.netflix.spinnaker.kork.annotations.NonnullByDefault;

/**
 * Base class for {@link ArtifactCredentials} implementations that carries the {@link Permissions}
 * declared on the backing {@link ArtifactAccount} definition, so every artifact account type
 * exposes them the same way without repeating the copy in each implementation's constructor.
 */
@NonnullByDefault
public abstract class AbstractArtifactCredentials implements ArtifactCredentials {
  private final Permissions permissions;

  protected AbstractArtifactCredentials(ArtifactAccount account) {
    this.permissions = new Permissions.Builder().set(account.getPermissions()).build();
  }

  @Override
  public Permissions getPermissions() {
    return permissions;
  }
}
